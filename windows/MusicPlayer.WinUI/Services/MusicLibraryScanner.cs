using MusicPlayer.WinUI.Models;
using Windows.Storage;
using Windows.Storage.FileProperties;

namespace MusicPlayer.WinUI.Services;

public sealed class MusicLibraryScanner
{
    private static readonly HashSet<string> SupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".m4a", ".flac", ".wav", ".aac", ".ogg"
    };

    public async Task<IReadOnlyList<Song>> ScanAsync(
        IEnumerable<string> folders,
        IProgress<(int current, int total)>? progress,
        CancellationToken cancellationToken)
    {
        var filePaths = folders
            .Where(Directory.Exists)
            .SelectMany(folder => Directory.EnumerateFiles(folder, "*.*", SearchOption.AllDirectories)
                .Where(path => SupportedExtensions.Contains(Path.GetExtension(path))))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

        var result = new List<Song>(filePaths.Count);
        var total = filePaths.Count;

        for (var index = 0; index < total; index++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var filePath = filePaths[index];
            var song = await BuildSongAsync(filePath, folders, cancellationToken);
            result.Add(song);
            progress?.Report((index + 1, total));
        }

        return result
            .OrderBy(song => song.Title, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
    }

    private static async Task<Song> BuildSongAsync(string filePath, IEnumerable<string> sourceFolders, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var titleFallback = Path.GetFileNameWithoutExtension(filePath);
        string title = titleFallback;
        var artist = "Unknown Artist";
        var album = "Unknown Album";
        var trackNumber = 0;

        try
        {
            var storageFile = await StorageFile.GetFileFromPathAsync(filePath);
            MusicProperties props = await storageFile.Properties.GetMusicPropertiesAsync();

            var fixedTitle = EncodingFixer.FixLatin1ToShiftJis(props.Title);
            var fixedArtist = EncodingFixer.FixLatin1ToShiftJis(props.Artist);
            var fixedAlbum = EncodingFixer.FixLatin1ToShiftJis(props.Album);

            if (!string.IsNullOrWhiteSpace(fixedTitle) && !EncodingFixer.IsGarbled(fixedTitle))
            {
                title = fixedTitle;
            }

            if (!string.IsNullOrWhiteSpace(fixedArtist) && !EncodingFixer.IsGarbled(fixedArtist))
            {
                artist = fixedArtist;
            }

            if (!string.IsNullOrWhiteSpace(fixedAlbum) && !EncodingFixer.IsGarbled(fixedAlbum))
            {
                album = fixedAlbum;
            }

            trackNumber = (int)props.TrackNumber;
        }
        catch
        {
            // メタデータ失敗時はfallback
        }

        var sourceFolder = sourceFolders.FirstOrDefault(folder => filePath.StartsWith(folder, StringComparison.OrdinalIgnoreCase))
            ?? Path.GetDirectoryName(filePath)
            ?? string.Empty;

        return new Song
        {
            FileUri = new Uri(filePath),
            FilePath = Path.GetFullPath(filePath),
            DisplayName = Path.GetFileName(filePath),
            Title = title,
            Artist = artist,
            Album = album,
            TrackNumber = trackNumber,
            SourceFolderPath = sourceFolder,
            PlayCount = 0,
        };
    }
}
