using MusicPlayer.WinUI.Models;

namespace MusicPlayer.WinUI.Services;

public sealed class MusicLibraryService
{
    private static readonly string[] SupportedExtensions = [
        ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".wma"
    ];

    public async Task<IReadOnlyList<Song>> ScanFoldersAsync(IEnumerable<string> rootFolders, CancellationToken cancellationToken = default)
    {
        return await Task.Run(() =>
        {
            var result = new List<Song>();

            foreach (var folder in rootFolders.Where(Directory.Exists))
            {
                cancellationToken.ThrowIfCancellationRequested();
                var files = Directory.EnumerateFiles(folder, "*.*", SearchOption.AllDirectories)
                    .Where(path => SupportedExtensions.Contains(System.IO.Path.GetExtension(path), StringComparer.OrdinalIgnoreCase));

                foreach (var path in files)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    var fileName = System.IO.Path.GetFileNameWithoutExtension(path);
                    result.Add(new Song(path, fileName, fileName, "Unknown Artist", "Unknown Album"));
                }
            }

            return (IReadOnlyList<Song>)result
                .OrderBy(s => s.DisplayName, StringComparer.CurrentCultureIgnoreCase)
                .ToList();
        }, cancellationToken);
    }
}
