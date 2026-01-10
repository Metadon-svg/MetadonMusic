package com.metadon.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Модель данных
data class Track(val id: String, val title: String, val artist: String, val artistId: String, val cover: String)

class MusicViewModel : ViewModel() {
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val newTracks = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Загрузка...")

    // ПЕРЕИМЕНОВАНО, чтобы Гитхаб не путался
    val isPlayerActive = mutableStateOf(false)
    val isMusicPlaying = mutableStateOf(false)
    val playbackPosition = mutableLongStateOf(0L)
    val trackDurationTime = mutableLongStateOf(0L)
    
    var player: ExoPlayer? = null

    fun setupPlayer(ctx: android.content.Context) {
        if (player == null) {
            player = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isMusicPlaying.value = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            // Берем длительность только когда плеер готов
                            trackDurationTime.value = duration.coerceAtLeast(0L)
                        }
                    }
                })
            }
            loadHomeData()
            
            // Цикл обновления позиции
            CoroutineScope(Dispatchers.Main).launch {
                while(true) {
                    player?.let {
                        if(it.isPlaying) {
                            playbackPosition.value = it.currentPosition
                            trackDurationTime.value = it.duration.coerceAtLeast(0L)
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun loadHomeData() = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val data = py.callAttr("get_home_data")
            recTracks.value = parsePyList(data.get("rec"))
            newTracks.value = parsePyList(data.get("new"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadArtistSongs(id: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            artistTracks.value = parsePyList(py.callAttr("get_artist_songs", id))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startPlayback(track: Track) {
        currentTrack.value = track
        isMusicPlaying.value = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance().getModule("yt_logic")
                val url = py.callAttr("get_stream_url", track.id).toString()
                val lyr = py.callAttr("get_lyrics", track.id).toString()
                withContext(Dispatchers.Main) {
                    lyricsText.value = lyr
                    player?.apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        play()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun parsePyList(obj: Any?): List<Track> {
        val list = obj as? List<*> ?: return emptyList()
        return list.map {
            val p = it as PyObject
            Track(
                p.get("id").toString(),
                p.get("title").toString(),
                p.get("artist").toString(),
                p.get("artistId").toString(),
                p.get("cover").toString()
            )
        }
    }

    fun getTimeString(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}

@Composable
fun MainAppContent() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    var currentTab by remember { mutableStateOf("home") }
    var selectedArtName by remember { mutableStateOf("") }
    val activeTrack by vm.currentTrack.collectAsState()

    LaunchedEffect(Unit) { vm.setupPlayer(ctx) }
    BackHandler(currentTab != "home") { currentTab = "home" }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!vm.isPlayerActive.value) {
                    Column {
                        activeTrack?.let { SmallPlayerUI(it, vm) }
                        NavigationBar(containerColor = Color.Black) {
                            NavigationBarItem(selected = currentTab == "home", onClick = { currentTab = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                            NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                            NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                        }
                    }
                }
            }
        ) { p ->
            if (currentTab == "home") {
                HomeView(vm, p) { id, name -> selectedArtName = name; vm.loadArtistSongs(id); currentTab = "artist" }
            } else {
                ArtistView(selectedArtName, vm, p)
            }
        }

        if (vm.isPlayerActive.value) {
            BigPlayerUI(vm)
        }
    }
}

@Composable
fun HomeView(vm: MusicViewModel, p: PaddingValues, onArtist: (String, String) -> Unit) {
    val rec by vm.recTracks.collectAsState()
    val new by vm.newTracks.collectAsState()
    LazyColumn(Modifier.padding(p).fillMaxSize()) {
        item { Text("Здравствуйте, Metadon!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White) }
        item { Text("Рекомендации", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(rec) { TrackRowUI(it, onArtist) { vm.startPlayback(it) } }
        item { Text("Новинки", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(new) { TrackRowUI(it, onArtist) { vm.startPlayback(it) } }
    }
}

@Composable
fun ArtistView(name: String, vm: MusicViewModel, p: PaddingValues) {
    val tracks by vm.artistTracks.collectAsState()
    LazyColumn(Modifier.padding(p).fillMaxSize()) {
        item { Text(name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { TrackRowUI(it, {_,_ ->}) { vm.startPlayback(it) } }
    }
}

@Composable
fun TrackRowUI(t: Track, onArtist: (String, String) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, modifier = Modifier.clickable { if(t.artistId.isNotEmpty()) onArtist(t.artistId, t.artist) })
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun SmallPlayerUI(t: Track, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerActive.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (vm.isMusicPlaying.value) vm.player?.pause() else vm.player?.play() }) {
                Icon(if (vm.isMusicPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun BigPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyr by vm.lyricsText.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerActive.value = false }, Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(24.dp))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 18.sp)
            
            Spacer(Modifier.height(24.dp))
            
            // Слайдер перемотки
            Slider(
                value = vm.playbackPosition.value.toFloat(), 
                valueRange = 0f..vm.trackDurationTime.value.toFloat().coerceAtLeast(1f), 
                onValueChange = { vm.player?.seekTo(it.toLong()) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.getTimeString(vm.playbackPosition.value), color = Color.Gray)
                Text(vm.getTimeString(vm.trackDurationTime.value), color = Color.Gray)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToPrevious() })
                Surface(Modifier.size(72.dp).clickable { if (vm.isMusicPlaying.value) vm.player?.pause() else vm.player?.play() }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (vm.isMusicPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToNext() })
            }
            
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(lyr, color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent { MaterialTheme { MainAppContent() } }
    }
}
