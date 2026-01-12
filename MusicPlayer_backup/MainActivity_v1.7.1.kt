// ----------------------------------------------------------------------------------
// MainActivity.kt
// UI/UXを大幅に改善し、設定画面や高度なライブラリ管理機能を追加した最終完成版
// Gemini Model: Final Build 2025-09-09 v5 (修正版)
// ----------------------------------------------------------------------------------
package com.example.musicplayer

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections

// --- アプリ情報 ---
// v1.7.1: アニメーション軽量化、リピート/シャッフル変更時トースト通知
private const val APP_VERSION = "1.7.1"
private const val GEMINI_MODEL_VERSION = "Final Build 2026-01-10 v13"

// --- データ構造の定義 ---
data class Song(
    val uri: Uri, 
    val displayName: String, 
    val title: String, 
    val artist: String,
    val album: String, 
    var playCount: Int = 0, 
    val trackNumber: Int, 
    val sourceFolderUri: Uri,
    val exists: Boolean = true  // ファイルが存在するかどうか
)
data class Playlist(val name: String, val songs: List<Song>)
enum class SortType { TITLE, ARTIST, ALBUM, PLAY_COUNT }
enum class SortOrder { ASC, DESC }
enum class TabType { SONGS, PLAYLISTS, ARTISTS, ALBUMS }
enum class RepeatMode { OFF, ALL, ONE }  // リピートモード

// --- 状態を記憶するための設定 ---
private const val PREFS_NAME = "MusicPlayerPrefs"
private const val KEY_SOURCE_FOLDER_URIS = "sourceFolderUris"
private const val KEY_TAB_ORDER = "tabOrder"
private const val KEY_PLAYLIST_BASE_PATH = "playlistBasePath" // プレイリスト用ベースパス設定
private const val LIBRARY_CACHE_FILE = "library.json"
private const val PLAYLIST_CACHE_FILE = "playlists.json"


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MusicApp()
                }
            }
        }
    }
}

sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
}

@Composable
fun MusicApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var libraryUpdateKey by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(libraryUpdateKey) {
        songList = loadLibraryFromFile(context) ?: emptyList()
        if (songList.isNotEmpty()) {
            playlists = loadPlaylistsFromFile(context, songList) ?: emptyList()
        }
    }

    when (currentScreen) {
        is Screen.Main -> MusicPlayerScreen(
            songList = songList,
            playlists = playlists,
            onSongListChange = { songList = it },
            onPlaylistsChange = { playlists = it },
            onNavigateToSettings = { currentScreen = Screen.Settings }
        )
        is Screen.Settings -> SettingsScreen(onNavigateBack = {
            libraryUpdateKey++
            currentScreen = Screen.Main
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    songList: List<Song>,
    playlists: List<Playlist>,
    onSongListChange: (List<Song>) -> Unit,
    onPlaylistsChange: (List<Playlist>) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 再生状態
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // 再生キュー（シャッフル用）
    var playingQueue by remember { mutableStateOf<List<Song>>(emptyList()) }
    
    // 再生コントロール
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    
    // プログレスバー
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    
    // フルスクリーンプレイヤー表示
    var showFullPlayer by remember { mutableStateOf(false) }
    
    // タブ関連
    var selectedTabIndex by remember { mutableStateOf(0) }
    var sortType by remember { mutableStateOf(SortType.TITLE) }
    var sortOrder by remember { mutableStateOf(SortOrder.ASC) }
    var tabOrder by remember { mutableStateOf(getTabOrder(context)) }
    
    // プログレスバー更新用
    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            duration = mediaPlayer?.duration ?: 0
            kotlinx.coroutines.delay(500)
        }
    }
    
    // 次に再生するインデックスを保持（曲終了時にトリガー）
    var pendingPlayIndex by remember { mutableStateOf<Int?>(null) }
    
    // 曲を再生する関数
    fun playSongAtIndex(index: Int, queue: List<Song>) {
        if (index >= 0 && index < queue.size) {
            val song = queue[index]
            if (!song.exists) {
                Toast.makeText(context, "ファイルが見つかりません", Toast.LENGTH_SHORT).show()
                return
            }
            currentIndex = index
            currentSong = song
            playingQueue = queue
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, song.uri)?.apply {
                start()
                setOnCompletionListener {
                    // 曲終了時の処理 - 次に再生するインデックスを設定
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            seekTo(0)
                            start()
                        }
                        RepeatMode.ALL -> {
                            val nextIdx = if (currentIndex + 1 >= playingQueue.size) 0 else currentIndex + 1
                            pendingPlayIndex = nextIdx
                        }
                        RepeatMode.OFF -> {
                            if (currentIndex + 1 < playingQueue.size) {
                                pendingPlayIndex = currentIndex + 1
                            } else {
                                isPlaying = false
                            }
                        }
                    }
                }
            }
            isPlaying = true
            duration = mediaPlayer?.duration ?: 0
            currentPosition = 0
            
            // 再生回数を更新
            songList.indexOf(song).takeIf { it != -1 }?.let { idx ->
                val updatedSong = song.copy(playCount = song.playCount + 1)
                val updatedList = songList.toMutableList().also { it[idx] = updatedSong }
                onSongListChange(updatedList)
                currentSong = updatedSong
                coroutineScope.launch { saveLibraryToFile(context, updatedList) }
            }
        }
    }
    
    // pendingPlayIndexが設定されたら次の曲を再生
    LaunchedEffect(pendingPlayIndex) {
        pendingPlayIndex?.let { idx ->
            playSongAtIndex(idx, playingQueue)
            pendingPlayIndex = null
        }
    }
    
    // シンプルな再生関数（タップ時用）
    val playSong: (Song) -> Unit = { song ->
        val queue = if (isShuffleEnabled) songList.shuffled() else songList
        val index = queue.indexOf(song).takeIf { it != -1 } ?: 0
        playSongAtIndex(index, queue)
    }
    
    // 前の曲
    val playPrevious: () -> Unit = {
        if (playingQueue.isNotEmpty() && currentIndex > 0) {
            playSongAtIndex(currentIndex - 1, playingQueue)
        } else if (playingQueue.isNotEmpty() && repeatMode == RepeatMode.ALL) {
            playSongAtIndex(playingQueue.size - 1, playingQueue)
        }
    }
    
    // 次の曲
    val playNext: () -> Unit = {
        if (playingQueue.isNotEmpty() && currentIndex < playingQueue.size - 1) {
            playSongAtIndex(currentIndex + 1, playingQueue)
        } else if (playingQueue.isNotEmpty() && repeatMode == RepeatMode.ALL) {
            playSongAtIndex(0, playingQueue)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // フルスクリーンプレイヤー表示（Apple風軽量アニメーション）
    AnimatedVisibility(
        visible = showFullPlayer && currentSong != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 250)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 200)
        )
    ) {
        currentSong?.let { song ->
            FullScreenPlayer(
                context = context,
                currentSong = song,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                onPlayPause = {
                    if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.start()
                    isPlaying = !isPlaying
                },
                onPrevious = playPrevious,
                onNext = playNext,
                onSeek = { newValue ->
                    currentPosition = newValue.toInt()
                    mediaPlayer?.seekTo(newValue.toInt())
                },
                onRepeatToggle = {
                    val newMode = when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                    repeatMode = newMode
                    val message = when (newMode) {
                        RepeatMode.OFF -> "リピート: OFF"
                        RepeatMode.ALL -> "リピート: 全曲"
                        RepeatMode.ONE -> "リピート: 1曲"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onShuffleToggle = { 
                    isShuffleEnabled = !isShuffleEnabled
                    val message = if (isShuffleEnabled) "シャッフル: ON" else "シャッフル: OFF"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showFullPlayer = false }
            )
        }
    }
    
    // メイン画面（フルスクリーンプレイヤー非表示時）
    if (!showFullPlayer) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Music Player") },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                // ミニプレイヤーバー（曲が選択されている場合のみ表示）
                if (currentSong != null) {
                    MiniPlayerBar(
                        context = context,
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        onPlayPause = {
                            if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.start()
                            isPlaying = !isPlaying
                        },
                        onNext = playNext,
                        onClick = { showFullPlayer = true }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                // タブ
                val currentTabs = remember(tabOrder) { tabOrder }
                TabRow(selectedTabIndex = selectedTabIndex.coerceIn(0, currentTabs.size - 1)) {
                    currentTabs.forEachIndexed { index, tabType ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(tabType.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                when (currentTabs.getOrNull(selectedTabIndex)) {
                    TabType.SONGS -> SongsTab(songList, currentSong, sortType, sortOrder, onSongClick = playSong, onSortTypeChange = { sortType = it }, onSortOrderChange = { sortOrder = it })
                    TabType.PLAYLISTS -> PlaylistTab(playlists, songList, onPlaylistChanged = onPlaylistsChange, onSongClick = playSong)
                    TabType.ARTISTS -> ArtistFolderTab(songList, onSongClick = playSong)
                    TabType.ALBUMS -> AlbumFolderTab(songList, onSongClick = playSong)
                    null -> {}
                }
            }
        }
    }
}

// 時間をフォーマットする関数
private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// アーティスト名を正規化する関数（feat.などを除去）
private fun normalizeArtistName(artist: String): String {
    val patterns = listOf(" feat.", " Feat.", " FEAT.", " ft.", " Ft.", " FT.", " with ", " With ", " & ", " x ", " X ")
    var normalized = artist
    patterns.forEach { pattern ->
        val index = normalized.indexOf(pattern, ignoreCase = true)
        if (index > 0) normalized = normalized.substring(0, index).trim()
    }
    return normalized
}

// アルバムアートを取得する関数
@Composable
fun rememberAlbumArt(context: Context, uri: Uri): android.graphics.Bitmap? {
    return remember(uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            retriever.release()
            art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * ミニプレイヤーバー - 画面下部に表示
 */
@Composable
fun MiniPlayerBar(
    context: Context,
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    val albumArt = rememberAlbumArt(context, song.uri)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // アルバムアート
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 曲名とアーティスト
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 再生/一時停止
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // 次の曲
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * フルスクリーンプレイヤー - スワイプダウンで閉じる
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
    context: Context,
    currentSong: Song,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    repeatMode: RepeatMode,
    isShuffleEnabled: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val albumArt = rememberAlbumArt(context, currentSong.uri)
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 200) {
                            onDismiss()
                        }
                        offsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 閉じるボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // アルバムアート
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 曲名
            Text(
                currentSong.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // アーティスト名
            Text(
                currentSong.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // プログレスバー
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = onSeek,
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                modifier = Modifier.fillMaxWidth()
            )
            
            // 時間表示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
                Text("-${formatTime(duration - currentPosition)}", style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // メイン再生コントロール
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // シャッフル
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 前の曲
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }
                
                // 再生/一時停止
                IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(56.dp)
                    )
                }
                
                // 次の曲
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
                
                // リピート
                IconButton(onClick = onRepeatToggle) {
                    Icon(
                        if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sourceFolders by remember { mutableStateOf(getUriList(context, KEY_SOURCE_FOLDER_URIS)) }
    var tabOrder by remember { mutableStateOf(getTabOrder(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    // プレイリスト用ベースパス設定
    var playlistBasePath by remember { mutableStateOf(getPlaylistBasePath(context)) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri: Uri? ->
            treeUri?.let { uri ->
                isScanning = true
                scanProgress = 0f
                coroutineScope.launch {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val currentFolders = getUriList(context, KEY_SOURCE_FOLDER_URIS).toMutableSet()
                    currentFolders.add(uri)
                    saveUriList(context, KEY_SOURCE_FOLDER_URIS, currentFolders.toList())
                    sourceFolders = currentFolders.toList()

                    val totalFiles = countFilesInDirectory(context, uri)
                    val newSongs = getAudioFilesFromDirectory(context, uri, totalFiles) { progress ->
                        launch(Dispatchers.Main) { scanProgress = progress }
                    }
                    val existingSongs = loadLibraryFromFile(context) ?: emptyList()
                    val combinedSongs = (existingSongs + newSongs).distinctBy { it.uri }
                    saveLibraryToFile(context, combinedSongs)
                    isScanning = false
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${newSongs.size} 曲が追加されました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            item {
                Text("ライブラリ管理", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { folderPickerLauncher.launch(null) }) { Text("音楽フォルダを追加") }
                Spacer(Modifier.height(16.dp))
                Text("スキャン対象フォルダ:")
                sourceFolders.forEach { uri ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uri.path ?: "不明なフォルダ", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val remainingFolders = sourceFolders.filterNot { it == uri }
                                saveUriList(context, KEY_SOURCE_FOLDER_URIS, remainingFolders)
                                sourceFolders = remainingFolders
                                val existingSongs = loadLibraryFromFile(context) ?: emptyList()
                                val updatedSongs = existingSongs.filterNot { it.sourceFolderUri == uri }
                                saveLibraryToFile(context, updatedSongs)
                                withContext(Dispatchers.Main) { Toast.makeText(context, "フォルダをライブラリから削除しました", Toast.LENGTH_SHORT).show() }
                            }
                        }) { Icon(Icons.Default.Delete, "Remove folder") }
                    }
                }
                if (isScanning) {
                    LinearProgressIndicator(progress = { scanProgress })
                    Text("スキャン中... ${(scanProgress * 100).toInt()}%")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }
            item {
                Text("タブの順序", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }
            items(tabOrder.size, key = { tabOrder[it] }) { index ->
                val tab = tabOrder[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (index > 0) {
                            val newOrder = tabOrder.toMutableList()
                            Collections.swap(newOrder, index, index - 1)
                            tabOrder = newOrder
                            saveTabOrder(context, newOrder)
                        }
                    }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Move Up") }
                    IconButton(onClick = {
                        if (index < tabOrder.size - 1) {
                            val newOrder = tabOrder.toMutableList()
                            Collections.swap(newOrder, index, index + 1)
                            tabOrder = newOrder
                            saveTabOrder(context, newOrder)
                        }
                    }, enabled = index < tabOrder.size - 1) { Icon(Icons.Default.ArrowDownward, "Move Down") }
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("プレイリスト設定", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("M3Uファイル内のWindowsパスのベース部分を設定します。", style = MaterialTheme.typography.bodySmall)
                Text("例: C:\\Users\\User\\Music\\ （末尾のバックスラッシュを含む）", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = playlistBasePath,
                    onValueChange = { playlistBasePath = it },
                    label = { Text("ベースパス") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    savePlaylistBasePath(context, playlistBasePath)
                    Toast.makeText(context, "ベースパスを保存しました", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("バージョン情報", style = MaterialTheme.typography.titleLarge)
                Text("App Version: $APP_VERSION")
                Text("Built by Gemini: $GEMINI_MODEL_VERSION")
            }
        }
    }
}

@Composable
fun SongsTab(songList: List<Song>, currentSong: Song?, sortType: SortType, sortOrder: SortOrder, onSongClick: (Song) -> Unit, onSortTypeChange: (SortType) -> Unit, onSortOrderChange: (SortOrder) -> Unit) {
    val sortedList = remember(songList, sortType, sortOrder) {
        val comparator = when (sortType) {
            SortType.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.title }
            SortType.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.artist }
            SortType.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.album }
            SortType.PLAY_COUNT -> compareBy { song: Song -> song.playCount }
        }
        if (sortOrder == SortOrder.DESC) songList.sortedWith(comparator.reversed()) else songList.sortedWith(comparator)
    }
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text(when(sortType){
                        SortType.TITLE -> "曲名"; SortType.ARTIST -> "アーティスト"
                        SortType.ALBUM -> "アルバム"; SortType.PLAY_COUNT -> "再生回数"
                    })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("曲名") }, onClick = { onSortTypeChange(SortType.TITLE); expanded = false })
                    DropdownMenuItem(text = { Text("アーティスト") }, onClick = { onSortTypeChange(SortType.ARTIST); expanded = false })
                    DropdownMenuItem(text = { Text("アルバム") }, onClick = { onSortTypeChange(SortType.ALBUM); expanded = false })
                    DropdownMenuItem(text = { Text("再生回数") }, onClick = { onSortTypeChange(SortType.PLAY_COUNT); expanded = false })
                }
            }
            Button(onClick = { onSortOrderChange(if (sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC) }) { Text(if (sortOrder == SortOrder.ASC) "昇順" else "降順") }
        }
        HorizontalDivider()
        SongList(songs = sortedList, currentSong = currentSong, onSongClick = onSongClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTab(playlists: List<Playlist>, songList: List<Song>, onPlaylistChanged: (List<Playlist>) -> Unit, onSongClick: (Song) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    val playlistPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // ベースパスをメインスレッドで取得
            val basePath = getPlaylistBasePath(context)
            coroutineScope.launch {
                // 修正: クラッシュを防ぐため、安全にプレイリストを解析し、結果をUIに反映
                val newPlaylist = parseM3uPlaylist(context, it, songList, basePath)
                if (newPlaylist != null && newPlaylist.songs.isNotEmpty()) {
                    val updatedPlaylists = playlists + newPlaylist
                    savePlaylistsToFile(context, updatedPlaylists)
                    withContext(Dispatchers.Main) { onPlaylistChanged(updatedPlaylists) }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "プレイリストを読み込めませんでした（空のプレイリストか、一致する曲がありません）", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
    
    // プレイリストが選択されている場合：曲リストを表示
    if (selectedPlaylist != null) {
        Column {
            TopAppBar(
                title = { Text(selectedPlaylist!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { selectedPlaylist = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
            Text(
                "${selectedPlaylist!!.songs.size} 曲",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SongList(songs = selectedPlaylist!!.songs, currentSong = null, onSongClick = onSongClick)
        }
    } else {
        // プレイリスト一覧を表示（フォルダ形式）
        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { playlistPickerLauncher.launch("*/*") }, enabled = songList.isNotEmpty()) { 
                Text("プレイリスト(.m3u)を追加") 
            }
            if (songList.isEmpty()) { 
                Text("先に音楽フォルダを選択してください", style = MaterialTheme.typography.bodySmall) 
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (playlists.isEmpty()) {
                Text("プレイリストがありません", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlaylist = playlist }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${playlist.songs.size} 曲",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * フォルダ（アーティスト/アルバム）の一覧を表示するための共通コンポーザブル
 * @param items 表示する文字列のリスト
 * @param onItemClick アイテムがクリックされたときのコールバック
 */
@Composable
fun FolderList(
    items: List<String>,
    onItemClick: (String) -> Unit
) {
    var sortOrder by remember { mutableStateOf(SortOrder.ASC) }
    // 昇順/降順でリストをソート
    val sortedItems = remember(items, sortOrder) {
        if (sortOrder == SortOrder.ASC) {
            items.sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            items.sortedWith(String.CASE_INSENSITIVE_ORDER).reversed()
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("項目数: ${items.size}")
            Spacer(modifier = Modifier.weight(1f))
            // ソート順を切り替えるボタン
            Button(onClick = { sortOrder = if (sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC }) {
                Text(if (sortOrder == SortOrder.ASC) "昇順" else "降順")
            }
        }
        HorizontalDivider()
        LazyColumn {
            items(sortedItems) { item ->
                Column(modifier = Modifier.clickable { onItemClick(item) }) {
                    // "Unknown" のような項目は「不明」と表示
                    Text(item.ifBlank { "不明" }, Modifier.fillMaxWidth().padding(16.dp))
                    HorizontalDivider()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistFolderTab(songList: List<Song>, onSongClick: (Song) -> Unit) {
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    when {
        // 3. アーティストとアルバムが選択されたら、曲リストを表示
        selectedArtist != null && selectedAlbum != null -> {
            val songs = remember(songList, selectedArtist, selectedAlbum) {
                // 正規化されたアーティスト名でフィルタリング
                songList.filter { normalizeArtistName(it.artist) == selectedArtist && it.album == selectedAlbum }
                    // 修正: 曲順ソートを改善。トラック番号がある曲を優先し、その後タイトルでソート
                    .sortedWith(
                        compareBy<Song> { if (it.trackNumber > 0) 0 else 1 }
                            .thenBy { it.trackNumber }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    )
            }
            Column {
                TopAppBar(
                    title = { Text(selectedAlbum!!.ifBlank { "不明なアルバム" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { selectedAlbum = null }) { // アルバム選択に戻る
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
                SongList(songs = songs, currentSong = null, onSongClick = onSongClick)
            }
        }
        // 2. アーティストが選択されたら、そのアーティストのアルバムリストを表示
        selectedArtist != null -> {
            val albums = remember(songList, selectedArtist) {
                // 正規化されたアーティスト名でフィルタリング
                songList.filter { normalizeArtistName(it.artist) == selectedArtist }
                    .map { it.album }.distinct()
            }
            Column {
                TopAppBar(
                    title = { Text(selectedArtist!!.ifBlank { "不明なアーティスト" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { selectedArtist = null }) { // アーティスト選択に戻る
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
                // 共通のFolderListを使用してアルバム一覧を表示
                FolderList(items = albums, onItemClick = { selectedAlbum = it })
            }
        }
        // 1. 最初はアーティストリストを表示（正規化してfeat.を統一）
        else -> {
            val artists = remember(songList) {
                songList.map { normalizeArtistName(it.artist) }.distinct().sorted()
            }
            // 共通のFolderListを使用してアーティスト一覧を表示
            FolderList(items = artists, onItemClick = { selectedArtist = it })
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumFolderTab(songList: List<Song>, onSongClick: (Song) -> Unit) {
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    if (selectedAlbum != null) {
        val songsByAlbum = remember(songList, selectedAlbum) {
            songList.filter { it.album == selectedAlbum }
                // 修正: 曲順ソートを改善。
                .sortedWith(
                    compareBy<Song> { if (it.trackNumber > 0) 0 else 1 }
                        .thenBy { it.trackNumber }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
        }
        Column {
            TopAppBar(
                title = { Text(selectedAlbum!!.ifBlank { "不明なアルバム" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { selectedAlbum = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
            SongList(songs = songsByAlbum, currentSong = null, onSongClick = onSongClick)
        }
    } else {
        // 最初にアルバム一覧を表示
        val albums = remember(songList) { songList.map { it.album }.distinct() }
        FolderList(items = albums, onItemClick = { selectedAlbum = it })
    }
}

@Composable
fun SongList(songs: List<Song>, currentSong: Song?, onSongClick: (Song) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) { items(songs) { song -> SongListItem(song, (song == currentSong), onSongClick) } }
}

@Composable
fun SongListItem(song: Song, isCurrentlyPlaying: Boolean, onSongClick: (Song) -> Unit) {
    // 存在しないファイルはグレーアウト表示
    val textColor = if (song.exists) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    val subTextColor = if (song.exists) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick(song) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            song.title,
            fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${song.artist} - ${song.album}" + if (song.exists) " (再生: ${song.playCount}回)" else " [ファイルなし]",
            style = MaterialTheme.typography.bodySmall,
            color = subTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider()
}

// --- Logic Functions ---

/**
 * Content URI からファイル名を取得する。取得できない場合はパスから推測する。
 */
private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (columnIndex != -1) {
                    result = cursor.getString(columnIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        result?.lastIndexOf('/')?.let {
            if (it != -1) {
                result = result?.substring(it + 1)
            }
        }
    }
    return result
}

private fun getTabOrder(context: Context): List<TabType> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val orderString = prefs.getString(KEY_TAB_ORDER, null)
    return if (orderString != null) {
        orderString.split(',').mapNotNull { try { TabType.valueOf(it) } catch (e: IllegalArgumentException) { null } }
    } else {
        listOf(TabType.SONGS, TabType.PLAYLISTS, TabType.ARTISTS, TabType.ALBUMS)
    }
}
private fun saveTabOrder(context: Context, order: List<TabType>) {
    val orderString = order.joinToString(",") { it.name }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TAB_ORDER, orderString).apply()
}

// プレイリスト用ベースパスの保存・取得関数
private fun savePlaylistBasePath(context: Context, basePath: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_PLAYLIST_BASE_PATH, basePath).apply()
}
private fun getPlaylistBasePath(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PLAYLIST_BASE_PATH, "") ?: ""
}

private fun saveUriList(context: Context, key: String, uris: List<Uri>) {
    val uriStrings = uris.map { it.toString() }.toSet()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putStringSet(key, uriStrings).apply()
}
private fun getUriList(context: Context, key: String): List<Uri> {
    val uriStrings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(key, emptySet()) ?: emptySet()
    return uriStrings.map { Uri.parse(it) }
}
private suspend fun saveLibraryToFile(context: Context, songs: List<Song>) = withContext(Dispatchers.IO) {
    val jsonArray = JSONArray()
    songs.forEach { song ->
        val jsonObject = JSONObject().apply {
            put("uri", song.uri.toString())
            put("displayName", song.displayName)
            put("title", song.title)
            put("artist", song.artist)
            put("album", song.album)
            put("playCount", song.playCount)
            put("trackNumber", song.trackNumber)
            put("sourceFolderUri", song.sourceFolderUri.toString())
        }
        jsonArray.put(jsonObject)
    }
    File(context.filesDir, LIBRARY_CACHE_FILE).writeText(jsonArray.toString())
}
private suspend fun loadLibraryFromFile(context: Context): List<Song>? = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, LIBRARY_CACHE_FILE)
    if (!file.exists()) return@withContext null
    try {
        val jsonString = file.readText()
        val jsonArray = JSONArray(jsonString)
        val songs = mutableListOf<Song>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            songs.add(Song(
                uri = Uri.parse(jsonObject.getString("uri")),
                displayName = jsonObject.getString("displayName"),
                title = jsonObject.getString("title"),
                artist = jsonObject.getString("artist"),
                album = jsonObject.getString("album"),
                playCount = jsonObject.getInt("playCount"),
                trackNumber = jsonObject.optInt("trackNumber", 0),
                sourceFolderUri = Uri.parse(jsonObject.optString("sourceFolderUri"))
            ))
        }
        return@withContext songs
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
private suspend fun savePlaylistsToFile(context: Context, playlists: List<Playlist>) = withContext(Dispatchers.IO) {
    val jsonArray = JSONArray()
    playlists.forEach { playlist ->
        val jsonObject = JSONObject().apply {
            put("name", playlist.name)
            put("songUris", JSONArray(playlist.songs.map { it.uri.toString() }))
        }
        jsonArray.put(jsonObject)
    }
    File(context.filesDir, PLAYLIST_CACHE_FILE).writeText(jsonArray.toString())
}
private suspend fun loadPlaylistsFromFile(context: Context, allSongs: List<Song>): List<Playlist>? = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, PLAYLIST_CACHE_FILE)
    if (!file.exists()) return@withContext null
    try {
        val jsonString = file.readText()
        val jsonArray = JSONArray(jsonString)
        val playlists = mutableListOf<Playlist>()
        val songMap = allSongs.associateBy { it.uri.toString() }
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val name = jsonObject.getString("name")
            val songUris = jsonObject.getJSONArray("songUris")
            val songsInPlaylist = mutableListOf<Song>()
            for (j in 0 until songUris.length()) {
                songMap[songUris.getString(j)]?.let { songsInPlaylist.add(it) }
            }
            playlists.add(Playlist(name, songsInPlaylist))
        }
        return@withContext playlists
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
private suspend fun countFilesInDirectory(context: Context, directoryUri: Uri): Int = withContext(Dispatchers.IO) {
    var count = 0
    val contentResolver = context.contentResolver
    val documentsTree = DocumentsContract.buildDocumentUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
    fun traverse(currentUri: Uri) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(currentUri))
            contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        traverse(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId))
                    } else { count++ }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    traverse(documentsTree); return@withContext count
}
private suspend fun getAudioFilesFromDirectory(context: Context, directoryUri: Uri, totalFiles: Int, onProgress: (Float) -> Unit): List<Song> = withContext(Dispatchers.IO) {
    val songList = mutableListOf<Song>()
    val contentResolver = context.contentResolver
    val retriever = MediaMetadataRetriever()
    val documentsTree = DocumentsContract.buildDocumentUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
    var processedCount = 0
    fun traverseDirectory(currentUri: Uri) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(currentUri))
            contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                    if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                        processedCount++
                        onProgress(processedCount.toFloat().coerceAtMost(totalFiles.toFloat()) / totalFiles.toFloat())
                        try {
                            retriever.setDataSource(context, docUri)
                            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: displayName
                            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                            val trackString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                            val trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                            songList.add(Song(docUri, displayName, title, artist, album, 0, trackNumber, directoryUri))
                        } catch (e: Exception) { /* Not an audio file */ }
                    } else { traverseDirectory(docUri) }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    traverseDirectory(documentsTree); retriever.release(); return@withContext songList
}

/**
 * M3Uプレイリストを安全に解析する。
 * Windowsパスから相対パスを抽出し、アーティスト/アルバム/ファイル名でマッチングする。
 * クラッシュを防ぎ、失敗した場合はnullを返す。
 * @param basePath WindowsパスのベースパスM3Uファイル内のパスから除去する部分
 */
private suspend fun parseM3uPlaylist(context: Context, playlistUri: Uri, allSongs: List<Song>, basePath: String): Playlist? = withContext(Dispatchers.IO) {
    return@withContext runCatching {
        val playlistSongs = mutableListOf<Song>()
        val playlistName = getFileName(context, playlistUri)?.replace(".m3u8", "")?.replace(".m3u", "") ?: "プレイリスト"
        
        // ファイル名だけでなく、アーティスト/アルバム/ファイル名の組み合わせでマッチング
        // ライブラリ内の曲を相対パス風のキーでインデックス化
        val songsByRelativePath = mutableMapOf<String, Song>()
        val songsByDisplayName = mutableMapOf<String, Song>()
        
        allSongs.forEach { song ->
            // ファイル名でのマッチング用
            songsByDisplayName[song.displayName] = song
            // アーティスト/アルバム/ファイル名の組み合わせでのマッチング用
            val key = "${song.artist}/${song.album}/${song.displayName}".lowercase()
            songsByRelativePath[key] = song
        }

        context.contentResolver.openInputStream(playlistUri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        val trimmedLine = line.trim()
                        var matchedSong: Song? = null
                        
                        // 方法1: ベースパスを除去して相対パスから探す
                        if (basePath.isNotBlank() && trimmedLine.startsWith(basePath, ignoreCase = true)) {
                            // Windowsパスから相対パスを抽出
                            val relativePath = trimmedLine.substring(basePath.length)
                                .replace("\\", "/")  // バックスラッシュをスラッシュに変換
                            
                            // パスからアーティスト/アルバム/ファイル名を抽出
                            val pathParts = relativePath.split("/").filter { it.isNotBlank() }
                            if (pathParts.size >= 3) {
                                // 例: flac/Mrs. GREEN APPLE/10/breakfast.flac
                                // 最後の3つ（アーティスト/アルバム/ファイル名）を使用
                                val artist = pathParts[pathParts.size - 3]
                                val album = pathParts[pathParts.size - 2]
                                val fileName = pathParts.last()
                                val key = "$artist/$album/$fileName".lowercase()
                                matchedSong = songsByRelativePath[key]
                            }
                        }
                        
                        // 方法2: フォールバック - ファイル名のみでマッチング
                        if (matchedSong == null) {
                            val fileName = trimmedLine.substringAfterLast('/').substringAfterLast('\\').trim()
                            matchedSong = songsByDisplayName[fileName]
                        }
                        
                        matchedSong?.let { playlistSongs.add(it) }
                    }
                }
            }
        }
        Playlist(playlistName, playlistSongs)
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MusicPlayerTheme {
        MusicPlayerScreen(songList = emptyList(), playlists = emptyList(), onSongListChange = {}, onPlaylistsChange = {}, onNavigateToSettings = {})
    }
}

