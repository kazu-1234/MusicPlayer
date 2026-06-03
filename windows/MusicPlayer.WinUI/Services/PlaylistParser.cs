using MusicPlayer.WinUI.Models;

namespace MusicPlayer.WinUI.Services;

public static class PlaylistParser
{
    public static Playlist LoadFromM3U(string playlistPath, IReadOnlyDictionary<string, Song> songsByPath, string? basePath = null)
    {
        var name = Path.GetFileNameWithoutExtension(playlistPath);
        var songs = new List<Song>();

        foreach (var line in File.ReadLines(playlistPath))
        {
            var raw = line.Trim();
            if (string.IsNullOrEmpty(raw) || raw.StartsWith("#")) continue;

            var candidatePath = NormalizePath(raw, basePath);
            if (songsByPath.TryGetValue(candidatePath, out var song))
            {
                songs.Add(song);
            }
        }

        return new Playlist
        {
            Name = name,
            Songs = songs,
        };
    }

    private static string NormalizePath(string path, string? basePath)
    {
        if (Path.IsPathRooted(path))
        {
            return Path.GetFullPath(path);
        }

        var combined = string.IsNullOrWhiteSpace(basePath)
            ? Path.GetFullPath(path)
            : Path.GetFullPath(Path.Combine(basePath, path));

        return combined;
    }
}
