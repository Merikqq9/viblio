package com.viblio.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Bg = Color(0xFF101114)
private val Panel = Color(0xCC24262B)
private val Accent = Color(0xFFC2A47E)
private val Muted = Color(0xFF9A9BA1)

data class Clip(val title: String, val url: String, val playlist: String, val downloaded: Boolean = false, val favorite: Boolean = false)

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) {
                ViblioApp(this, sharedText)
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
    }
}

@Composable
fun ViblioApp(context: Context, sharedLink: String?) {
    val prefs = remember { context.getSharedPreferences("viblio", Context.MODE_PRIVATE) }
    val clips = remember {
        mutableStateListOf<Clip>().apply {
            val arr = runCatching { JSONArray(prefs.getString("clips", "[]") ?: "[]") }.getOrDefault(JSONArray())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(Clip(o.optString("title"), o.optString("url"), o.optString("playlist"), o.optBoolean("downloaded"), o.optBoolean("favorite")))
            }
        }
    }
    val playlists = remember {
        mutableStateListOf<String>().apply {
            val arr = runCatching { JSONArray(prefs.getString("playlists", "[]") ?: "[]") }.getOrDefault(JSONArray())
            for (i in 0 until arr.length()) {
                val value = arr.optString(i)
                if (value.isNotBlank() && !contains(value)) add(value)
            }
            clips.map { it.playlist }.filter { it.isNotBlank() }.forEach { if (!contains(it)) add(it) }
        }
    }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf(sharedLink.orEmpty()) }
    LaunchedEffect(sharedLink) { if (!sharedLink.isNullOrBlank()) { incoming = sharedLink; showAdd = true } }

    fun persistClips() {
        val arr = JSONArray()
        clips.forEach { c ->
            arr.put(JSONObject().put("title", c.title).put("url", c.url)
                .put("playlist", c.playlist).put("downloaded", c.downloaded).put("favorite", c.favorite))
        }
        prefs.edit().putString("clips", arr.toString()).apply()
    }

    fun persistPlaylists() {
        val arr = JSONArray()
        playlists.forEach { arr.put(it) }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    val importVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val folder = File(context.filesDir, "videos").apply { mkdirs() }
                val file = File(folder, "viblio_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Не удалось открыть файл" }.use { source ->
                        file.outputStream().use { output -> source.copyTo(output) }
                    }
                }
                val list = selectedPlaylist ?: "Избранное"
                if (!playlists.contains(list)) {
                    playlists.add(list)
                    persistPlaylists()
                }
                clips.add(Clip(file.nameWithoutExtension, Uri.fromFile(file).toString(), list, true))
                persistClips()
                Toast.makeText(context, "Видео добавлено в «$list»", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Не удалось импортировать видео: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        if (selectedPlaylist == null) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VIBLIO", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("Твоя видеотека", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showNewPlaylist = true }) { Text("+ Плейлист", color = Accent) }
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ПЛЕЙЛИСТЫ", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    Text("${clips.size} видео", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(playlists) { name ->
                        val count = clips.count { it.playlist == name }
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
                            .clickable { selectedPlaylist = name }.padding(16.dp)) {
                            Box(Modifier.fillMaxWidth().height(104.dp).background(
                                listOf(Color(0xFF343A2B), Color(0xFF282D42), Color(0xFF3A293A), Color(0xFF263B3C))[playlists.indexOf(name) % 4],
                                RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase(), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Accent)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("$count видео", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Button(onClick = { importVideo.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Accent),
                    shape = RoundedCornerShape(16.dp)) { Text("Импортировать видео с телефона") }
                Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    shape = RoundedCornerShape(16.dp)) { Text("+ Добавить ссылку", fontWeight = FontWeight.Bold) }
            }
        } else {
            val current = selectedPlaylist!!
            val playlistClips = clips.filter { it.playlist == current }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 30.sp, modifier = Modifier.clickable { selectedPlaylist = null }.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${playlistClips.size} видео · листай вверх", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { importVideo.launch(arrayOf("video/*")) }) { Text("+ Файл", color = Accent) }
                    TextButton(onClick = { showAdd = true }) { Text("+ Ссылка", color = Accent) }
                }
                if (playlistClips.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Здесь пока пусто", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Добавь видеофайл или ссылку", color = Muted)
                        }
                    }
                } else {
                    val pager = rememberPagerState(pageCount = { playlistClips.size })
                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        VideoPage(playlistClips[page], context, onToggleFavorite = { clip ->
                            val index = clips.indexOfFirst { it.url == clip.url && it.playlist == clip.playlist }
                            if (index >= 0) { clips[index] = clips[index].copy(favorite = !clips[index].favorite); persistClips() }
                        })
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddClipDialog(incoming, playlists.toList(), selectedPlaylist, onDismiss = { showAdd = false }) { title, url, list ->
            clips.add(Clip(title.ifBlank { "Видео ${clips.size + 1}" }, url.trim(), list, false))
            if (!playlists.contains(list)) {
                playlists.add(list)
                persistPlaylists()
            }
            persistClips()
            val lower = url.trim().lowercase()
            val isDirect = lower.substringBefore("?").substringBefore("#").let { it.endsWith(".mp4") || it.endsWith(".m3u8") }
            if (isDirect && (lower.startsWith("https://") || lower.startsWith("http://"))) downloadDirect(context, url.trim(), title)
            else Toast.makeText(context, "Ссылка сохранена. Ссылки TikTok не являются видеофайлами — для просмотра внутри Viblio импортируй MP4 с телефона.", Toast.LENGTH_LONG).show()
            incoming = ""
            showAdd = false
        }
    }
    if (showNewPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNewPlaylist = false }, title = { Text("Новый плейлист") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotBlank() && !playlists.contains(clean)) {
                    playlists.add(clean)
                    persistPlaylists()
                }
                showNewPlaylist = false
            }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { showNewPlaylist = false }) { Text("Отмена") } },
            containerColor = Panel)
    }
}

@Composable
fun AddClipDialog(initial: String, lists: List<String>, preferred: String?, onDismiss: () -> Unit,
                  onSave: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    var title by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf(preferred ?: lists.firstOrNull().orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Добавить ссылку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Ссылка на видео") }, placeholder = { Text("https://...") })
                OutlinedTextField(title, { title = it }, label = { Text("Название (необязательно)") }, singleLine = true)
                Text("Сохранить в", color = Muted)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(playlist) }
                    DropdownMenu(expanded, { expanded = false }) {
                        lists.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { playlist = item; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank() && playlist.isNotBlank(), onClick = { if (url.isNotBlank() && playlist.isNotBlank()) onSave(title, url, playlist) }) { Text("Сохранить", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun VideoPage(clip: Clip, context: Context, onToggleFavorite: (Clip) -> Unit) {
    val cleanUrl = clip.url.lowercase().substringBefore("?").substringBefore("#")
    val isDirect = clip.url.startsWith("file://") || clip.url.startsWith("content://") ||
        cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".m3u8")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isDirect) {
            val exoPlayer = remember(clip.url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(clip.url))
                    prepare()
                    playWhenReady = true
                }
            }
            var speed by remember(clip.url) { mutableFloatStateOf(1f) }
            DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
            AndroidView(factory = { PlayerView(it).apply { this.player = exoPlayer; useController = true } },
                modifier = Modifier.fillMaxSize(), update = { it.player = exoPlayer })
            Row(Modifier.align(Alignment.TopEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color(0xAA202126), shape = RoundedCornerShape(22.dp), modifier = Modifier.clickable {
                    speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    exoPlayer.setPlaybackSpeed(speed)
                }) { Text("${speed}×", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold) }
                Surface(color = Color(0xAA202126), shape = RoundedCornerShape(22.dp), modifier = Modifier.clickable { onToggleFavorite(clip) }) {
                    Text(if (clip.favorite) "♥" else "♡", color = Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 20.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Видео ещё не получено", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ссылка сохранена в плейлисте, но Viblio пока не получил сам видеофайл. Веб-страница больше не открывается внутри приложения. Чтобы посмотреть ролик, добавь видеофайл, когда он будет доступен.",
                    color = Muted, fontSize = 14.sp
                )
                Text("♡", color = Accent, modifier = Modifier.clickable { onToggleFavorite(clip) }.padding(8.dp), fontSize = 24.sp)
            }
        }

        Text(clip.title, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp))
    }
}

fun downloadDirect(context: Context, url: String, title: String) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title.ifBlank { "Viblio video" })
            .setDescription("Загрузка видео в Viblio")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Viblio/${System.currentTimeMillis()}.mp4")
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(context, "Загрузка началась", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Не удалось начать загрузку: ${it.message}", Toast.LENGTH_LONG).show()
    }
}
