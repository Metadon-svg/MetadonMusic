package com.metadon.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

// Данные
data class Track(val id: String, val title: String, val artist: String, val artistId: String = "", val cover: String)

// ViewModel
class MusicViewModel : ViewModel() {
    val uiState = MutableStateFlow<List<Track>>(emptyList())
    val artistState = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val isPlayerFullScreen = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    var exoPlayer: ExoPlayer? = null

    fun search(query: String) = CoroutineScope(Dispatchers.IO).launch {
        val py = Python.getInstance().getModule("yt_logic")
        val res = py.callAttr("search_music", query).asList()
        uiState.value = res.map { 
            val m = it.asMap()
            Track(m["id"].toString(), m["title"].toString(), m["artist"].toString(), m["artistId"].toString(), m["cover"].toString())
        }
    }

    fun loadArtist(id: String) = CoroutineScope(Dispatchers.IO).launch {
        val py = Python.getInstance().getModule("yt_logic")
        val res = py.callAttr("get_artist_songs", id).asList()
        artistState.value = res.map { 
            val m = it.asMap()
            Track(m["id"].toString(), m["title"].toString(), m["artist"].toString(), "", m["cover"].toString())
        }
    }

    fun play(track: Track) {
        currentTrack.value = track
        isPlaying.value = true
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
                LaunchedEffect(Unit) { vm.exoPlayer = ExoPlayer.Builder(ctx).build(); vm.search("Костюшкин Стас") }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    MainNavigation(vm)
                    
                    // Полноэкранный плеер
                    AnimatedVisibility(
                        visible = vm.isPlayerFullScreen.value,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        FullPlayer(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(vm: MusicViewModel) {
    var screen by remember { mutableStateOf("home") }
    var selectedArtistName by remember { mutableStateOf("") }
    val track by vm.currentTrack.collectAsState()

    BackHandler(screen != "home") { screen = "home" }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!vm.isPlayerFullScreen.value) {
                Column {
                    track?.let { MiniPlayer(it, vm) }
                    NavigationBar(containerColor = Color.Black) {
                        NavigationBarItem(selected = true, onClick = { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                        NavigationBarItem(selected = false, onClick = { }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                        NavigationBarItem(selected = false, onClick = { }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                    }
                }
            }
        }
    ) { p ->
        Column(Modifier.padding(p)) {
            if (screen == "home") {
                HomeTab(vm) { artistId, name ->
                    selectedArtistName = name
                    vm.loadArtist(artistId)
                    screen = "artist"
                }
            } else {
                ArtistScreen(selectedArtistName, vm)
            }
        }
    }
}

@Composable
fun HomeTab(vm: MusicViewModel, onArtistClick: (String, String) -> Unit) {
    val tracks by vm.uiState.collectAsState()
    LazyColumn {
        item {
            Text("Здравствуйте, Metadon!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White)
            Text("Рекомендации", fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), color = Color.Gray)
        }
        items(tracks) { track ->
            TrackItem(track, onArtistClick) { vm.play(track) }
        }
    }
}

@Composable
fun ArtistScreen(name: String, vm: MusicViewModel) {
    val tracks by vm.artistState.collectAsState()
    Column {
        Text(name, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White)
        LazyColumn {
            items(tracks) { track ->
                TrackItem(track, {_,_ ->}) { vm.play(track) }
            }
        }
    }
}

@Composable
fun TrackItem(t: Track, onArtistClick: (String, String) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, modifier = Modifier.clickable { onArtistClick(t.artistId, t.artist) })
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(t: Track, vm: MusicViewModel) {
    Surface(
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFullScreen.value = true }
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { /* play/pause */ }) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun FullPlayer(vm: MusicViewModel) {
    val track = vm.currentTrack.collectAsState().value ?: return
    
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFullScreen.value = false }, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(40.dp))
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(48.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(track.artist, color = Color.Gray, fontSize = 18.sp)
                }
                Icon(Icons.Default.AddCircleOutline, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            
            Spacer(Modifier.height(32.dp))
            Slider(value = 0.3f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1:20", color = Color.Gray, fontSize = 12.sp)
                Text("3:45", color = Color.Gray, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = Color.Gray)
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(48.dp).padding(16.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.Repeat, null, tint = Color.Gray)
            }
        }
    }
}
