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
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Модель данных ---
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String,
    var streamUrl: String? = null
)

// --- ViewModel (Мозг приложения) ---
class MusicViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<List<Track>>(emptyList())
    val uiState: StateFlow<List<Track>> = _uiState

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private var exoPlayer: ExoPlayer? = null

    fun initPlayer(context: android.content.Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

    fun searchYouTube(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            _isSearching.value = true
            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_logic")
                val results = module.callAttr("search_music", query).asList()
                
                val tracks = results.map { 
                    val map = it.asMap()
                    Track(
                        id = map["id"].toString(),
                        title = map["title"].toString(),
                        artist = map["artist"].toString(),
                        cover = map["cover"].toString()
                    )
                }
                _uiState.value = tracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playTrack(track: Track) {
        _currentTrack.value = track
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_logic")
                val url = module.callAttr("get_stream_url", track.id).toString()
                
                withContext(Dispatchers.Main) {
                    exoPlayer?.apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
    }
}

// --- Главная Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Запуск Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val vm: MusicViewModel = viewModel()
                    vm.initPlayer(LocalContext.current)
                    MainScreen(vm)
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: MusicViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTrack by vm.currentTrack.collectAsState()

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Column {
                currentTrack?.let { track ->
                    MiniPlayer(track)
                }
                BottomNavigationBar(selectedTab) { selectedTab = it }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(vm: MusicViewModel) {
    val tracks by vm.uiState.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    var searchText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Верхняя панель (Logo + Icons)
        TopBar()

        // Категории
        CategoryChips()

        // Поиск
        TextField(
            value = searchText,
            onValueChange = { 
                searchText = it
                if (it.length > 2) vm.searchYouTube(it)
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск в YouTube Music", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color.DarkGray.copy(0.4f),
                textColor = Color.White,
                cursorColor = Color.Red
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.Red)
        }

        // Список треков
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Результаты поиска", 
                    color = Color.White, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(tracks) { track ->
                TrackItem(track) { vm.playTrack(track) }
            }
        }
    }
}

@Composable
fun TrackItem(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.cover,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(track: Track) {
    Surface(
        color = Color(0xFF212121),
        modifier = Modifier.fillMaxWidth().height(64.dp).border(0.5.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.cover,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.AccountCircle, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun CategoryChips() {
    val chips = listOf("Релакс", "Вечеринка", "Заряд энергии", "Веселая")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(chips) { chip ->
            Surface(
                modifier = Modifier.padding(end = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.DarkGray.copy(alpha = 0.5f)
            ) {
                Text(chip, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0F0F0F)) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, "Главная") },
            label = { Text("Главная") }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Explore, "Навигатор") },
            label = { Text("Навигатор") }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.LibraryMusic, "Библиотека") },
            label = { Text("Библиотека") }
        )
    }
}

@Composable fun ExploreTab() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Тренды YouTube Music", color = Color.White) } }
@Composable fun LibraryTab() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Ваши плейлисты и альбомы", color = Color.White) } }
