// ----------------------------------------------------------------------------------
// MainActivity.kt
// UI/UXを大幅に改善し、設定画面や高度なライブラリ管理機能を追加した最終完成版
// Gemini Model: Final Build 2025-09-09 v5 (修正版)
// ----------------------------------------------------------------------------------
package com.example.musicplayer

import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.os.Build
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import org.json.JSONArray
import java.io.File
import java.util.Collections

// --- アプリ情報 ---
// v2.1.1: 設定UIリファクタ（バージョン+アップデート統合）、並列スキャン修正
private const val APP_VERSION = "v2.1.1"
private const val GEMINI_MODEL_VERSION = "Final Build 2026-01-13 v31"

// --- データ構造の定義 ---
enum class SortType { DEFAULT, TITLE, ARTIST, ALBUM, PLAY_COUNT }
enum class SortOrder { ASC, DESC }
enum class TabType { SONGS, PLAYLISTS, ARTISTS, ALBUMS }
enum class RepeatMode { OFF, ALL, ONE }

// --- 定数 ---
private const val PREFS_NAME = "MusicPlayerPrefs"
private const val KEY_SOURCE_FOLDER_URIS = "sourceFolderUris"
private const val KEY_TAB_ORDER = "tabOrder"
private const val KEY_PLAYLIST_BASE_PATH = "playlistBasePath"
private const val KEY_USE_SYSTEM_MEDIA_CONTROLLER = "useSystemMediaController"
private const val LIBRARY_CACHE_FILE = "library.json"
private const val PLAYLIST_CACHE_FILE = "playlists.json"

// Actions
private const val ACTION_NEXT = "com.example.musicplayer.ACTION_NEXT"
private const val ACTION_PREVIOUS = "com.example.musicplayer.ACTION_PREVIOUS"
private const val ACTION_PLAY_PAUSE = "com.example.musicplayer.ACTION_PLAY_PAUSE"
private const val ACTION_SHUFFLE = "com.example.musicplayer.ACTION_SHUFFLE"
private const val ACTION_REPEAT = "com.example.musicplayer.ACTION_REPEAT"
private const val ACTION_STOP = "com.example.musicplayer.ACTION_STOP"
private const val ACTION_SEEK = "com.example.musicplayer.ACTION_SEEK"
private const val EXTRA_SEEK_POS = "com.example.musicplayer.EXTRA_SEEK_POS"
private const val NOTIFICATION_CHANNEL_ID = "music_notification_channel"
private const val NOTIFICATION_ID = 1

class MainActivity : ComponentActivity() {

    // MediaSession
    private lateinit var mediaSession: android.support.v4.media.session.MediaSessionCompat
    private var useSystemMediaController by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mediaSession = android.support.v4.media.session.MediaSessionCompat(this, "MusicPlayerSession").apply {
            setCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay() { 
                    sendBroadcast(Intent(ACTION_PLAY_PAUSE).setPackage(packageName)) 
                }
                override fun onPause() { 
                    sendBroadcast(Intent(ACTION_PLAY_PAUSE).setPackage(packageName)) 
                }
                override fun onSkipToNext() { 
                    sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName)) 
                }
                override fun onSkipToPrevious() { 
                    sendBroadcast(Intent(ACTION_PREVIOUS).setPackage(packageName)) 
                }
                override fun onStop() { 
                    sendBroadcast(Intent(ACTION_STOP).setPackage(packageName)) 
                }
                override fun onSeekTo(pos: Long) {
                    sendBroadcast(Intent(ACTION_SEEK).setPackage(packageName).putExtra(EXTRA_SEEK_POS, pos))
                }
            })
            isActive = true
        }

        createNotificationChannel()
        requestNotificationPermission()
        setContent {
            MusicPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MusicApp(
                        useSystemMediaController = useSystemMediaController,
                        onUseSystemMediaControllerChange = { 
                            useSystemMediaController = it 
                            saveUseSystemMediaController(this, it)
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.action?.let { action ->
            val broadcastIntent = Intent(action)
            broadcastIntent.setPackage(packageName)
            if (action == ACTION_SEEK) {
                broadcastIntent.putExtras(intent)
            }
            sendBroadcast(broadcastIntent)
            if (action == ACTION_STOP) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "楽曲再生"
            val descriptionText = "再生中の楽曲情報を通知に表示します"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    internal fun updateNotification(
        song: Song,
        isPlaying: Boolean,
        isShuffleEnabled: Boolean,
        repeatMode: RepeatMode
    ) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        fun createActionIntent(action: String): PendingIntent {
            val intent = Intent(this, MusicNotificationReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            
        if (useSystemMediaController) {
             val style = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(1, 2, 3)
             notificationBuilder.setStyle(style)
             
             val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
             
             try {
                val ret = MediaMetadataRetriever()
                ret.setDataSource(this, song.uri)
                val artBytes = ret.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    notificationBuilder.setLargeIcon(bitmap)
                }
                ret.release()
             } catch (e: Exception) { }
             
             mediaSession.setMetadata(metadataBuilder.build())
             
             val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setActions(
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
                )
             stateBuilder.setState(
                 if (isPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                 android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                 1.0f
             )
             mediaSession.setPlaybackState(stateBuilder.build())
        } else {
             notificationBuilder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1, 2, 3) 
            )
        }

        notificationBuilder.addAction(
            if (isShuffleEnabled) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off,
            if (isShuffleEnabled) "シャッフル ON" else "シャッフル OFF",
            createActionIntent(ACTION_SHUFFLE)
        )
        notificationBuilder.addAction(
            R.drawable.ic_skip_previous,
            "前の曲",
            createActionIntent(ACTION_PREVIOUS)
        )
        notificationBuilder.addAction(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) "一時停止" else "再生",
            createActionIntent(ACTION_PLAY_PAUSE)
        )
        notificationBuilder.addAction(
            R.drawable.ic_skip_next,
            "次の曲",
            createActionIntent(ACTION_NEXT)
        )
        notificationBuilder.addAction(
            R.drawable.ic_close,
            "×",
            createActionIntent(ACTION_STOP)
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
}

sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
}

@Composable
fun MusicApp(useSystemMediaController: Boolean, onUseSystemMediaControllerChange: (Boolean) -> Unit) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        MusicPlayerScreen(
            songList = songList,
            playlists = playlists,
            onSongListChange = { songList = it },
            onPlaylistsChange = { playlists = it },
            onNavigateToSettings = { currentScreen = Screen.Settings }
        )

        if (currentScreen is Screen.Settings) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SettingsScreen(
                    onNavigateBack = {
                        libraryUpdateKey++
                        currentScreen = Screen.Main
                    },
                    useSystemMediaController = useSystemMediaController,
                    onUseSystemMediaControllerChange = onUseSystemMediaControllerChange
                )
            }
            BackHandler {
                libraryUpdateKey++
                currentScreen = Screen.Main
            }
        }
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
    
    // スキャン進捗状態 (v2.0.9)
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanCount by remember { mutableStateOf(0) }
    var scanTotal by remember { mutableStateOf(0) } // 推定合計
    
    // 再生状態
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // 再生キュー管理 (v2.0.9)
    // playingQueue: 現在再生用（シャッフルまたは手動並び替え後）
    // originalQueue: シャッフルOFF時の復帰用（元のコンテキスト順序）
    var playingQueue by remember { mutableStateOf<List<Song>>(emptyList()) }
    var originalQueue by remember { mutableStateOf<List<Song>>(emptyList()) }
    
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
    
    // プログレスバー更新用（滑らかな動きのため200ms間隔）
    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            duration = mediaPlayer?.duration ?: 0
            kotlinx.coroutines.delay(200) // 200ms間隔で滑らかに更新
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
                    // 曲終了時の処理
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            seekTo(0)
                            start()
                        }
                        RepeatMode.ALL -> {
                            // キューの最後なら最初へ
                            val nextIdx = if (currentIndex + 1 >= playingQueue.size) 0 else currentIndex + 1
                            playSongAtIndex(nextIdx, playingQueue)
                        }
                        RepeatMode.OFF -> {
                            if (currentIndex + 1 < playingQueue.size) {
                                playSongAtIndex(currentIndex + 1, playingQueue)
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
    
    // シンプルな再生関数（タップ時用）- コンテキスト（リスト）を受け取るように変更
    val playSong: (Song, List<Song>) -> Unit = { song, contextList ->
        // 1. 元のキューを保存
        originalQueue = contextList
        
        // 2. シャッフル有効かどうかで初期キューを決定
        val queue = if (isShuffleEnabled) {
            // 現在の曲 + 残りの曲をシャッフル
            val remainder = contextList.filter { it.uri != song.uri }.shuffled()
            listOf(song) + remainder
        } else {
            contextList
        }
        
        // 3. 再生開始
        val index = queue.indexOfFirst { it.uri == song.uri }.takeIf { it != -1 } ?: 0
        playSongAtIndex(index, queue)
    }

    // シャッフル切り替え時のロジック (LaunchedEffectで監視、またはToggleActionで実行)
    // ユーザー要望: ボタンを押すたびに「現在の曲の次以降」をシャッフル
    val toggleShuffle: () -> Unit = {
        val newShuffleState = !isShuffleEnabled
        isShuffleEnabled = newShuffleState
        
        if (currentSong != null && originalQueue.isNotEmpty()) {
            if (newShuffleState) {
                // OFF -> ON: 現在の曲の次以降をシャッフル
                val remainder = originalQueue.filter { it.uri != currentSong!!.uri }.shuffled()
                val newQueue = listOf(currentSong!!) + remainder
                playingQueue = newQueue
                currentIndex = 0 // 先頭は現在の曲
            } else {
                // ON -> OFF: 元の順序に戻す
                playingQueue = originalQueue
                // 現在の曲の位置を探し直す
                val idx = originalQueue.indexOfFirst { it.uri == currentSong!!.uri }
                if (idx != -1) {
                    currentIndex = idx
                }
            }
        }
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

    // --- 通知連携 ---
    // 1. 再生状態の変更を通知に反映
    LaunchedEffect(currentSong, isPlaying, isShuffleEnabled, repeatMode) {
        if (currentSong != null) {
            (context as? MainActivity)?.updateNotification(currentSong!!, isPlaying, isShuffleEnabled, repeatMode)
        }
    }

    // 関数参照をrememberUpdatedStateで保持（Recomposition時の参照無効化を防止）
    val currentPlayPrevious by rememberUpdatedState(playPrevious)
    val currentPlayNext by rememberUpdatedState(playNext)
    val currentMediaPlayer by rememberUpdatedState(mediaPlayer)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    // 2. 通知からのアクション受信とクリーンアップ
    DisposableEffect(Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var lastActionTime = 0L
        val debounceMs = 300L // 300ms以内の連続アクションを無視
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val now = System.currentTimeMillis()
                if (now - lastActionTime < debounceMs) {
                    return // デバウンス: 短時間の連続アクションを無視
                }
                lastActionTime = now
                
                // メインスレッドで状態更新を実行
                mainHandler.post {
                    when (intent.action) {
                        ACTION_PREVIOUS -> currentPlayPrevious()
                        ACTION_NEXT -> currentPlayNext()
                        ACTION_PLAY_PAUSE -> {
                            if (currentIsPlaying) {
                                currentMediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                currentMediaPlayer?.start()
                                isPlaying = true
                            }
                        }
                        ACTION_SHUFFLE -> { toggleShuffle() }
                        ACTION_REPEAT -> {
                            repeatMode = when (repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                        }
                        ACTION_STOP -> {
                            // アプリを完全に終了
                            currentMediaPlayer?.stop()
                            currentMediaPlayer?.release()
                            mediaPlayer = null
                            // 通知をキャンセル
                            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancel(NOTIFICATION_ID)
                            // Activityを終了
                            (ctx as? android.app.Activity)?.finishAffinity()
                        }
                        ACTION_SEEK -> {
                            val pos = intent.getLongExtra(EXTRA_SEEK_POS, 0L)
                            currentMediaPlayer?.seekTo(pos.toInt())
                            currentPosition = pos.toInt()
                        }
                        MusicScanService.ACTION_SCAN_PROGRESS -> {
                            val p = intent.getFloatExtra(MusicScanService.EXTRA_PROGRESS, 0f)
                            val c = intent.getIntExtra(MusicScanService.EXTRA_CURRENT_COUNT, 0)
                            scanProgress = p
                            scanCount = c
                            // Totalは推定値だが、Progressから逆算またはServiceから受け取る必要があるが...
                            // 今回は簡易的にService側でProgressしているので、そのまま表示
                            isScanning = (p < 1.0f)
                        }
                        MusicScanService.ACTION_SCAN_COMPLETE -> {
                            isScanning = false
                            scanProgress = 1.0f
                            // ScanResultHolderから結果を取得
                            val newSongs = ScanResultHolder.scannedSongs ?: emptyList()
                            if (newSongs.isNotEmpty()) {
                                // 既存リストとマージ（重複排除）
                                val merged = (songList + newSongs).distinctBy { it.uri }
                                onSongListChange(merged.sortedBy { it.title }) // タイトル順
                                coroutineScope.launch { saveLibraryToFile(context, merged) }
                                Toast.makeText(context, "スキャン完了: ${newSongs.size}曲追加", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "スキャン完了: 新規ファイルなし", Toast.LENGTH_SHORT).show()
                            }
                            ScanResultHolder.scannedSongs = null // Clear
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_SHUFFLE)
            addAction(ACTION_REPEAT)
            addAction(ACTION_STOP)
            addAction(MusicScanService.ACTION_SCAN_PROGRESS) // スキャン進捗受信
            addAction(MusicScanService.ACTION_SCAN_COMPLETE) // スキャン完了受信
        }
        // RECEIVER_NOT_EXPORTEDで同一パッケージからのブロードキャストのみ受信
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // 既に登録解除済みの場合は無視
            }
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // スキャン制御関数 (Service起動)
    fun startScanService(uris: List<Uri>?) {
        val targetUris = uris ?: getUriList(context, KEY_SOURCE_FOLDER_URIS)
        if (targetUris.isEmpty()) {
            Toast.makeText(context, "フォルダが選択されていません", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, MusicScanService::class.java).apply {
            action = MusicScanService.ACTION_START_SCAN
            putParcelableArrayListExtra(MusicScanService.EXTRA_SCAN_URIS, ArrayList(targetUris))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        isScanning = true
        scanProgress = 0f
        scanCount = 0
        scanTotal = 0
    }
    
    // メイン画面とフルスクリーンプレイヤーを重ね合わせ（閉じるアニメーションを正しく動作させるため）
    Box(modifier = Modifier.fillMaxSize()) {
        // メイン画面（常に表示）
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                         Column {
                             Text("Music Player")
                             if (isScanning) {
                                 val percent = (scanProgress * 100).toInt()
                                 Text("読み込み中: ${percent}% (${scanCount}曲)", style = MaterialTheme.typography.bodySmall)
                                 LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth().height(2.dp))
                             }
                         }
                    },
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
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        onPrevious = playPrevious,
                        onPlayPause = {
                            if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.start()
                            isPlaying = !isPlaying
                        },
                        onNext = playNext,
                        onShuffleToggle = { toggleShuffle() },
                        onRepeatToggle = {
                            repeatMode = when (repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                        },
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
                    TabType.PLAYLISTS -> PlaylistTab(playlists, songList, currentSong, onPlaylistChanged = onPlaylistsChange, onSongClick = playSong)
                    TabType.ARTISTS -> ArtistFolderTab(songList, onSongClick = playSong)
                    TabType.ALBUMS -> AlbumFolderTab(songList, onSongClick = playSong)
                    null -> {}
                }
            }
        }
        
        // フルスクリーンプレイヤー（上に重ねて表示、アニメーション付き）
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
                    playingQueue = playingQueue,
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
                        repeatMode = when (repeatMode) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        }
                    },
                    onShuffleToggle = { toggleShuffle() },
                    onReorder = { from, to ->
                        if (from in playingQueue.indices && to in playingQueue.indices) {
                            val newList = playingQueue.toMutableList()
                            Collections.swap(newList, from, to)
                            playingQueue = newList
                            if (currentIndex == from) currentIndex = to
                            else if (currentIndex == to) currentIndex = from
                        }
                    },
                    onDismiss = { showFullPlayer = false }
                )
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
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
            
            // シャッフル（ON時に背景表示）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isShuffleEnabled) 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        else 
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onShuffleToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(20.dp),
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // リピート（ON時に背景表示）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (repeatMode != RepeatMode.OFF) 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        else 
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onRepeatToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        modifier = Modifier.size(20.dp),
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 前の曲
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // 再生/一時停止
            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // 次の曲
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(28.dp)
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
    playingQueue: List<Song> = emptyList(),
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val albumArt = rememberAlbumArt(context, currentSong.uri)
    // キュー表示用の状態
    var showQueue by remember { mutableStateOf(false) }
    // ドラッグ中のアイテムインデックス
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    
    // キュー表示ダイアログ
    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            title = { Text("再生キュー (${playingQueue.size}曲)") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(playingQueue.size, key = { playingQueue[it].uri.toString() }) { index ->
                        val song = playingQueue[index]
                        val isCurrent = song.uri == currentSong.uri
                        val isDragging = draggedIndex == index
                        
                        // ドラッグ中のオフセット計算（シンプル化）
                        val itemHeight = 56
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { 
                                    val yOffset = if (isDragging) {
                                        dragOffset.toInt()
                                    } else if (draggedIndex != null) {
                                        val draggedPos = draggedIndex!!
                                        val moveBy = (dragOffset / itemHeight).toInt()
                                        val targetPos = draggedPos + moveBy
                                        when {
                                            draggedPos > index && targetPos <= index -> itemHeight
                                            draggedPos < index && targetPos >= index -> -itemHeight
                                            else -> 0
                                        }
                                    } else 0
                                    IntOffset(0, yOffset) 
                                }
                                .background(
                                    when {
                                        isDragging -> MaterialTheme.colorScheme.secondaryContainer
                                        isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 曲番号
                            Text(
                                "${index + 1}",
                                modifier = Modifier.width(28.dp),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // 曲情報
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
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
                            
                            // ドラッグハンドル（右側）- 長押しでドラッグ開始
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "ドラッグ",
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerInput(index) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                val moveBy = (dragOffset / itemHeight).toInt()
                                                val targetIndex = (index + moveBy).coerceIn(0, playingQueue.size - 1)
                                                if (targetIndex != index) {
                                                    onReorder(index, targetIndex)
                                                }
                                                draggedIndex = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.y
                                            }
                                        )
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < playingQueue.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueue = false }) {
                    Text("閉じる")
                }
            }
        )
    }
    
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
            // ヘッダー: 閉じるボタンのみ（中央）
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
                // シャッフル（ON時に背景表示）
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isShuffleEnabled) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onShuffleToggle) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                
                // リピート（ON時に背景表示）
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (repeatMode != RepeatMode.OFF) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onRepeatToggle) {
                        Icon(
                            if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 右下にキューボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = "Queue",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    useSystemMediaController: Boolean,
    onUseSystemMediaControllerChange: (Boolean) -> Unit
) {
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
                Text("設定", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                // MediaSession設定
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("システムメディアプレイヤーを使用", modifier = Modifier.weight(1f))
                    Switch(checked = useSystemMediaController, onCheckedChange = onUseSystemMediaControllerChange)
                }
                Text("通知パネルのデザインを変更します", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
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

                        IconButton(onClick = {
                            if (isScanning) return@IconButton
                            isScanning = true
                            scanProgress = 0f
                            coroutineScope.launch {
                                val currentLibrary = loadLibraryFromFile(context) ?: emptyList()
                                val cleanLibrary = currentLibrary.filterNot { it.sourceFolderUri == uri }
                                val count = countFilesInDirectory(context, uri)
                                val newSongs = getAudioFilesFromDirectory(context, uri, count) { progress ->
                                     launch(Dispatchers.Main) { scanProgress = progress }
                                }
                                val combined = (cleanLibrary + newSongs).distinctBy { it.uri }
                                saveLibraryToFile(context, combined)
                                isScanning = false
                                withContext(Dispatchers.Main) { Toast.makeText(context, "再スキャン完了: ${newSongs.size}曲", Toast.LENGTH_SHORT).show() }
                            }
                        }) { Icon(Icons.Filled.Refresh, "Rescan folder") }
                    }
                }

                if (isScanning) {
                    LinearProgressIndicator(progress = { scanProgress })
                    Text("読み込み中... ${(scanProgress * 100).toInt()}%")
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
                Text("例: C:\\Users\\Music\\ （末尾のバックスラッシュを含む）", style = MaterialTheme.typography.bodySmall)
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
                
                // バージョン情報 + アップデート統合
                Text("バージョン情報", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("App Version: $APP_VERSION", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<String?>(null) }
                var latestVersion by remember { mutableStateOf<String?>(null) }
                var downloadUrl by remember { mutableStateOf<String?>(null) }
                var updateReleaseNotes by remember { mutableStateOf<String?>(null) }
                var showUpdateConfirmDialog by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        isCheckingUpdate = true
                        updateInfo = null
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    checkForUpdate()
                                }
                                latestVersion = result.first
                                downloadUrl = result.second
                                updateReleaseNotes = result.third
                                
                                if (latestVersion != null) {
                                    val normalize = { v: String -> v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 } }
                                    val currentParts = normalize(APP_VERSION)
                                    val latestParts = normalize(latestVersion!!)
                                    
                                    var isNewer = false
                                    val length = maxOf(currentParts.size, latestParts.size)
                                    for (i in 0 until length) {
                                        val c = currentParts.getOrElse(i) { 0 }
                                        val l = latestParts.getOrElse(i) { 0 }
                                        if (l > c) {
                                            isNewer = true
                                            break
                                        }
                                        if (l < c) break
                                    }

                                    if (isNewer) {
                                        updateInfo = "新しいバージョン $latestVersion が利用可能です"
                                        showUpdateConfirmDialog = true
                                    } else {
                                        updateInfo = "最新バージョンです"
                                    }
                                }
                            } catch (e: Exception) {
                                updateInfo = "エラー: ${e.message}"
                            }
                            isCheckingUpdate = false
                        }
                    },
                    enabled = !isCheckingUpdate
                ) {
                    Text(if (isCheckingUpdate) "確認中..." else "更新を確認")
                }
                
                updateInfo?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                if (showUpdateConfirmDialog && latestVersion != null && downloadUrl != null) {
                    AlertDialog(
                        onDismissRequest = { showUpdateConfirmDialog = false },
                        title = { Text("アップデートあり") },
                        text = {
                            Column {
                                Text("新しいバージョン $latestVersion が見つかりました。")
                                if (!updateReleaseNotes.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("変更点:", style = MaterialTheme.typography.titleSmall)
                                    Text(updateReleaseNotes!!, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("ダウンロードして更新しますか？")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUpdateConfirmDialog = false
                                downloadAndInstallApk(context, downloadUrl!!)
                            }) { Text("更新") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateConfirmDialog = false }) { Text("キャンセル") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SongsTab(songList: List<Song>, currentSong: Song?, sortType: SortType, sortOrder: SortOrder, onSongClick: (Song, List<Song>) -> Unit, onSortTypeChange: (SortType) -> Unit, onSortOrderChange: (SortOrder) -> Unit) {
    val sortedList = remember(songList, sortType, sortOrder) {
        val comparator = when (sortType) {
            SortType.DEFAULT -> compareBy { song: Song -> song.displayName } // ファイル名順
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
                        SortType.DEFAULT -> "標準"; SortType.TITLE -> "曲名"; SortType.ARTIST -> "アーティスト"
                        SortType.ALBUM -> "アルバム"; SortType.PLAY_COUNT -> "再生回数"
                    })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("標準") }, onClick = { onSortTypeChange(SortType.DEFAULT); expanded = false })
                    DropdownMenuItem(text = { Text("曲名") }, onClick = { onSortTypeChange(SortType.TITLE); expanded = false })
                    DropdownMenuItem(text = { Text("アーティスト") }, onClick = { onSortTypeChange(SortType.ARTIST); expanded = false })
                    DropdownMenuItem(text = { Text("アルバム") }, onClick = { onSortTypeChange(SortType.ALBUM); expanded = false })
                    DropdownMenuItem(text = { Text("再生回数") }, onClick = { onSortTypeChange(SortType.PLAY_COUNT); expanded = false })
                }
            }
            Button(onClick = { onSortOrderChange(if (sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC) }) { Text(if (sortOrder == SortOrder.ASC) "昇順" else "降順") }
        }
        HorizontalDivider()
        SongList(songs = sortedList, currentSong = currentSong, onSongClick = { song -> onSongClick(song, sortedList) })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistTab(playlists: List<Playlist>, songList: List<Song>, currentSong: Song?, onPlaylistChanged: (List<Playlist>) -> Unit, onSongClick: (Song, List<Song>) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    // 戻るボタンのハンドリング
    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    // 削除確認ダイアログ用の状態
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    
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

    // 削除確認ダイアログ
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("プレイリストを削除") },
            text = { Text("「${playlistToDelete!!.name}」をリストから削除しますか？\n（元のファイルは削除されません）") },
            confirmButton = {
                TextButton(onClick = {
                    val updatedPlaylists = playlists.filter { it != playlistToDelete }
                    coroutineScope.launch {
                        savePlaylistsToFile(context, updatedPlaylists)
                        withContext(Dispatchers.Main) { 
                            onPlaylistChanged(updatedPlaylists)
                            Toast.makeText(context, "プレイリストを削除しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                    playlistToDelete = null
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
    
    // プレイリストが選択されている場合：曲リストを表示
    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = selectedPlaylist!!,
            currentSong = currentSong,
            onSongClick = onSongClick,
            onBack = { selectedPlaylist = null }
        )
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
                 // 文言削除
                LazyColumn {
                    items(playlists) { playlist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { selectedPlaylist = playlist },
                                    onLongClick = { playlistToDelete = playlist }
                                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlist: Playlist, currentSong: Song?, onSongClick: (Song, List<Song>) -> Unit, onBack: () -> Unit) {
    var sortType by remember { mutableStateOf(SortType.DEFAULT) }
    var sortOrder by remember { mutableStateOf(SortOrder.ASC) }

    val sortedSongs = remember(playlist.songs, sortType, sortOrder) {
        val comparator = when (sortType) {
            SortType.DEFAULT -> compareBy { song: Song -> song.displayName } // ファイル名順
            SortType.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.title }
            SortType.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.artist }
            SortType.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.album }
            SortType.PLAY_COUNT -> compareBy { song: Song -> song.playCount }
        }
        if (sortOrder == SortOrder.DESC) playlist.songs.sortedWith(comparator.reversed()) else playlist.songs.sortedWith(comparator)
    }

    Column {
        TopAppBar(title = { Text(playlist.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        
        // プレイリスト内ソート機能
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text(when(sortType){
                        SortType.DEFAULT -> "標準"; SortType.TITLE -> "曲名"; SortType.ARTIST -> "アーティスト"
                        SortType.ALBUM -> "アルバム"; SortType.PLAY_COUNT -> "再生回数"
                    })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("標準") }, onClick = { sortType = SortType.DEFAULT; expanded = false })
                    DropdownMenuItem(text = { Text("曲名") }, onClick = { sortType = SortType.TITLE; expanded = false })
                    DropdownMenuItem(text = { Text("アーティスト") }, onClick = { sortType = SortType.ARTIST; expanded = false })
                    DropdownMenuItem(text = { Text("アルバム") }, onClick = { sortType = SortType.ALBUM; expanded = false })
                    DropdownMenuItem(text = { Text("再生回数") }, onClick = { sortType = SortType.PLAY_COUNT; expanded = false })
                }
            }
            Button(onClick = { sortOrder = if (sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC }) { Text(if (sortOrder == SortOrder.ASC) "昇順" else "降順") }
        }

        SongList(songs = sortedSongs, currentSong = currentSong, onSongClick = { song -> onSongClick(song, sortedSongs) })
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
fun ArtistFolderTab(songList: List<Song>, onSongClick: (Song, List<Song>) -> Unit) {
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    // 戻るボタンのハンドリング
    BackHandler(enabled = selectedArtist != null) {
        if (selectedAlbum != null) {
            selectedAlbum = null
        } else {
            selectedArtist = null
        }
    }

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
                SongList(songs = songs, currentSong = null, onSongClick = { song -> onSongClick(song, songs) })
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
fun AlbumFolderTab(songList: List<Song>, onSongClick: (Song, List<Song>) -> Unit) {
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    // 戻るボタンのハンドリング
    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }

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
            SongList(songs = songsByAlbum, currentSong = null, onSongClick = { song -> onSongClick(song, songsByAlbum) })
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

// MediaSession設定の保存・取得関数
private fun saveUseSystemMediaController(context: Context, use: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_USE_SYSTEM_MEDIA_CONTROLLER, use).apply()
}
private fun getUseSystemMediaController(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_SYSTEM_MEDIA_CONTROLLER, false)
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

    // 高速化: Uriから実際のファイルパスを推測してjava.io.Fileを使用する
    // v2.0.8: SAFフォールバック時はカウントをスキップするため（0を返す）、ここではFile APIが使えるかだけを試す
    
    var useFileApi = false
    var rootFile: File? = null
    try {
        if (DocumentsContract.isTreeUri(directoryUri)) {
            val docId = DocumentsContract.getTreeDocumentId(directoryUri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val pathStr = split[1]
                val targetPath = if (type == "primary") {
                    "/storage/emulated/0/$pathStr"
                } else {
                    "/storage/$type/$pathStr"
                }
                val file = File(targetPath)
                if (file.exists() && file.isDirectory && file.canRead()) {
                    rootFile = file
                    useFileApi = true
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

    if (useFileApi && rootFile != null) {
        try {
            // 高速カウント
            count = rootFile!!.walk()
                .filter { it.isFile && (it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("flac", true) || it.extension.equals("wav", true) || it.extension.equals("aac", true) || it.extension.equals("ogg", true)) }
                .count()
            
            // 重要: 実機制限などで0件の場合はフォールバックする
            if (count > 0) {
                return@withContext count
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // v2.0.8: SAFが必要な場合、ここでの低速カウントは時間がかかるためスキップする (return 0)
    // 実際のカウントと読み込みは getAudioFilesFromDirectory でまとめて行う
    return@withContext 0 
}
private suspend fun getAudioFilesFromDirectory(context: Context, directoryUri: Uri, totalFiles: Int, onProgress: (Float) -> Unit): List<Song> = withContext(Dispatchers.IO) {
    val songList = mutableListOf<Song>()
    val retriever = MediaMetadataRetriever()
    var processedCount = 0

    // 高速化: Uriから実際のファイルパスを推測してjava.io.Fileを使用する
    var useFileApi = false
    var rootFile: File? = null

    try {
        if (DocumentsContract.isTreeUri(directoryUri)) {
            val docId = DocumentsContract.getTreeDocumentId(directoryUri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val pathStr = split[1]
                
                val targetPath = if (type == "primary") {
                    "/storage/emulated/0/$pathStr"
                } else {
                    "/storage/$type/$pathStr"
                }
                
                val file = File(targetPath)
                if (file.exists() && file.isDirectory && file.canRead()) {
                    rootFile = file
                    useFileApi = true
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (useFileApi && rootFile != null) {
        // java.io.File APIを使用した高速スキャン
        try {
            val allFiles = rootFile!!.walk()
                .filter { it.isFile && (it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("flac", true) || it.extension.equals("wav", true) || it.extension.equals("aac", true) || it.extension.equals("ogg", true)) }
                .toList()
            
            
            // val total = allFiles.size // 以前の総数を使用（walk().toList()しているので正確）
            val total = allFiles.size 
            
            if (total > 0) {
                 allFiles.forEach { file ->
                    processedCount++
                    if (processedCount % 10 == 0) { // UI更新頻度を調整
                        onProgress(processedCount.toFloat().coerceAtMost(total.toFloat()) / total.toFloat())
                    }
                    
                    try {
                        val filePath = file.absolutePath
                        val title = file.nameWithoutExtension
                        
                        var songTitle = title
                        var songArtist = "Unknown Artist"
                        var songAlbum = "Unknown Album"
                        var trackNumber = 0

                        // v2.0.8: メタデータ取得復活（ユーザー要望）
                        try {
                            retriever.setDataSource(filePath)
                            songTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
                            songArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            songAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                            val trackString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                            trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                        } catch (e: Exception) {
                            // メタデータ取得失敗時はデフォルト値
                        }

                        songList.add(Song(Uri.fromFile(file), file.name, songTitle, songArtist, songAlbum, 0, trackNumber, directoryUri))
                        
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                 useFileApi = false // 0件の場合はフォールバック
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 失敗時はDocumentFileロジックへ（下記）
            useFileApi = false
        }
    }

    if (!useFileApi) {
        // 従来のDocumentFileを使用したスキャン（低速だが確実）
        // v2.0.8: totalFilesが0（スキップされた）場合、プログレスは推定値や件数ベースにする必要があるが、
        // 簡易実装として 0.1f ずつ増やすなどの対応、あるいは「読み込み中... N曲」表示にする（呼び出し元で対応）
        
        val contentResolver = context.contentResolver
        val documentsTree = DocumentsContract.buildDocumentUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
        fun traverseDirectory(currentUri: Uri) {
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(currentUri))
                contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        
                        // 高速化のためディレクトリなら再帰
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            traverseDirectory(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId))
                        } else {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: ""
                            val ext = name.substringAfterLast('.', "").lowercase()
                            
                            // 拡張子チェック
                            // MIMEタイプが audio/* または 一般的な音楽拡張子
                            if (mimeType.startsWith("audio/") || mimeType == "application/ogg" || 
                                setOf("mp3", "m4a", "flac", "wav", "aac", "ogg").contains(ext)) {
                                processedCount++
                                if (processedCount % 5 == 0) { // 更新頻度
                                    // totalCountが0の場合は適当な進捗を送るが、呼び出し側で件数表示に切り替えるため、ここではprocessedCountを負の値として送るハックもありだが
                                    // onProgressはFloatのみ。
                                    // 呼び出し元が totalFiles == 0 の場合、progress は無視して processedCount を表示するロジックに変更するのが望ましいが
                                    // ここでは単純に 0.5 (50%) などを送って「動いている」ことだけ示す、あるいは 1.0 未満で推移させる
                                    onProgress(0.1f) // 不定状態
                                }
                                
                                // v2.0.8: メタデータ取得復活（低速モードでも情報重視）
                                var songTitle = name.substringBeforeLast('.')
                                var songArtist = "Unknown Artist"
                                var songAlbum = "Unknown Album"
                                var trackNumber = 0
                                
                                try {
                                    /* SAFでのメタデータ取得は非常に遅いが要望により実装検討
                                       ただしDocumentFileからのパス取得は困難なため、ここではファイル名ベースを維持しつつ、
                                       後で可能なら改善するが、ひとまずタイトル等はファイル名から。
                                       ★実機で遅すぎる場合はここがボトルネックになる。
                                       -> MediaMetadataRetriever は Uri を受け取れる。
                                    */
                                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                                    retriever.setDataSource(context, fileUri)
                                    songTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: songTitle
                                    songArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                                    songAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                                    val trackString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                    trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                                } catch (e: Exception) {
                                     // 無視
                                }

                                songList.add(Song(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId), name, songTitle, songArtist, songAlbum, 0, trackNumber, directoryUri))
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        traverseDirectory(documentsTree)
    }
    
    // 最後にソート（デフォルト順）
    return@withContext songList.sortedBy { it.title }
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


// GitHub APIから最新リリースを確認する関数
// GitHubリポジトリ名を設定してください
private const val GITHUB_REPO = "kazu-1234/MusicPlayer" // ユーザー名/リポジトリ名

private suspend fun checkForUpdate(): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
    return@withContext try {
        val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        // GitHub APIはUser-Agentが必須の場合があるため追加
        connection.setRequestProperty("User-Agent", "MusicPlayerAndroid")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val tagName = json.optString("tag_name", null)
            val htmlUrl = json.optString("html_url", null)
            val body = json.optString("body", "") // リリースノート本文
            
            // APKアセットのダウンロードURLを探す
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }
            
            Triple(tagName, apkUrl ?: htmlUrl, body)
        } else {
            // エラー時はnullを返すが、ログには出す
            println("GitHub API Error: $responseCode")
            Triple(null, null, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Triple(null, null, null)
    }
}

private fun downloadAndInstallApk(context: Context, url: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("Music Player Update")
        request.setDescription("Downloading update...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MusicPlayer_update.apk")
        request.setMimeType("application/vnd.android.package-archive")
        // Android 12以降のセキュリティ対策
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "ダウンロードを開始しました。完了通知をタップしてインストールしてください。", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "ダウンロードエラー: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MusicPlayerTheme {
        MusicPlayerScreen(songList = emptyList(), playlists = emptyList(), onSongListChange = {}, onPlaylistsChange = {}, onNavigateToSettings = {})
    }
}

// --- 通知からのアクションを受け取るBroadcastReceiver ---

/**
 * 通知からのアクションを受け取るBroadcastReceiver
 * アプリ内にブロードキャストを転送する
 */
class MusicNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // アプリ内にブロードキャストを送信（Composable内のreceiverがキャッチ）
        val broadcastIntent = Intent(action)
        broadcastIntent.setPackage(context.packageName)
        context.sendBroadcast(broadcastIntent)
    }
}

