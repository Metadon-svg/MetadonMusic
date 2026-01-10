package com.metadon.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Модель данных трека
data class Track(val id: String, val title: String, val artist: String, val cover: String)

// ViewModel - логика поиска и проигрывания
class MusicViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<List<Track>>(emptyList())
    val uiState: StateFlow<List<Track>> = _uiState

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    var exoPlayer: ExoPlayer? = null

    fun initPlayer(context: android.content.Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

    fun search(query: String) {
        if (query.length < 3) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance().getModule("yt_logic")
                val results = py.callAttr("search_music", query).asList()
                val tracks = results.map { item ->
                    // Исправлено: явное получение данных для обхода ошибок Type Inference
                    Track(
                        id = item.get("id").toString(),
                        title = item.get("title").toString(),
                        artist = item.get("artist").toString(),
                        cover = item.get("cover").toString()
                    )
                }
                _uiState.value = tracks
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun play(track: Track) {
        _currentTrack.value = track
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance().getModule("yt_logic")
                val url = py.callAttr("get_stream_url", track.id).toString()
                withContext(Dispatchers.Main) {
                    exoPlayer?.apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        play()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация Python среды
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: MusicViewModel = viewModel()
                vm.initPlayer(LocalContext.current)
                
                var selectedTab by remember { mutableIntStateOf(0) }
                val currentTrack by vm.currentTrack.collectAsState()

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        Column {
                            currentTrack?.let { MiniPlayer(it) }
                            NavigationBar(containerColor = Color.Black) {
                                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                            }
                        }
                    }
                ) { p ->
                    Box(Modifier.padding(p)) {
                        when (selectedTab) {
                            0 -> HomeTab(vm)
                            1 -> CenterText("Вкладка Навигатор")
                            2 -> CenterText("Медиатека")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(vm: MusicViewModel) {
    var query by remember { mutableStateOf("") }
    val tracks by vm.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Search, null, tint = Color.White)
        }
        
        TextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Искать в YouTube Music", color = Color.Gray) },
            colors = TextFieldDefaults.textFieldColors(containerColor = Color(0xFF1A1A1A), textColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        )
        
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Рекомендации", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            items(tracks) { track ->
                TrackRow(track) { vm.play(track) }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(track: Track) {
    Surface(color = Color(0xFF212121), modifier = Modifier.fillMaxWidth().height(64.dp).border(0.5.dp, Color.DarkGray)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun CenterText(txt: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(txt, color = Color.White)
    }
}
