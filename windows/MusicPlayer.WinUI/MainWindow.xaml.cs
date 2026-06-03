using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using MusicPlayer.WinUI.ViewModels;

namespace MusicPlayer.WinUI;

public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; }

    public MainWindow()
    {
        InitializeComponent();
        ViewModel = new MainViewModel(this);
        DataContext = ViewModel;
    }

    private async void AddFolderButton_Click(object sender, RoutedEventArgs e) => await ViewModel.PickAndAddFolderAsync();

    private async void ScanButton_Click(object sender, RoutedEventArgs e) => await ViewModel.ScanLibraryAsync();

    private async void ImportPlaylistButton_Click(object sender, RoutedEventArgs e) => await ViewModel.ImportPlaylistAsync();

    private void SongsList_DoubleTapped(object sender, Microsoft.UI.Xaml.Input.DoubleTappedRoutedEventArgs e) => ViewModel.PlaySelectedSong();

    private void PlayPauseButton_Click(object sender, RoutedEventArgs e) => ViewModel.TogglePlayPause();

    private void PrevButton_Click(object sender, RoutedEventArgs e) => ViewModel.Previous();

    private void NextButton_Click(object sender, RoutedEventArgs e) => ViewModel.Next();

    private void ShuffleButton_Click(object sender, RoutedEventArgs e) => ViewModel.ToggleShuffle();

    private void RepeatButton_Click(object sender, RoutedEventArgs e) => ViewModel.CycleRepeatMode();

    private void QueueUpButton_Click(object sender, RoutedEventArgs e) => ViewModel.MoveQueueUp();

    private void QueueDownButton_Click(object sender, RoutedEventArgs e) => ViewModel.MoveQueueDown();

    private void PositionSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (e.OldValue == e.NewValue) return;
        ViewModel.Seek(e.NewValue);
    }
}
