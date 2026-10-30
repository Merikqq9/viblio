package com.viblio.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Bg = Color(0xFF090A0F)
private val Panel = Color(0xFF171923)
private val Accent = Color(0xFFB7F36B)
private val Muted = Color(0xFF9A9DAC)

data class Clip(val title: String, val url: String, val playlist: String, val downloaded: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) {
                ViblioApp(this, shared)
            }
        }
    }
}

@Composable
fun ViblioApp(context: Context, sharedLink: String?) {
    val prefs = remember { context.getSharedPreferences("viblio", Context.MODE_PRIVATE) }
    val clips = remember {
        mutableStateListOf<Clip>().apply {
            val raw = prefs.getString("clips", "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Clip(o.optString("title"), o.optString("url"), o.optString("playlist"), o.optBoolean("downloaded")))
            }
        }
    }
    val playlists = remember { mutableStateListOf("Избранное", "Юмор", "Обучение", "Спорт", "Рецепты") }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf(sharedLink.orEmpty()) }

    fun persist() {
        val arr = JSONArray()
        clips.forEach { c ->
            arr.put(JSONObject().put("title", c.title).put("url", c.url)
                .put("playlist", c.playlist).put("downloaded", c.downloaded))
        }
        prefs.edit().putString("clips", arr.toString()).apply()
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
                Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    shape = RoundedCornerShape(16.dp)) { Text("+ Добавить видео", fontWeight = FontWeight.Bold) }
            }
        } else {
            val current = selectedPlaylist!!
            val playlistClips = clips.filter { it.playlist == current }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 30.sp, modifier = Modifier.clickable { selectedPlaylist = null }.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${playlistClips.size} видео · листай вверх", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { showAdd = true }) { Text("+ Добавить", color = Accent) }
                }
                if (playlistClips.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Здесь пока пусто", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Добавь первое видео в этот плейлист", color = Muted)
                        }
                    }
                } else {
                    val pager = rememberPagerState(pageCount = { playlistClips.size })
                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        VideoPage(playlistClips[page], context)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddClipDialog(incoming, playlists.toList(), selectedPlaylist, onDismiss = { showAdd = false }) { title, url, list ->
            val isDirect = url.lowercase().let { it.endsWith(".mp4") || it.contains(".mp4?") || it.endsWith(".m3u8") }
            clips.add(Clip(title.ifBlank { "Видео ${clips.size + 1}" }, url.trim(), list, false))
            persist()
            if (isDirect) downloadDirect(context, url.trim(), title)
            else Toast.makeText(context, "Ссылка сохранена. Для скачивания нужен разрешённый прямой URL видео.", Toast.LENGTH_LONG).show()
            incoming = ""
            showAdd = false
        }
    }
    if (showNewPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNewPlaylist = false }, title = { Text("Новый плейлист") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank() && !playlists.contains(name.trim())) playlists.add(name.trim())
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
    var playlist by remember { mutableStateOf(preferred ?: lists.firstOrNull() ?: "Избранное") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Добавить видео") },
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
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onSave(title, url, playlist) }) { Text("Сохранить", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun VideoPage(clip: Clip, context: Context) {
    val isDirect = clip.url.lowercase().let { it.endsWith(".mp4") || it.contains(".mp4?") || it.endsWith(".m3u8") }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isDirect) {
            val player = remember(clip.url) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(clip.url)); prepare(); playWhenReady = true } }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("↗", color = Accent, fontSize = 54.sp)
                Text("Ссылка сохранена", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Для просмотра внутри Viblio добавь разрешённый видеофайл MP4 или M3U8. Ссылку можно открыть отдельно.", color = Muted)
                Spacer(Modifier.height(18.dp))
                Button(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(clip.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Text("Открыть ссылку") }
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
