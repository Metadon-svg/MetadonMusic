package com.metadon.music

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.*
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// --- Модель данных ---
data class Track(val id: String, val title: String, val artist: String, val cover: String)

// --- ViewModel (Логика приложения) ---
class MusicViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<List<Track>>(emptyList())
    val uiState: StateFlow<List<Track>> = _uiState

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    var exoPlayer: ExoPlayer? = null

    fun initPlayer(context: android.content.Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
        }
    }

    fun search(query: String) {
        if (query.length < 3) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance().getModule("yt_logic")
                val results = py.callAttr("search_music", query).asList()
                val tracks = results.map {
                    val m = it.asMap()
                    Track(m["id"].toString(), m["title"].toString(), m["artist"].toString(), m["cover"].toString())
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
                    exoPlayer?.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun togglePlay() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }
}

// --- Главный экран ---
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

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        Column {
                            val currentTrack by vm.currentTrack.collectAsState()
                            val isPlaying by vm.isPlaying.collectAsState()
                            currentTrack?.let { 
                                MiniPlayer(it, isPlaying) { vm.togglePlay() } 
                            }
                            BottomBar(selectedTab) { selectedTab = it }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> HomeTab(vm)
                            1 -> ExploreTab()
                            2 -> LibraryTab()
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
    val tracks by vm.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar()
        CategoryChips()
        
        TextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск песен, альбомов...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = TextFieldDefaults.textFieldColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(Modifier.weight(1f)) {
            item { Text("Рекомендации", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(16.dp)) }
            items(tracks) { track ->
                TrackItem(track) { vm.play(track) }
            }
        }
    }
}

@Composable
fun TrackItem(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(t: Track, isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(color = Color(0xFF212121), modifier = Modifier.fillMaxWidth().height(64.dp).border(0.5.dp, Color.DarkGray)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text("Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.AccountCircle, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun CategoryChips() {
    val items = listOf("Релакс", "Вечеринка", "Заряд энергии", "Веселая")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(items) { chip ->
            Surface(Modifier.padding(end = 8.dp), shape = RoundedCornerShape(8.dp), color = Color.DarkGray.copy(0.4f)) {
                Text(chip, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = selected == 0, onClick = { onSelect(0) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
        NavigationBarItem(selected = selected == 1, onClick = { onSelect(1) }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
        NavigationBarItem(selected = selected == 2, onClick = { onSelect(2) }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
    }
}

@Composable fun ExploreTab() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Вкладка Навигатор", color = Color.White) } }
@Composable fun LibraryTab() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Ваша Медиатека", color = Color.White) } }
