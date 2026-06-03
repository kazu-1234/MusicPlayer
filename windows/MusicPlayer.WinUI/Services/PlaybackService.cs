using MusicPlayer.WinUI.Models;
using Windows.Media.Core;
using Windows.Media.Playback;

namespace MusicPlayer.WinUI.Services;

public sealed class PlaybackService : IDisposable
{
    private readonly MediaPlayer _player = new();
    private readonly List<Song> _queue = [];

    public event Action<Song?>? SongChanged;
    public event Action<bool>? PlaybackStateChanged;
    public event Action<TimeSpan, TimeSpan>? PositionChanged;

    public Song? CurrentSong { get; private set; }
    public int CurrentIndex { get; private set; } = -1;
    public bool IsShuffleEnabled { get; private set; }
    public RepeatMode RepeatMode { get; set; } = RepeatMode.Off;

    public IReadOnlyList<Song> Queue => _queue;

    public PlaybackService()
    {
        _player.PlaybackSession.PositionChanged += (_, _) =>
        {
            PositionChanged?.Invoke(_player.PlaybackSession.Position, _player.PlaybackSession.NaturalDuration);
        };

        _player.MediaEnded += (_, _) => PlayNext();
        _player.PlaybackSession.PlaybackStateChanged += (_, _) =>
        {
            var isPlaying = _player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing;
            PlaybackStateChanged?.Invoke(isPlaying);
        };
    }

    public void SetQueue(IEnumerable<Song> songs)
    {
        _queue.Clear();
        _queue.AddRange(songs);
    }

    public void PlaySong(Song song, IEnumerable<Song> context)
    {
        var list = context.ToList();
        if (IsShuffleEnabled)
        {
            var rest = list.Where(s => s.Path != song.Path).OrderBy(_ => Guid.NewGuid());
            list = [song, .. rest];
        }

        SetQueue(list);
        var index = _queue.FindIndex(s => s.Path == song.Path);
        PlaySongAtIndex(index >= 0 ? index : 0);
    }

    public void PlaySongAtIndex(int index)
    {
        if (index < 0 || index >= _queue.Count)
        {
            return;
        }

        var song = _queue[index];
        if (!File.Exists(song.Path))
        {
            return;
        }

        CurrentIndex = index;
        CurrentSong = song;
        _player.Source = MediaSource.CreateFromUri(new Uri(song.Path));
        _player.Play();
        SongChanged?.Invoke(song);
    }

    public void Pause() => _player.Pause();

    public void Resume() => _player.Play();

    public void SeekTo(TimeSpan position) => _player.PlaybackSession.Position = position;

    public void Stop()
    {
        _player.Pause();
        _player.Source = null;
        CurrentSong = null;
        CurrentIndex = -1;
        SongChanged?.Invoke(null);
    }

    public void PlayNext()
    {
        if (_queue.Count == 0)
        {
            return;
        }

        if (RepeatMode == RepeatMode.One)
        {
            PlaySongAtIndex(CurrentIndex);
            return;
        }

        var next = CurrentIndex + 1;
        if (next >= _queue.Count)
        {
            if (RepeatMode == RepeatMode.All)
            {
                next = 0;
            }
            else
            {
                Stop();
                return;
            }
        }

        PlaySongAtIndex(next);
    }

    public void PlayPrevious()
    {
        if (_queue.Count == 0)
        {
            return;
        }

        var previous = CurrentIndex <= 0 ? (_queue.Count - 1) : (CurrentIndex - 1);
        PlaySongAtIndex(previous);
    }

    public void ToggleShuffle() => IsShuffleEnabled = !IsShuffleEnabled;

    public void ToggleRepeatMode()
    {
        RepeatMode = RepeatMode switch
        {
            RepeatMode.Off => RepeatMode.All,
            RepeatMode.All => RepeatMode.One,
            _ => RepeatMode.Off
        };
    }

    public void Dispose()
    {
        _player.Dispose();
    }
}
