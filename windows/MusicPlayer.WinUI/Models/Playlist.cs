namespace MusicPlayer.WinUI.Models;

public sealed class Playlist
{
    public required string Name { get; init; }
    public required IReadOnlyList<Song> Songs { get; init; }
}
