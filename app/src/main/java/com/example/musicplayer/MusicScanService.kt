package com.example.musicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class MusicScanService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isScanning = false

    companion object {
        const val ACTION_START_SCAN = "com.example.musicplayer.ACTION_START_SCAN"
        const val ACTION_SCAN_PROGRESS = "com.example.musicplayer.ACTION_SCAN_PROGRESS"
        const val ACTION_SCAN_COMPLETE = "com.example.musicplayer.ACTION_SCAN_COMPLETE"
        
        const val EXTRA_SCAN_URIS = "extra_scan_uris"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_CURRENT_COUNT = "extra_current_count"
        const val EXTRA_NEW_SONGS = "extra_new_songs" // In a real app, passing large list via Intent is bad. Using file cache or DB is better.
        // For this app, we simply notify completion and let MainActivity reload from file.
        
        private const val NOTIFICATION_CHANNEL_ID = "music_scan_channel"
        private const val NOTIFICATION_ID = 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_SCAN_URIS)
                if (uris != null && !isScanning) {
                    startScan(uris)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startScan(uris: List<Uri>) {
        isScanning = true
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("音楽スキャン中")
            .setContentText("準備中...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            var totalCount = 0
            val newSongs = mutableListOf<Song>()
            
            // 1. 推定合計数をカウント (Progressバー用)
            // SAFの場合は0になる可能性があるが、その場合は不定プログレスにする
            uris.forEach { uri ->
                 totalCount += countFilesInDirectory(this@MusicScanService, uri)
            }

            var processedCount = 0
            
            uris.forEach { uri ->
                val songs = getAudioFilesFromDirectory(this@MusicScanService, uri, totalCount) { progress, currentCount ->
                     processedCount++
                     // 通知更新 (あまり頻繁にやらない)
                     if (processedCount % 10 == 0) {
                        updateNotification(processedCount, totalCount)
                     }
                     // UI更新用ブロードキャスト
                     sendProgressBroadcast(progress, processedCount)
                }
                newSongs.addAll(songs)
            }
            
            // 完了通知
            stopForeground(STOP_FOREGROUND_REMOVE)
            sendCompleteBroadcast(newSongs)
            isScanning = false
            stopSelf()
        }
    }

    private fun updateNotification(current: Int, total: Int) {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            
        if (total > 0) {
            val progress = (current.toFloat() / total.toFloat() * 100).toInt()
            builder.setContentTitle("音楽スキャン中: $progress%")
            builder.setContentText("$current / $total 曲")
            builder.setProgress(total, current, false)
        } else {
            builder.setContentTitle("音楽スキャン中")
            builder.setContentText("$current 曲読み込み完了")
            builder.setProgress(0, 0, true)
        }
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun sendProgressBroadcast(progress: Float, currentCount: Int) {
        val intent = Intent(ACTION_SCAN_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_CURRENT_COUNT, currentCount)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendCompleteBroadcast(songs: List<Song>) {
        // 大量のデータをIntentで送ると TransactionTooLargeException になるので、
        // MainActivity側でファイルから再読み込み等をしてもらう方が安全だが、
        // 既存ロジックに合わせて "songs" を渡したいが...
        // ここでは「完了した」ことだけ伝え、 MainActivity側でDB(JSON)保存等は... 
        // 既存ロジック: getAudioFilesFromDirectory がリストを返す -> MainActivityでcombined -> save.
        
        // Service内で保存までやってしまうのがベスト。
        // しかし saveLibraryToFile は MainActivity の private function.
        // Copying logic here.
        
        // Wait, MusicScanService doesn't know about "existing library" to merge with.
        // It's better if the Service returns the *new* songs found in the scanned URIs?
        // Or better yet, we can't easily pass List<Song> if it's huge.
        
        // Strategy: Save "temp_scan_results.json" and tell MainActivity to load it?
        // Or simply broadcast. If library is < 10000 songs, maybe okay?
        // Let's assume for now we use a static variable or Singleton to pass data BACK? No, leakage risk.
        // Let's modify design: The Service updates the Library File directly?
        // But it needs to know "all existing songs" to remove duplicates or merge?
        
        // For "Rescan Folder", we clear that folder's songs and add new ones.
        // For "Initial Scan", we add to existing.
        
        // Simplest approach for this immediate task:
        // Use a static "resultHolder" in the Service companion object?
        // Or just let MainActivity handle file ops?
        // Let's try sending broadcast. If it fails on large data, we'll fix.
        // But wait, the user wants "Rescan".
        // Let's make "completed" broadcast signal that the *service* has finished logic.
        // But the logic is "scan and return songs".
        
        // REVISED PLAN FOR DATA:
        // Service will receive "URIs to scan".
        // It returns "Found Songs" via a static variable (simple, in-process communication)
        // because serialization of large list is heavy.
        
        ScanResultHolder.scannedSongs = songs
        val intent = Intent(ACTION_SCAN_COMPLETE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Scan",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // --- COPIED & ADAPTED SCAN LOGIC ---
    
    private suspend fun countFilesInDirectory(context: Context, directoryUri: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        var useFileApi = false
        var rootFile: File? = null
        try {
            if (DocumentsContract.isTreeUri(directoryUri)) {
                val docId = DocumentsContract.getTreeDocumentId(directoryUri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val pathStr = split[1]
                    val targetPath = if (type == "primary") "/storage/emulated/0/$pathStr" else "/storage/$type/$pathStr"
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
                // Return 0 if we want to skip pre-count (v2.0.8 optimization logic)
                // But for Progress UI, we prefer having a count if likely fast.
                // Keeping original logic: if > 0 return it.
                 count = rootFile!!.walk()
                    .filter { it.isFile && (it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("flac", true) || it.extension.equals("wav", true) || it.extension.equals("aac", true) || it.extension.equals("ogg", true)) }
                    .count()
                 if (count > 0) return@withContext count
            } catch (e: Exception) { e.printStackTrace() }
        }
        return@withContext 0 // Fallback or SAF skip
    }

    private suspend fun getAudioFilesFromDirectory(context: Context, directoryUri: Uri, totalFiles: Int, onProgress: (Float, Int) -> Unit): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val retriever = MediaMetadataRetriever()
        var processedCount = 0
        var useFileApi = false
        var rootFile: File? = null

        try {
            if (DocumentsContract.isTreeUri(directoryUri)) {
                val docId = DocumentsContract.getTreeDocumentId(directoryUri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val pathStr = split[1]
                    val targetPath = if (type == "primary") "/storage/emulated/0/$pathStr" else "/storage/$type/$pathStr"
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
                val allFiles = rootFile!!.walk()
                    .filter { it.isFile && (it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("flac", true) || it.extension.equals("wav", true) || it.extension.equals("aac", true) || it.extension.equals("ogg", true)) }
                    .toList()
                val total = allFiles.size 
                if (total > 0) {
                    // ファイルエクスプローラーモード: フォルダ構造からメタデータを取得（高速）
                    var processedCount = 0
                    
                    allFiles.forEach { file ->
                        processedCount++
                        onProgress(processedCount.toFloat() / total.toFloat(), processedCount)
                        
                        // ファイル名から曲タイトルを取得
                        val songTitle = file.nameWithoutExtension
                        
                        // フォルダ構造: [音楽フォルダ]/アーティスト/アルバム/曲.flac
                        val parentFile = file.parentFile  // アルバムフォルダ
                        val grandParentFile = parentFile?.parentFile  // アーティストフォルダ
                        
                        val songAlbum = parentFile?.name ?: "Unknown Album"
                        val songArtist = grandParentFile?.name ?: "Unknown Artist"
                        
                        // トラック番号: ファイル名の先頭数字から取得
                        val trackNumber = songTitle.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

                        songList.add(Song(
                            uri = Uri.fromFile(file),
                            displayName = file.name,
                            title = songTitle,
                            artist = songArtist,
                            album = songAlbum,
                            playCount = 0,
                            trackNumber = trackNumber,
                            sourceFolderUri = directoryUri
                        ))
                    }
                } else {
                     useFileApi = false 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                useFileApi = false
            }
        }

        if (!useFileApi) {
            val contentResolver = context.contentResolver
            val documentsTree = DocumentsContract.buildDocumentUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
            fun traverseDirectory(currentUri: Uri) {
                try {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(currentUri))
                    contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                traverseDirectory(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId))
                            } else {
                                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: ""
                                val ext = name.substringAfterLast('.', "").lowercase()
                                if (mimeType.startsWith("audio/") || mimeType == "application/ogg" || setOf("mp3", "m4a", "flac", "wav", "aac", "ogg").contains(ext)) {
                                    processedCount++
                                    onProgress(0f, processedCount) // SAF unknown total
                                    
                                    var songTitle = name.substringBeforeLast('.')
                                    var songArtist = "Unknown Artist"
                                    var songAlbum = "Unknown Album"
                                    var trackNumber = 0
                                    try {
                                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                                        retriever.setDataSource(context, fileUri)
                                        songTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: songTitle
                                        songArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                                        songAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                                        val trackString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                        trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                                    } catch (e: Exception) { }
                                    songList.add(Song(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId), name, songTitle, songArtist, songAlbum, 0, trackNumber, directoryUri))
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            traverseDirectory(documentsTree)
        }
        return@withContext songList.sortedBy { it.title }
    }
}

// Global holder for result to avoid Parcelable overhead
object ScanResultHolder {
    var scannedSongs: List<Song>? = null
}
