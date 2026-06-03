using MusicPlayer.WinUI.Models;
using System.Text;

namespace MusicPlayer.WinUI.Services;

public sealed class PlaylistService
{
    public async Task<Playlist?> ParseM3uAsync(string playlistPath, IReadOnlyList<Song> allSongs, string basePath = "")
    {
        if (!File.Exists(playlistPath))
        {
            return null;
        }

        var entries = await File.ReadAllLinesAsync(playlistPath, DetectEncoding(playlistPath));
        var playlistSongs = new List<Song>();

        var playlistDirectory = System.IO.Path.GetDirectoryName(playlistPath) ?? Directory.GetCurrentDirectory();

        foreach (var raw in entries)
        {
            var line = raw.Trim();
            if (string.IsNullOrWhiteSpace(line) || line.StartsWith("#", StringComparison.Ordinal))
            {
                continue;
            }

            var normalized = NormalizePlaylistPath(line, basePath, playlistDirectory);
            var matched = FindSong(normalized, allSongs);
            if (matched is not null)
            {
                playlistSongs.Add(matched);
                continue;
            }

            var missingName = System.IO.Path.GetFileNameWithoutExtension(normalized);
            playlistSongs.Add(new Song(normalized, missingName, missingName, "Unknown Artist", "Unknown Album", Exists: File.Exists(normalized)));
        }

        var playlistName = System.IO.Path.GetFileNameWithoutExtension(playlistPath);
        return new Playlist(playlistName, playlistSongs);
    }

    private static Song? FindSong(string line, IReadOnlyList<Song> songs)
    {
        var byFullPath = songs.FirstOrDefault(s => string.Equals(s.Path, line, StringComparison.OrdinalIgnoreCase));
        if (byFullPath is not null)
        {
            return byFullPath;
        }

        var fileName = System.IO.Path.GetFileName(line);
        return songs.FirstOrDefault(s => string.Equals(System.IO.Path.GetFileName(s.Path), fileName, StringComparison.OrdinalIgnoreCase));
    }

    private static string NormalizePlaylistPath(string line, string basePath, string playlistDirectory)
    {
        var normalized = line.Replace('/', Path.DirectorySeparatorChar).Replace('\\', Path.DirectorySeparatorChar);

        if (!string.IsNullOrWhiteSpace(basePath))
        {
            var normalizedBase = basePath.Replace('/', Path.DirectorySeparatorChar).Replace('\\', Path.DirectorySeparatorChar);
            if (normalized.StartsWith(normalizedBase, StringComparison.OrdinalIgnoreCase))
            {
                normalized = normalized[normalizedBase.Length..].TrimStart(Path.DirectorySeparatorChar);
            }
        }

        return Path.IsPathRooted(normalized)
            ? Path.GetFullPath(normalized)
            : Path.GetFullPath(Path.Combine(playlistDirectory, normalized));
    }

    private static Encoding DetectEncoding(string file)
    {
        var bytes = File.ReadAllBytes(file);
        if (bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF)
        {
            return Encoding.UTF8;
        }

        try
        {
            _ = Encoding.UTF8.GetString(bytes);
            return Encoding.UTF8;
        }
        catch
        {
            return Encoding.GetEncoding("shift_jis");
        }
    }
}
