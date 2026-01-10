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

// Модель данных
data class Track(val id: String, val title: String, val artist: String, val artistId: String, val cover: String)

class MusicViewModel : ViewModel() {
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val newTracks = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Загрузка текста...")
    
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableLongStateOf(0L)
    val duration = mutableLongStateOf(0L)
    var exoPlayer: ExoPlayer? = null

    fun initPlayer(ctx: android.content.Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { isPlaying.value = p }
                })
            }
            loadHome()
            CoroutineScope(Dispatchers.Main).launch {
                while(true) {
                    exoPlayer?.let { 
                        if(it.isPlaying) {
                            currentPos.longValue = it.currentPosition
                            duration.longValue = it.duration.coerceAtLeast(0)
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun loadHome() = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val homeData = py.callAttr("get_home_data")
            
            // Достаем списки напрямую через get(), чтобы избежать ошибки Type Inference K
            val recObj = homeData.get("rec")
            val newObj = homeData.get("new")
            
            recTracks.value = mapPyList(recObj)
            newTracks.value = mapPyList(newObj)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadArtist(id: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val songsObj = py.callAttr("get_artist_songs", id)
            artistTracks.value = mapPyList(songsObj)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun play(track: Track) {
        currentTrack.value = track
        lyricsText.value = "Загрузка текста..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance().getModule("yt_logic")
                val url = py.callAttr("get_stream_url", track.id).toString()
                val l = py.callAttr("get_lyrics", track.id).toString()
                withContext(Dispatchers.Main) {
                    lyricsText.value = l
                    exoPlayer?.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ИСПРАВЛЕННЫЙ МАППИНГ: Самый безопасный способ для Гитхаба
    private fun mapPyList(obj: Any?): List<Track> {
        val pyList = obj as? List<*> ?: return emptyList()
        val result = mutableListOf<Track>()
        for (item in pyList) {
            val p = item as PyObject
            result.add(
                Track(
                    id = p.get("id").toString(),
                    title = p.get("title").toString(),
                    artist = p.get("artist").toString(),
                    artistId = p.get("artistId").toString(),
                    cover = p.get("cover").toString()
                )
            )
        }
        return result
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
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
                var screen by remember { mutableStateOf("home") }
                var artName by remember { mutableStateOf("") }
                val curTrack by vm.currentTrack.collectAsState()

                LaunchedEffect(Unit) { vm.initPlayer(ctx) }
                BackHandler(screen != "home") { screen = "home" }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (!vm.isPlayerFull.value) {
                                Column {
                                    curTrack?.let { MiniPlayer(it, vm) }
                                    NavigationBar(containerColor = Color.Black) {
                                        NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                                        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                                        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                                    }
                                }
                            }
                        }
                    ) { p ->
                        if (screen == "home") HomeTab(vm, p) { id, name -> artName = name; vm.loadArtist(id); screen = "artist" }
                        else ArtistTab(artName, vm, p)
                    }

                    if (vm.isPlayerFull.value) FullPlayer(vm)
                }
            }
        }
    }
}

@Composable
fun HomeTab(vm: MusicViewModel, p: PaddingValues, onArt: (String, String) -> Unit) {
    val rec by vm.recTracks.collectAsState()
    val new by vm.newTracks.collectAsState()
    LazyColumn(Modifier.padding(p).fillMaxSize()) {
        item { Text("Здравствуйте, Metadon!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White) }
        item { Text("Рекомендации", color = Color.Gray, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
        items(rec) { track -> TrackRow(track, onArt) { vm.play(track) } }
        item { Text("Новинки", color = Color.Gray, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) }
        items(new) { track -> TrackRow(track, onArt) { vm.play(track) } }
    }
}

@Composable
fun ArtistTab(name: String, vm: MusicViewModel, p: PaddingValues) {
    val tracks by vm.artistTracks.collectAsState()
    LazyColumn(Modifier.padding(p).fillMaxSize()) {
        item { Text(name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { track -> TrackRow(track, { _, _ -> }) { vm.play(track) } }
    }
}

@Composable
fun TrackRow(t: Track, onArt: (String, String) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, modifier = Modifier.clickable { if(t.artistId.isNotEmpty()) onArt(t.artistId, t.artist) })
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(t: Track, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { if (vm.isPlaying.value) vm.exoPlayer?.pause() else vm.exoPlayer?.play() }) {
                Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayer(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyr by vm.lyricsText.collectAsState()
    val pos = vm.currentPos.longValue
    val dur = vm.duration.longValue

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(24.dp))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 18.sp)
            
            Spacer(Modifier.height(24.dp))
            Slider(
                value = pos.toFloat(), 
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f), 
                onValueChange = { vm.exoPlayer?.seekTo(it.toLong()) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.Gray, fontSize = 12.sp)
                Text(vm.formatTime(dur), color = Color.Gray, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.exoPlayer?.seekToPrevious() })
                Surface(Modifier.size(72.dp).clickable { if (vm.isPlaying.value) vm.exoPlayer?.pause() else vm.exoPlayer?.play() }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.exoPlayer?.seekToNext() })
            }
            
            Spacer(Modifier.height(32.dp))
            Text("Текст песни", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(lyr, color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp), fontSize = 16.sp)
            }
        }
    }
}
