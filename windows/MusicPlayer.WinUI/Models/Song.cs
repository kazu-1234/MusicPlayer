using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace MusicPlayer.WinUI.Models;

public sealed class Song : INotifyPropertyChanged
{
    private int _playCount;

    public required Uri FileUri { get; init; }
    public required string FilePath { get; init; }
    public required string DisplayName { get; init; }
    public required string Title { get; init; }
    public required string Artist { get; init; }
    public required string Album { get; init; }
    public required int TrackNumber { get; init; }
    public required string SourceFolderPath { get; init; }

    public int PlayCount
    {
        get => _playCount;
        set
        {
            if (_playCount == value) return;
            _playCount = value;
            OnPropertyChanged();
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
