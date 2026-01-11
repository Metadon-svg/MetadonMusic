package com.metadon.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class Track(val id: String, val title: String, val artist: String, val cover: String)

class MusicViewModel : ViewModel() {
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val newTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    
    var serverUrl = mutableStateOf("http://твоя-ссылка:8000")
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val gson = Gson()
    var player: ExoPlayer? = null

    fun connectToServer() {
        val wsUrl = serverUrl.value
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .removeSuffix("/") + "/ws"
        
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.value = true
                // При подключении просим главную страницу (INIT_HOME в твоем server.py)
                webSocket.send("{\"type\": \"INIT_HOME\"}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = gson.fromJson(text, JsonObject::class.java)
                when(json.get("type").asString) {
                    "INIT_HOME" -> {
                        val data = json.getAsJsonObject("data")
                        recTracks.value = parseTracks(data.getAsJsonArray("rec"))
                        newTracks.value = parseTracks(data.getAsJsonArray("new"))
                    }
                    "SEARCH_RES" -> {
                        searchResults.value = parseTracks(json.getAsJsonArray("data"))
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.value = false
            }
        })
    }

    private fun parseTracks(array: com.google.gson.JsonArray): List<Track> {
        return array.map {
            val obj = it.asJsonObject
            Track(
                obj.get("id").asString,
                obj.get("title").asString,
                obj.get("artist").asString,
                obj.get("thumb").asString // В твоем сервере это 'thumb'
            )
        }
    }

    fun search(q: String) {
        if (q.length > 2) {
            webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}")
        }
    }

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        // Используем эндпоинт /stream/{id} из твоего сервера
        val streamUrl = "${serverUrl.value}/stream/${t.id}"
        player?.setMediaItem(MediaItem.fromUri(streamUrl))
        player?.prepare()
        player?.play()
    }
}

@Composable
fun MainAppUI() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("home") }
    var showUrlDialog by remember { mutableStateOf(!vm.isConnected.value) }

    LaunchedEffect(Unit) { vm.player = ExoPlayer.Builder(ctx).build() }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Настройка сервера") },
            text = {
                TextField(
                    value = vm.serverUrl.value,
                    onValueChange = { vm.serverUrl.value = it },
                    label = { Text("URL Сервера (http://...)") }
                )
            },
            confirmButton = {
                Button(onClick = { vm.connectToServer(); showUrlDialog = false }) { Text("Подключиться") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!vm.isPlayerFull.value) {
                Column {
                    vm.currentTrack.collectAsState().value?.let { MiniPlayer(it, vm) }
                    NavigationBar(containerColor = Color.Black) {
                        NavigationBarItem(selected = tab=="home", onClick={tab="home"}, icon={Icon(Icons.Default.Home,null)}, label={Text("Главная")})
                        NavigationBarItem(selected = tab=="nav", onClick={tab="nav"}, icon={Icon(Icons.Default.Explore,null)}, label={Text("Навигатор")})
                        NavigationBarItem(selected = tab=="lib", onClick={tab="lib"}, icon={Icon(Icons.Default.LibraryMusic,null)}, label={Text("Библиотека")})
                    }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p)) {
            when(tab) {
                "home" -> HomeContent(vm)
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("В разработке", color = Color.White) }
            }
        }
    }
    
    if (vm.isPlayerFull.value) FullPlayerUI(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(vm: MusicViewModel) {
    val rec by vm.recTracks.collectAsState()
    val new by vm.newTracks.collectAsState()
    var q by remember { mutableStateOf("") }

    Column {
        TextField(
            value = q, onValueChange = { q = it; vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск на сервере...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        
        LazyColumn {
            item { Text("Рекомендации с сервера", color = Color.White, modifier = Modifier.padding(16.dp)) }
            items(if(q.isEmpty()) rec else vm.searchResults.collectAsState().value) { track ->
                TrackRow(track) { vm.play(track) }
            }
            if(q.isEmpty()) {
                item { Text("Новинки", color = Color.White, modifier = Modifier.padding(16.dp)) }
                items(new) { track -> TrackRow(track) { vm.play(track) } }
            }
        }
    }
}

@Composable
fun TrackRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(t.artist, color = Color.Gray)
        }
    }
}

@Composable
fun MiniPlayer(t: Track, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !vm.isPlaying.value }) {
                Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(t.artist, color = Color.Gray, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !vm.isPlaying.value }) {
                Text(if (vm.isPlaying.value) "Пауза" else "Играть")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MainAppUI() } }
    }
}
