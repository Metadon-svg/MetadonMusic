package com.metadon.music

import android.os.Bundle
import android.widget.Toast
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

data class Track(val id: String, val title: String, val artist: String, val artistId: String, val cover: String)

class MusicViewModel : ViewModel() {
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val newTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks = MutableStateFlow<List<Track>>(emptyList())
    
    val currentTrack = MutableStateFlow<Track?>(null)
    val isSearching = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableLongStateOf(0L)
    val duration = mutableLongStateOf(0L)
    
    var player: ExoPlayer? = null

    fun init(ctx: android.content.Context) {
        if (player == null) {
            player = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { isPlaying.value = p }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) duration.value = duration.coerceAtLeast(0)
                    }
                })
            }
            loadHome()
            CoroutineScope(Dispatchers.Main).launch {
                while(true) {
                    player?.let { if(it.isPlaying) currentPos.value = it.currentPosition }
                    delay(1000)
                }
            }
        }
    }

    private fun loadHome() = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val data = py.callAttr("get_home_data")
            recTracks.value = mapPyList(data.get("rec"))
            newTracks.value = mapPyList(data.get("new"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startSearch(q: String) = CoroutineScope(Dispatchers.IO).launch {
        if (q.length < 3) return@launch
        isSearching.value = true
        try {
            val py = Python.getInstance().getModule("yt_logic")
            searchResults.value = mapPyList(py.callAttr("search_music", q))
        } finally { isSearching.value = false }
    }

    fun loadArtist(id: String) = CoroutineScope(Dispatchers.IO).launch {
        val py = Python.getInstance().getModule("yt_logic")
        artistTracks.value = mapPyList(py.callAttr("get_artist_songs", id))
    }

    fun play(t: Track) {
        currentTrack.value = t
        CoroutineScope(Dispatchers.IO).launch {
            val py = Python.getInstance().getModule("yt_logic")
            val url = py.callAttr("get_stream_url", t.id).toString()
            withContext(Dispatchers.Main) {
                player?.setMediaItem(MediaItem.fromUri(url))
                player?.prepare()
                player?.play()
            }
        }
    }

    private fun mapPyList(obj: Any?): List<Track> {
        val list = obj as? List<*> ?: return emptyList()
        return list.map {
            val p = it as PyObject
            Track(p.get("id").toString(), p.get("title").toString(), p.get("artist").toString(), p.get("artistId").toString(), p.get("cover").toString())
        }
    }
}

@Composable
fun MainApp() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("home") }
    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.init(ctx) }
    BackHandler(tab != "home" || searchActive) { 
        if (searchActive) searchActive = false else tab = "home" 
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!vm.isPlayerFull.value) {
                Column {
                    vm.currentTrack.collectAsState().value?.let { MiniPlayerUI(it, vm) }
                    NavigationBar(containerColor = Color.Black) {
                        NavigationBarItem(selected = tab=="home", onClick={tab="home"; searchActive=false}, icon={Icon(Icons.Default.Home,null)}, label={Text("Главная")})
                        NavigationBarItem(selected = tab=="nav", onClick={tab="nav"}, icon={Icon(Icons.Default.Explore,null)}, label={Text("Навигатор")})
                        NavigationBarItem(selected = tab=="lib", onClick={tab="lib"}, icon={Icon(Icons.Default.LibraryMusic,null)}, label={Text("Библиотека")})
                    }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when(tab) {
                "home" -> {
                    Column {
                        SearchBarUI(searchText, { searchText = it; vm.startSearch(it) }, { searchActive = true })
                        if (searchActive) {
                            SearchList(vm)
                        } else {
                            HomeList(vm) { id, name -> artistName = name; vm.loadArtist(id); tab = "artist" }
                        }
                    }
                }
                "artist" -> ArtistList(artistName, vm)
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("В разработке", color = Color.White) }
            }
        }
        if (vm.isPlayerFull.value) FullPlayerUI(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarUI(text: String, onValueChange: (String) -> Unit, onFocus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = text, onValueChange = onValueChange,
            modifier = Modifier.weight(1f).onGloballyPositioned { onFocus() },
            placeholder = { Text("Поиск музыки...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.textFieldColors(containerColor = Color(0xFF1A1A1A), textColor = Color.White)
        )
    }
}

@Composable
fun HomeList(vm: MusicViewModel, onArt: (String, String) -> Unit) {
    val rec by vm.recTracks.collectAsState()
    val new by vm.newTracks.collectAsState()
    LazyColumn {
        item { Text("Рекомендации", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        if (rec.isEmpty()) item { CircularProgressIndicator(Modifier.padding(16.dp), color = Color.Red) }
        items(rec) { TrackRow(it, onArt) { vm.play(it) } }
        
        item { Text("Новинки", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(new) { TrackRow(it, onArt) { vm.play(it) } }
    }
}

@Composable
fun SearchList(vm: MusicViewModel) {
    val res by vm.searchResults.collectAsState()
    if (vm.isSearching.value) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Red)
    LazyColumn {
        items(res) { TrackRow(it, {_,_ ->}) { vm.play(it) } }
    }
}

@Composable
fun ArtistList(name: String, vm: MusicViewModel) {
    val tracks by vm.artistTracks.collectAsState()
    Column {
        Text(name, color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(16.dp))
        LazyColumn { items(tracks) { TrackRow(it, {_,_ ->}) { vm.play(it) } } }
    }
}

@Composable
fun TrackRow(t: Track, onArt: (String, String) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, modifier = Modifier.clickable { if(t.artistId.isNotEmpty()) onArt(t.artistId, t.artist) })
        }
    }
}

@Composable
fun MiniPlayerUI(t: Track, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play() }) {
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
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp)); AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(24.dp)); Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(t.artist, color = Color.Gray, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Slider(value = vm.currentPos.value.toFloat(), valueRange = 0f..vm.duration.value.toFloat().coerceAtLeast(1f), onValueChange = { vm.player?.seekTo(it.toLong()) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToPrevious() })
                Surface(Modifier.size(72.dp).clickable { if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play() }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp)) }
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToNext() })
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent { MaterialTheme { MainApp() } }
    }
}
