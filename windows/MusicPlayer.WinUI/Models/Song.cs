namespace MusicPlayer.WinUI.Models;

public sealed record Song(
    string Path,
    string DisplayName,
    string Title,
    string Artist,
    string Album,
    int PlayCount = 0,
    bool Exists = true
);
