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

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer:ScanServiceWakeLock")
    }

    private fun startScan(uris: List<Uri>) {
        isScanning = true
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes timeout*/)
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("音楽スキャン中")
            .setContentText("準備中...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
            
        // Use type-safe startForeground if targeting SDK 29+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                var totalCount = 0
                val newSongs = mutableListOf<Song>()
                
                uris.forEach { uri ->
                     totalCount += countFilesInDirectory(this@MusicScanService, uri)
                }
    
                var processedCount = 0
                
                uris.forEach { uri ->
                    val songs = getAudioFilesFromDirectory(this@MusicScanService, uri, totalCount) { progress, currentCount ->
                         processedCount++
                         if (processedCount % 10 == 0) {
                            updateNotification(processedCount, totalCount)
                         }
                         sendProgressBroadcast(progress, processedCount)
                    }
                    newSongs.addAll(songs)
                }
                
                sendCompleteBroadcast(newSongs)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isScanning = false
                if (wakeLock?.isHeld == true) wakeLock?.release()
                stopSelf()
            }
        }
    }

    // ... (Encoding Fix Logic) ...

    // 文字化け検出関数
    fun isGarbled(s: String?): Boolean {
        if (s == null) return false
        if (s.contains('\uFFFD')) return true
        if (s.any { 
            val c = it.code
            (c < 32 && c != 9 && c != 10 && c != 13) || 
            (c == 0x7F) || 
            (c in 0x80..0x9F) 
        }) return true
        val japaneseChars = s.any { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF }
        if (japaneseChars) return false
        val mojibakeChars = "‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿ÃÅÆÇÈÉÊËÌÍÎÏ"
        val mojibakeCount = s.count { it in mojibakeChars }
        return s.length > 0 && mojibakeCount.toFloat() / s.length.toFloat() > 0.3f
    }

    // 文字化け修復関数 (Latin-1 to Shift_JIS)
    fun fixEncoding(original: String?): String? {
        if (original == null) return null
        try {
            val bytes = original.toByteArray(Charsets.ISO_8859_1)
            val sjis = String(bytes, java.nio.charset.Charset.forName("Shift_JIS"))
            val originalJpCount = original.count { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF }
            val sjisJpCount = sjis.count { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF }
            if (sjisJpCount > originalJpCount) return sjis
        } catch (e: Exception) {}
        return original
    }

    // ... (Updated getAudioFilesFromDirectory logic to use fixEncoding) ...
    // Note: Since we are replacing a large block, I will replace the relevant part in getAudioFilesFromDirectory


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

                                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                                    val localRetriever = MediaMetadataRetriever()
                                    try {
                                        localRetriever.setDataSource(context, fileUri)
                                        val extractedTitle = localRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                        val extractedArtist = localRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                        val extractedAlbum = localRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                        
                                        val fixedTitle = fixEncoding(extractedTitle)
                                        val fixedArtist = fixEncoding(extractedArtist)
                                        val fixedAlbum = fixEncoding(extractedAlbum)
                                        
                                        songTitle = if (isGarbled(fixedTitle)) songTitle else (fixedTitle ?: songTitle)
                                        songArtist = if (isGarbled(fixedArtist)) "Unknown Artist" else (fixedArtist ?: "Unknown Artist")
                                        songAlbum = if (isGarbled(fixedAlbum)) "Unknown Album" else (fixedAlbum ?: "Unknown Album")
                                        
                                        val trackString = localRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                        trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                                    } finally {
                                        try { localRetriever.release() } catch (ignored: Exception) {}
                                    }
                                } catch (e: Exception) {
                                     // メタデータ取得失敗時はデフォルト値を使用
                                }

                                songList.add(Song(DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId), name, songTitle, songArtist, songAlbum, 0, trackNumber, directoryUri))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    val rootName = if (DocumentsContract.isTreeUri(directoryUri)) {
         DocumentsContract.getTreeDocumentId(directoryUri).split(":").lastOrNull() ?: "Unknown Album"
    } else {
        File(directoryUri.path ?: "").name
    }
    
    if (useFileApi && rootFile != null) {
          // File API logic (Parallel)
          try {
                val allFiles = rootFile!!.walk()
                    .filter { it.isFile && (it.extension.equals("mp3", true) || it.extension.equals("m4a", true) || it.extension.equals("flac", true) || it.extension.equals("wav", true) || it.extension.equals("aac", true) || it.extension.equals("ogg", true)) }
                    .toList()
                val total = allFiles.size 
                if (total > 0) {
                    val semaphore = kotlinx.coroutines.sync.Semaphore(50)
                    var processedLocal = java.util.concurrent.atomic.AtomicInteger(0)
                    
                    val results = coroutineScope {
                        allFiles.map { file ->
                            async {
                                semaphore.acquire()
                                try {
                                    val currentCount = processedLocal.incrementAndGet()
                                    val progress = currentCount.toFloat() / total.toFloat()
                                    onProgress(progress, currentCount)
                                    
                                    val retrieverLocal = MediaMetadataRetriever()
                                    val filePath = file.absolutePath
                                    val title = file.nameWithoutExtension
                                    var songTitle = title
                                    var songArtist = "Unknown Artist"
                                    var songAlbum = "Unknown Album"
                                    var trackNumber = 0

                                    // Local isGarbled removed (using class member)

                                    try {
                                        retrieverLocal.setDataSource(filePath)
                                        val extractedTitle = retrieverLocal.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                        val extractedArtist = retrieverLocal.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                        val extractedAlbum = retrieverLocal.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                        
                                        val fixedTitle = fixEncoding(extractedTitle)
                                        val fixedArtist = fixEncoding(extractedArtist)
                                        val fixedAlbum = fixEncoding(extractedAlbum)
                                        
                                        songTitle = if (isGarbled(fixedTitle)) title else (fixedTitle ?: title)
                                        songArtist = if (isGarbled(fixedArtist)) "Unknown Artist" else (fixedArtist ?: "Unknown Artist")
                                        songAlbum = if (isGarbled(fixedAlbum)) "Unknown Album" else (fixedAlbum ?: "Unknown Album")
                                        
                                        val trackString = retrieverLocal.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                        trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                                        retrieverLocal.release()
                                    } catch (e: Exception) { 
                                        try { retrieverLocal.release() } catch (ignored: Exception) {}
                                    }
                                    
                                    Song(Uri.fromFile(file), file.name, songTitle, songArtist, songAlbum, 0, trackNumber, directoryUri)
                                } catch (e: Exception) { 
                                    e.printStackTrace()
                                    null 
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll()
                    }
                    songList.addAll(results.filterNotNull())
                } else {
                     useFileApi = false 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                useFileApi = false
            }
    }
    
    // SAF fallback logic if File API failed or not applicable
    if (!useFileApi) {
         val contentResolver = context.contentResolver
         val documentsTree = DocumentsContract.buildDocumentUriUsingTree(directoryUri, DocumentsContract.getTreeDocumentId(directoryUri))
         // Recursive SAF traversal (Simplified for brevity regarding replacement, ensure complete logic is present)
         suspend fun traverseDirectory(currentUri: Uri) {
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
                                onProgress(0f, processedCount)
                                
                                var songTitle = name.substringBeforeLast('.')
                                var songArtist = "Unknown Artist"
                                var songAlbum = "Unknown Album"
                                var trackNumber = 0
                                try {
                                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                                    val localRetrieverSAFE = MediaMetadataRetriever()
                                    try {
                                        localRetrieverSAFE.setDataSource(context, fileUri)
                                        val extractedTitle = localRetrieverSAFE.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                        val extractedArtist = localRetrieverSAFE.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                        val extractedAlbum = localRetrieverSAFE.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                        
                                        val fixedTitle = fixEncoding(extractedTitle)
                                        val fixedArtist = fixEncoding(extractedArtist)
                                        val fixedAlbum = fixEncoding(extractedAlbum)
                                        
                                        songTitle = if (isGarbled(fixedTitle)) songTitle else (fixedTitle ?: songTitle)
                                        songArtist = if (isGarbled(fixedArtist)) "Unknown Artist" else (fixedArtist ?: "Unknown Artist")
                                        songAlbum = if (isGarbled(fixedAlbum)) "Unknown Album" else (fixedAlbum ?: "Unknown Album")
                                        
                                        val trackString = localRetrieverSAFE.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                        trackNumber = trackString?.substringBefore("/")?.toIntOrNull() ?: 0
                                    } finally {
                                        try { localRetrieverSAFE.release() } catch (ignored: Exception) {}
                                    }
                                } catch (e: Exception) {}
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
