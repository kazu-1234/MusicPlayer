using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using MusicPlayer.WinUI.Models;
using MusicPlayer.WinUI.Services;
using System.Collections.ObjectModel;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace MusicPlayer.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly MusicLibraryService _libraryService = new();
    private readonly PlaylistService _playlistService = new();
    private readonly PlaybackService _playbackService = new();

    private readonly ObservableCollection<Song> _allSongs = [];
    private readonly ObservableCollection<Song> _filteredSongs = [];
    private readonly ObservableCollection<Song> _queue = [];
    private readonly ObservableCollection<PlaylistItem> _playlists = [];
    private readonly List<string> _libraryFolders = [];
    private bool _isPlaying;

    public MainWindow()
    {
        InitializeComponent();
        SongsListView.ItemsSource = _filteredSongs;
        QueueListView.ItemsSource = _queue;
        PlaylistsListView.ItemsSource = _playlists;

        _playbackService.SongChanged += song =>
        {
            DispatcherQueue.TryEnqueue(() =>
            {
                NowPlayingText.Text = song is null ? "再生中: なし" : $"再生中: {song.Title} - {song.Artist}";
                SyncQueue();
            });
        };

        _playbackService.PlaybackStateChanged += playing =>
        {
            DispatcherQueue.TryEnqueue(() =>
            {
                _isPlaying = playing;
                PlayPauseButton.Content = playing ? "一時停止" : "再生";
            });
        };

        _playbackService.PositionChanged += (position, duration) =>
        {
            DispatcherQueue.TryEnqueue(() =>
            {
                PositionSlider.Maximum = Math.Max(1, duration.TotalSeconds);
                PositionSlider.Value = Math.Min(PositionSlider.Maximum, position.TotalSeconds);
            });
        };
    }

    private async void OnPickFolderClick(object sender, RoutedEventArgs e)
    {
        var picker = new FolderPicker();
        picker.FileTypeFilter.Add("*");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));

        var folder = await picker.PickSingleFolderAsync();
        if (folder is null)
        {
            return;
        }

        if (!_libraryFolders.Contains(folder.Path, StringComparer.OrdinalIgnoreCase))
        {
            _libraryFolders.Add(folder.Path);
        }

        await ScanLibraryAsync();
    }

    private async void OnScanClick(object sender, RoutedEventArgs e)
    {
        await ScanLibraryAsync();
    }

    private async Task ScanLibraryAsync()
    {
        if (_libraryFolders.Count == 0)
        {
            return;
        }

        ScanButton.IsEnabled = false;
        try
        {
            var songs = await _libraryService.ScanFoldersAsync(_libraryFolders);
            _allSongs.Clear();
            foreach (var song in songs)
            {
                _allSongs.Add(song);
            }

            ApplyFilter(SearchTextBox.Text);
            _playbackService.SetQueue(_allSongs);
            SyncQueue();
        }
        finally
        {
            ScanButton.IsEnabled = true;
        }
    }

    private void OnSearchTextChanged(object sender, TextChangedEventArgs e)
    {
        ApplyFilter(SearchTextBox.Text);
    }

    private void ApplyFilter(string? keyword)
    {
        _filteredSongs.Clear();

        IEnumerable<Song> songs = _allSongs;
        if (!string.IsNullOrWhiteSpace(keyword))
        {
            songs = songs.Where(song =>
                song.Title.Contains(keyword, StringComparison.CurrentCultureIgnoreCase) ||
                song.Artist.Contains(keyword, StringComparison.CurrentCultureIgnoreCase) ||
                song.Album.Contains(keyword, StringComparison.CurrentCultureIgnoreCase));
        }

        foreach (var song in songs)
        {
            _filteredSongs.Add(song);
        }
    }

    private void OnSongItemClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is not Song song)
        {
            return;
        }

        _playbackService.PlaySong(song, _filteredSongs);
        SyncQueue();
    }

    private void OnPlayPauseClick(object sender, RoutedEventArgs e)
    {
        if (_isPlaying)
        {
            _playbackService.Pause();
        }
        else if (_playbackService.CurrentSong is null && _queue.Count > 0)
        {
            _playbackService.PlaySongAtIndex(0);
        }
        else
        {
            _playbackService.Resume();
        }
    }

    private void OnPrevClick(object sender, RoutedEventArgs e)
    {
        _playbackService.PlayPrevious();
    }

    private void OnNextClick(object sender, RoutedEventArgs e)
    {
        _playbackService.PlayNext();
    }

    private void OnShuffleClick(object sender, RoutedEventArgs e)
    {
        _playbackService.ToggleShuffle();
        ShuffleButton.Content = _playbackService.IsShuffleEnabled ? "シャッフル: ON" : "シャッフル: OFF";
    }

    private void OnRepeatClick(object sender, RoutedEventArgs e)
    {
        _playbackService.ToggleRepeatMode();
        RepeatButton.Content = $"リピート: {ToRepeatText(_playbackService.RepeatMode)}";
    }

    private static string ToRepeatText(RepeatMode mode) => mode switch
    {
        RepeatMode.All => "ALL",
        RepeatMode.One => "ONE",
        _ => "OFF"
    };

    private void OnPositionSliderChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (Math.Abs(e.NewValue - e.OldValue) < 1)
        {
            return;
        }

        _playbackService.SeekTo(TimeSpan.FromSeconds(e.NewValue));
    }

    private async void OnImportPlaylistClick(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".m3u");
        picker.FileTypeFilter.Add(".m3u8");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));

        var file = await picker.PickSingleFileAsync();
        if (file is null)
        {
            return;
        }

        var playlist = await _playlistService.ParseM3uAsync(file.Path, _allSongs, BasePathTextBox.Text);
        if (playlist is null)
        {
            return;
        }

        _playlists.Add(new PlaylistItem(playlist));
    }

    private void OnPlaylistItemClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is not PlaylistItem item)
        {
            return;
        }

        _filteredSongs.Clear();
        foreach (var song in item.Playlist.Songs)
        {
            _filteredSongs.Add(song);
        }

        _playbackService.SetQueue(item.Playlist.Songs);
        SyncQueue();
    }

    private void SyncQueue()
    {
        _queue.Clear();
        foreach (var song in _playbackService.Queue)
        {
            _queue.Add(song);
        }
    }

    private sealed class PlaylistItem(Playlist playlist)
    {
        public Playlist Playlist { get; } = playlist;
        public string Name => Playlist.Name;
        public string SongCountText => $"{Playlist.Songs.Count} 曲";
    }
}
