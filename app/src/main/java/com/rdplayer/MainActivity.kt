package com.rdplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LocalMedia(val id: Long, val title: String, val uri: Uri, val isVideo: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RDPlayerApp() }
    }
}

private val bg = Color(0xFF101018)
private val panel = Color(0xFF1B1B29)
private val purple = Color(0xFF9B6DFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RDPlayerApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Home") }
    var media by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var selected by remember { mutableStateOf<LocalMedia?>(null) }
    var query by remember { mutableStateOf("") }
    var ytResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Allow media access to scan songs and videos on this device.") }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun scanMedia() {
        val found = mutableListOf<LocalMedia>()
        val video = Build.VERSION.SDK_INT >= 29
        val audioCollection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val audioProjection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        context.contentResolver.query(audioCollection, audioProjection, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                found.add(LocalMedia(id, c.getString(titleCol) ?: "Audio", ContentUris.withAppendedId(audioCollection, id), false))
            }
        }
        val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE)
        context.contentResolver.query(videoCollection, videoProjection, null, null, "${MediaStore.Video.Media.TITLE} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                found.add(LocalMedia(id, c.getString(titleCol) ?: "Video", ContentUris.withAppendedId(videoCollection, id), true))
            }
        }
        media = found
        message = if (found.isEmpty()) "No local media found. Add songs or videos to your phone." else "${found.size} media files found"
    }

    val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scanMedia() else message = "Media permission was not granted."
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = purple, background = bg, surface = panel, onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = bg,
            topBar = {
                TopAppBar(
                    title = { Column { Text("RD PLAYER", fontWeight = FontWeight.Black); Text("Your media, one place", style = MaterialTheme.typography.labelSmall, color = Color.LightGray) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, titleContentColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = panel) {
                    listOf("Home", "Music", "Videos", "YouTube").forEach { name ->
                        NavigationBarItem(
                            selected = tab == name, onClick = { tab = name },
                            icon = { Icon(when(name) { "Music" -> Icons.Default.MusicNote; "Videos" -> Icons.Default.Movie; "YouTube" -> Icons.Default.Search; else -> Icons.Default.Home }, null) },
                            label = { Text(name) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = purple, indicatorColor = Color(0xFF34264F), selectedTextColor = Color.White, unselectedTextColor = Color.LightGray)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                when (tab) {
                    "YouTube" -> {
                        Text("Search YouTube", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs, movies, videos...") },
                            trailingIcon = { IconButton(onClick = {
                                loading = true
                                ytResults = emptyList()
                                // Search is wired to YouTube Data API; add your API key in app/build.gradle.kts.
                                kotlinx.coroutines.MainScope().launch {
                                    try {
                                        ytResults = searchYouTube(query)
                                        if (ytResults.isEmpty()) message = "No results. Check API key and quota."
                                    } catch (e: Exception) { message = "YouTube search error: ${e.message}" }
                                    loading = false
                                }
                            }) { Icon(Icons.Default.Search, "Search") } },
                            singleLine = true, shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        if (loading) CircularProgressIndicator()
                        ytResults.forEach { (title, id) ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selected = null; tab = "YouTubePlayer:$id" }, colors = CardDefaults.cardColors(containerColor = panel)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayCircle, null, tint = purple)
                                    Spacer(Modifier.width(10.dp))
                                    Text(title, color = Color.White)
                                }
                            }
                        }
                        Text(message, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        if (tab.startsWith("YouTubePlayer:")) {
                            val id = tab.substringAfter(":")
                            Text("YouTube Player", style = MaterialTheme.typography.headlineSmall)
                            AndroidView(factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    loadUrl("https://www.youtube.com/embed/$id?playsinline=1")
                                }
                            }, modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f))
                            Text("Playback uses the official embedded YouTube player. YouTube background playback is not provided.", color = Color.LightGray)
                        } else {
                            Text(if (tab == "Home") "Your Library" else tab, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = {
                                val allGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                                if (allGranted) scanMedia() else permissionLauncher.launch(permissions)
                            }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Scan phone media") }
                            Text(message, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                            val shown = media.filter { when(tab) { "Music" -> !it.isVideo; "Videos" -> it.isVideo; else -> true } }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(shown) { item ->
                                    Card(Modifier.fillMaxWidth().clickable {
                                        selected = item
                                        player.setMediaItem(MediaItem.fromUri(item.uri))
                                        player.prepare()
                                        player.play()
                                    }, colors = CardDefaults.cardColors(containerColor = panel)) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if(item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote, null, tint = purple)
                                            Spacer(Modifier.width(12.dp))
                                            Text(item.title, modifier = Modifier.weight(1f), color = Color.White)
                                            Icon(Icons.Default.PlayArrow, null, tint = purple)
                                        }
                                    }
                                }
                            }
                            selected?.let { current ->
                                Spacer(Modifier.height(8.dp))
                                Text("Now playing: ${current.title}", color = purple, fontWeight = FontWeight.Bold)
                                AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                                    update = { it.player = player },
                                    modifier = Modifier.fillMaxWidth().height(if(current.isVideo) 220.dp else 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchYouTube(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    val key = BuildConfig.YOUTUBE_API_KEY
    require(key != "PUT_YOUR_YOUTUBE_DATA_API_KEY_HERE") { "Add a YouTube Data API key first" }
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    val url = URL("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20&q=$encoded&key=$key")
    val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 15000 }
    try {
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val items = json.getJSONArray("items")
        (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            item.getJSONObject("snippet").getString("title") to item.getJSONObject("id").getString("videoId")
        }
    } finally { conn.disconnect() }
}
