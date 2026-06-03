using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using Microsoft.UI.Xaml;
using MusicPlayer.WinUI.Models;
using MusicPlayer.WinUI.Services;
using Windows.Media.Core;
using Windows.Media.Playback;
using Windows.Storage.Pickers;

namespace MusicPlayer.WinUI.ViewModels;

public sealed class MainViewModel : INotifyPropertyChanged
{
    private readonly Window _window;
    private readonly MusicLibraryScanner _scanner = new();
    private readonly MediaPlayer _player = new();
    private readonly DispatcherTimer _positionTimer = new();
    private readonly List<Song> _allSongs = [];
    private int _currentIndex = -1;
    private CancellationTokenSource? _scanCancellation;

    public MainViewModel(Window window)
    {
        _window = window;

        _player.MediaEnded += (_, _) => OnMediaEnded();
        _positionTimer.Interval = TimeSpan.FromMilliseconds(250);
        _positionTimer.Tick += (_, _) => UpdatePosition();
        _positionTimer.Start();

        SortOptions = ["Title", "Artist", "Album", "PlayCount"];
        SelectedSort = SortOptions[0];
    }

    public ObservableCollection<string> SourceFolders { get; } = [];
    public ObservableCollection<Song> Songs { get; } = [];
    public ObservableCollection<Song> Queue { get; } = [];
    public ObservableCollection<Playlist> Playlists { get; } = [];
    public ObservableCollection<string> SortOptions { get; }

    private Song? _selectedSong;
    public Song? SelectedSong
    {
        get => _selectedSong;
        set
        {
            _selectedSong = value;
            OnPropertyChanged();
        }
    }

    private Song? _selectedQueueSong;
    public Song? SelectedQueueSong
    {
        get => _selectedQueueSong;
        set
        {
            _selectedQueueSong = value;
            OnPropertyChanged();
        }
    }

    private string _searchQuery = string.Empty;
    public string SearchQuery
    {
        get => _searchQuery;
        set
        {
            if (_searchQuery == value) return;
            _searchQuery = value;
            OnPropertyChanged();
            ApplyFilters();
        }
    }

    private string _selectedSort;
    public string SelectedSort
    {
        get => _selectedSort;
        set
        {
            if (_selectedSort == value) return;
            _selectedSort = value;
            OnPropertyChanged();
            ApplyFilters();
        }
    }

    private bool _isScanning;
    public bool IsScanning
    {
        get => _isScanning;
        private set
        {
            _isScanning = value;
            OnPropertyChanged();
        }
    }

    private double _scanProgress;
    public double ScanProgress
    {
        get => _scanProgress;
        private set
        {
            _scanProgress = value;
            OnPropertyChanged();
        }
    }

    private Song? _currentSong;
    public Song? CurrentSong
    {
        get => _currentSong;
        private set
        {
            _currentSong = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(CurrentTitle));
            OnPropertyChanged(nameof(CurrentArtist));
        }
    }

    public string CurrentTitle => CurrentSong?.Title ?? "未再生";
    public string CurrentArtist => CurrentSong?.Artist ?? "";

    private bool _isPlaying;
    public bool IsPlaying
    {
        get => _isPlaying;
        private set
        {
            _isPlaying = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(PlayPauseLabel));
        }
    }

    public string PlayPauseLabel => IsPlaying ? "Pause" : "Play";

    private RepeatMode _repeatMode = RepeatMode.Off;
    public RepeatMode RepeatMode
    {
        get => _repeatMode;
        private set
        {
            _repeatMode = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(RepeatModeLabel));
        }
    }

    public string RepeatModeLabel => RepeatMode switch
    {
        RepeatMode.Off => "Repeat: Off",
        RepeatMode.All => "Repeat: All",
        RepeatMode.One => "Repeat: One",
        _ => "Repeat: Off"
    };

    private bool _isShuffleEnabled;
    public bool IsShuffleEnabled
    {
        get => _isShuffleEnabled;
        private set
        {
            _isShuffleEnabled = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(ShuffleLabel));
        }
    }

    public string ShuffleLabel => IsShuffleEnabled ? "Shuffle: On" : "Shuffle: Off";

    private string _positionText = "00:00";
    public string PositionText
    {
        get => _positionText;
        private set
        {
            _positionText = value;
            OnPropertyChanged();
        }
    }

    private string _durationText = "00:00";
    public string DurationText
    {
        get => _durationText;
        private set
        {
            _durationText = value;
            OnPropertyChanged();
        }
    }

    private double _positionRatio;
    public double PositionRatio
    {
        get => _positionRatio;
        private set
        {
            _positionRatio = value;
            OnPropertyChanged();
        }
    }

    public async Task PickAndAddFolderAsync()
    {
        var picker = new FolderPicker();
        picker.FileTypeFilter.Add("*");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(_window));

        var folder = await picker.PickSingleFolderAsync();
        if (folder is null) return;

        var path = folder.Path;
        if (!SourceFolders.Contains(path))
        {
            SourceFolders.Add(path);
        }
    }

    public async Task ScanLibraryAsync()
    {
        if (SourceFolders.Count == 0) return;

        _scanCancellation?.Cancel();
        _scanCancellation = new CancellationTokenSource();

        IsScanning = true;
        ScanProgress = 0;

        var progress = new Progress<(int current, int total)>(p =>
        {
            ScanProgress = p.total == 0 ? 0 : (double)p.current / p.total;
        });

        try
        {
            var songs = await _scanner.ScanAsync(SourceFolders, progress, _scanCancellation.Token);
            _allSongs.Clear();
            _allSongs.AddRange(songs);

            Queue.Clear();
            foreach (var song in _allSongs)
            {
                Queue.Add(song);
            }

            ApplyFilters();
        }
        finally
        {
            IsScanning = false;
        }
    }

    public async Task ImportPlaylistAsync()
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".m3u");
        picker.FileTypeFilter.Add(".m3u8");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(_window));

        var file = await picker.PickSingleFileAsync();
        if (file is null) return;

        var dictionary = _allSongs.ToDictionary(song => song.FilePath, StringComparer.OrdinalIgnoreCase);
        var playlist = PlaylistParser.LoadFromM3U(file.Path, dictionary, SourceFolders.FirstOrDefault());
        Playlists.Add(playlist);
    }

    public void PlaySelectedSong()
    {
        if (SelectedSong is null) return;

        var index = Queue.IndexOf(SelectedSong);
        if (index < 0) return;

        PlayAt(index);
    }

    public void TogglePlayPause()
    {
        if (CurrentSong is null)
        {
            if (Queue.Count > 0)
            {
                PlayAt(0);
            }
            return;
        }

        if (IsPlaying)
        {
            _player.Pause();
            IsPlaying = false;
        }
        else
        {
            _player.Play();
            IsPlaying = true;
        }
    }

    public void Next()
    {
        if (Queue.Count == 0) return;

        if (RepeatMode == RepeatMode.One)
        {
            PlayAt(_currentIndex);
            return;
        }

        var nextIndex = _currentIndex + 1;
        if (nextIndex >= Queue.Count)
        {
            if (RepeatMode == RepeatMode.All)
            {
                nextIndex = 0;
            }
            else
            {
                _player.Pause();
                IsPlaying = false;
                return;
            }
        }

        PlayAt(nextIndex);
    }

    public void Previous()
    {
        if (Queue.Count == 0) return;

        if (_player.PlaybackSession.Position > TimeSpan.FromSeconds(3))
        {
            _player.PlaybackSession.Position = TimeSpan.Zero;
            return;
        }

        var previousIndex = _currentIndex - 1;
        if (previousIndex < 0)
        {
            previousIndex = RepeatMode == RepeatMode.All ? Queue.Count - 1 : 0;
        }

        PlayAt(previousIndex);
    }

    public void Seek(double ratio)
    {
        if (_player.Source is null) return;
        ratio = Math.Clamp(ratio, 0, 1);

        var duration = _player.PlaybackSession.NaturalDuration;
        if (duration <= TimeSpan.Zero) return;

        _player.PlaybackSession.Position = TimeSpan.FromTicks((long)(duration.Ticks * ratio));
    }

    public void ToggleShuffle()
    {
        IsShuffleEnabled = !IsShuffleEnabled;

        if (IsShuffleEnabled)
        {
            var current = CurrentSong;
            var shuffled = Queue.OrderBy(_ => Guid.NewGuid()).ToList();

            Queue.Clear();
            foreach (var song in shuffled)
            {
                Queue.Add(song);
            }

            if (current is not null)
            {
                _currentIndex = Queue.IndexOf(current);
            }
        }
        else
        {
            Queue.Clear();
            foreach (var song in _allSongs)
            {
                Queue.Add(song);
            }

            if (CurrentSong is not null)
            {
                _currentIndex = Queue.IndexOf(CurrentSong);
            }
        }
    }

    public void CycleRepeatMode()
    {
        RepeatMode = RepeatMode switch
        {
            RepeatMode.Off => RepeatMode.All,
            RepeatMode.All => RepeatMode.One,
            _ => RepeatMode.Off,
        };
    }

    public void MoveQueueUp()
    {
        if (SelectedQueueSong is null) return;
        var index = Queue.IndexOf(SelectedQueueSong);
        if (index <= 0) return;

        Queue.Move(index, index - 1);
        if (_currentIndex == index)
        {
            _currentIndex--;
        }
        else if (_currentIndex == index - 1)
        {
            _currentIndex++;
        }
    }

    public void MoveQueueDown()
    {
        if (SelectedQueueSong is null) return;
        var index = Queue.IndexOf(SelectedQueueSong);
        if (index < 0 || index >= Queue.Count - 1) return;

        Queue.Move(index, index + 1);
        if (_currentIndex == index)
        {
            _currentIndex++;
        }
        else if (_currentIndex == index + 1)
        {
            _currentIndex--;
        }
    }

    private void PlayAt(int index)
    {
        if (index < 0 || index >= Queue.Count) return;

        _currentIndex = index;
        CurrentSong = Queue[index];
        CurrentSong.PlayCount++;

        _player.Source = MediaSource.CreateFromUri(CurrentSong.FileUri);
        _player.Play();
        IsPlaying = true;
    }

    private void OnMediaEnded()
    {
        if (RepeatMode == RepeatMode.One)
        {
            PlayAt(_currentIndex);
            return;
        }

        Next();
    }

    private void ApplyFilters()
    {
        IEnumerable<Song> query = _allSongs;

        if (!string.IsNullOrWhiteSpace(SearchQuery))
        {
            query = query.Where(song =>
                song.Title.Contains(SearchQuery, StringComparison.CurrentCultureIgnoreCase) ||
                song.Artist.Contains(SearchQuery, StringComparison.CurrentCultureIgnoreCase) ||
                song.Album.Contains(SearchQuery, StringComparison.CurrentCultureIgnoreCase));
        }

        query = SelectedSort switch
        {
            "Artist" => query.OrderBy(song => song.Artist, StringComparer.CurrentCultureIgnoreCase).ThenBy(song => song.Title, StringComparer.CurrentCultureIgnoreCase),
            "Album" => query.OrderBy(song => song.Album, StringComparer.CurrentCultureIgnoreCase).ThenBy(song => song.TrackNumber).ThenBy(song => song.Title, StringComparer.CurrentCultureIgnoreCase),
            "PlayCount" => query.OrderByDescending(song => song.PlayCount).ThenBy(song => song.Title, StringComparer.CurrentCultureIgnoreCase),
            _ => query.OrderBy(song => song.Title, StringComparer.CurrentCultureIgnoreCase),
        };

        Songs.Clear();
        foreach (var song in query)
        {
            Songs.Add(song);
        }
    }

    private void UpdatePosition()
    {
        var session = _player.PlaybackSession;
        var duration = session.NaturalDuration;
        var position = session.Position;

        PositionText = position.ToString(@"mm\:ss");
        DurationText = duration > TimeSpan.Zero ? duration.ToString(@"mm\:ss") : "00:00";

        PositionRatio = duration <= TimeSpan.Zero ? 0 : Math.Clamp(position.TotalSeconds / duration.TotalSeconds, 0, 1);
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnPropertyChanged([CallerMemberName] string? propertyName = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
