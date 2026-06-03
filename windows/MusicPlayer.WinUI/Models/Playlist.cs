namespace MusicPlayer.WinUI.Models;

public sealed record Playlist(string Name, IReadOnlyList<Song> Songs);
