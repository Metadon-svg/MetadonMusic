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
import androidx.compose.ui.text.style.TextOverflow
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

// --- Модель данных ---
data class Track(
    val id: String, 
    val title: String, 
    val artist: String, 
    val artistId: String, 
    val cover: String
)

// --- ViewModel (Логика плеера и поиска) ---
class MusicViewModel : ViewModel() {
    val uiState = MutableStateFlow<List<Track>>(emptyList())
    val artistState = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    
    val isPlayerFullScreen = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    
    var currentPos = mutableLongStateOf(0L)
    var duration = mutableLongStateOf(0L)
    
    var exoPlayer: ExoPlayer? = null

    fun initPlayer(context: android.content.Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying.value = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            duration.longValue = duration.coerceAtLeast(0L)
                        }
                    }
                })
            }

            // Таймер для обновления прогресс-бара
            CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    exoPlayer?.let {
                        if (it.isPlaying) {
                            currentPos.longValue = it.currentPosition
                            duration.longValue = it.duration.coerceAtLeast(0L)
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    fun seekTo(position: Float) {
        exoPlayer?.seekTo(position.toLong())
        currentPos.longValue = position.toLong()
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    // Поиск (Исправлена типизация PyObject)
    fun search(query: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val res = py.callAttr("search_music", query).asList()
            val tracks = res.map { item ->
                val p = item as PyObject
                Track(
                    id = p.get("id").toString(),
                    title = p.get("title").toString(),
                    artist = p.get("artist").toString(),
                    artistId = p.get("artistId").toString(),
                    cover = p.get("cover").toString()
                )
            }
            uiState.value = tracks
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Загрузка песен артиста
    fun loadArtist(id: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val py = Python.getInstance().getModule("yt_logic")
            val res = py.callAttr("get_artist_songs", id).asList()
            artistState.value = res.map { item ->
                val p = item as PyObject
                Track(
                    id = p.get("id").toString(),
                    title = p.get("title").toString(),
                    artist = p.get("artist").toString(),
                    artistId = "", // Внутри страницы артиста id не обязателен
                    cover = p.get("cover").toString()
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun play(track: Track) {
        currentTrack.value = track
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

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: MusicViewModel = viewModel()
                val ctx = LocalContext.current
                
                LaunchedEffect(Unit) { 
                    vm.initPlayer(ctx)
                    vm.search("Костюшкин Стас") // Стартовые рекомендации
                }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    MainNavigation(vm)
                    
                    // Анимация выезда полноэкранного плеера
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
    var artistName by remember { mutableStateOf("") }
    val track by vm.currentTrack.collectAsState()

    // Обработка кнопки "Назад" (выйти из режима артиста)
    BackHandler(screen != "home") { screen = "home" }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!vm.isPlayerFullScreen.value) {
                Column {
                    track?.let { MiniPlayer(it, vm) }
                    NavigationBar(containerColor = Color.Black) {
                        NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
                        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
                    }
                }
            }
        }
    ) { p ->
        Column(Modifier.padding(p)) {
            if (screen == "home") {
                HomeTab(vm) { id, name ->
                    artistName = name
                    vm.loadArtist(id)
                    screen = "artist"
                }
            } else {
                ArtistScreen(artistName, vm)
            }
        }
    }
}

@Composable
fun HomeTab(vm: MusicViewModel, onArtist: (String, String) -> Unit) {
    val tracks by vm.uiState.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Здравствуйте, Metadon!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White)
            Text("Рекомендации", fontSize = 18.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
        items(tracks) { track ->
            TrackItem(track, onArtist) { vm.play(track) }
        }
    }
}

@Composable
fun ArtistScreen(name: String, vm: MusicViewModel) {
    val tracks by vm.artistState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text(name, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Color.White)
        LazyColumn {
            items(tracks) { track ->
                TrackItem(track, { _, _ -> }) { vm.play(track) }
            }
        }
    }
}

@Composable
fun TrackItem(t: Track, onArtist: (String, String) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t.artist, 
                color = Color.Gray, 
                fontSize = 14.sp,
                modifier = Modifier.clickable { if(t.artistId.isNotEmpty()) onArtist(t.artistId, t.artist) }
            )
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
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { if (vm.isPlaying.value) vm.exoPlayer?.pause() else vm.exoPlayer?.play() }) {
                Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun FullPlayer(vm: MusicViewModel) {
    val track = vm.currentTrack.collectAsState().value ?: return
    val pos = vm.currentPos.longValue
    val dur = vm.duration.longValue

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFullScreen.value = false }, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(40.dp))
            AsyncImage(
                model = track.cover, 
                contentDescription = null, 
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(48.dp))
            
            Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start), maxLines = 1)
            Text(track.artist, color = Color.Gray, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
            
            Spacer(Modifier.height(32.dp))
            
            // Слайдер перемотки
            Slider(
                value = pos.toFloat(),
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                onValueChange = { vm.seekTo(it) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.Gray, fontSize = 12.sp)
                Text(vm.formatTime(dur), color = Color.Gray, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(40.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = Color.Gray)
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                
                // Большая кнопка Play/Pause
                Surface(
                    shape = CircleShape, 
                    color = Color.White, 
                    modifier = Modifier.size(72.dp).clickable { 
                        if (vm.isPlaying.value) vm.exoPlayer?.pause() else vm.exoPlayer?.play() 
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                }

                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.Repeat, null, tint = Color.Gray)
            }
        }
    }
}
