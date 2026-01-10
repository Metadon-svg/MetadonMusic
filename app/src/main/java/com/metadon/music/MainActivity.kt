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
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Track(val id: String, val title: String, val artist: String, val cover: String)

class MusicViewModel : ViewModel() {
    val uiState = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    var exoPlayer: ExoPlayer? = null

    fun search(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val py = Python.getInstance().getModule("yt_logic")
            val results = py.callAttr("search_music", query).asList()
            uiState.value = results.map {
                val m = it.asMap()
                Track(m["id"].toString(), m["title"].toString(), m["artist"].toString(), m["cover"].toString())
            }
        }
    }

    fun play(track: Track) {
        currentTrack.value = track
        CoroutineScope(Dispatchers.IO).launch {
            val py = Python.getInstance().getModule("yt_logic")
            val url = py.callAttr("get_stream_url", track.id).toString()
            withContext(Dispatchers.Main) {
                exoPlayer?.setMediaItem(MediaItem.fromUri(url))
                exoPlayer?.prepare()
                exoPlayer?.play()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: MusicViewModel = viewModel()
                val ctx = LocalContext.current
                LaunchedEffect(Unit) { vm.exoPlayer = ExoPlayer.Builder(ctx).build() }
                
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        Column {
                            vm.currentTrack.collectAsState().value?.let { MiniPlayer(it) }
                            NavigationBar(containerColor = Color.Black) {
                                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                            }
                        }
                    }
                ) { p ->
                    Column(Modifier.padding(p)) {
                        if (tab == 0) HomeTab(vm) else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("В разработке", color = Color.White) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(vm: MusicViewModel) {
    var q by remember { mutableStateOf("") }
    val tracks by vm.uiState.collectAsState()
    
    TextField(value = q, onValueChange = { q = it; if(it.length > 2) vm.search(it) }, 
        modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Поиск музыки...") })
    
    LazyColumn {
        items(tracks) { TrackItem(it) { vm.play(it) } }
    }
}

@Composable
fun TrackItem(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(t.title, color = Color.White, maxLines = 1); Text(t.artist, color = Color.Gray) }
    }
}

@Composable
fun MiniPlayer(t: Track) {
    Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth().height(60.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}
