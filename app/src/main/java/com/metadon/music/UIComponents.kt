package com.metadon.music

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun MainAppScreen() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    val curTrack by vm.currentTrack.collectAsState()
    val recTracks by vm.recTracks.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isPlaying = vm.isPlaying.value
    val isFull = vm.isPlayerFull.value

    var tab by remember { mutableStateOf("home") }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.initPlayer(ctx)
        vm.connect()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!isFull) {
                Column {
                    AnimatedVisibility(
                        visible = curTrack != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        curTrack?.let { MiniPlayerUI(it, isPlaying, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when (tab) {
                "home" -> HomeTabUI(vm, recTracks)
                "search" -> SearchTabUI(vm, q) { q = it }
                "lib" -> LibraryTabUI(vm)
            }
        }
    }

    AnimatedVisibility(visible = isFull, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

// --- ПОЛНЫЙ ПЛЕЕР (С ИСПРАВЛЕННЫМИ КНОПКАМИ) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyrics by vm.lyricsText.collectAsState()
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value
    val isPlaying = vm.isPlaying.value
    val isLiked = t.isLiked
    
    // Состояния кнопок
    val shuffleColor = if (vm.isShuffle.value) Color.White else Color.White.copy(0.3f)
    val repeatMode = vm.repeatMode.value
    // Логика иконки повтора
    val repeatIcon = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
    val repeatColor = if (repeatMode == 0) Color.White.copy(0.3f) else Color.White
    
    var playerTab by remember { mutableStateOf("track") }
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                MenuItem(Icons.Default.Download, "Скачать")
                MenuItem(Icons.Default.PlaylistAdd, "Добавить в плейлист")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).pointerInput(Unit){}) {
        AsyncImage(
            model = t.cover, contentDescription = null, 
            modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f), 
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)))

        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Text("Трек", color = if(playerTab=="track") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="track" }.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Текст", color = if(playerTab=="lyrics") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="lyrics" }.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (playerTab == "track") {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(40.dp)) {
                    AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.weight(1f))
                
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(t.artist, color = Color.White.copy(0.7f), fontSize = 18.sp, maxLines = 1)
                    }
                    IconButton(onClick = { vm.toggleLike(t) }) {
                        Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (isLiked) Color.Red else Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                Slider(
                    value = pos.toFloat(),
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    onValueChange = { vm.seekTo(it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(vm.formatTime(pos), color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Text(vm.formatTime(dur), color = Color.White.copy(0.6f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(24.dp))
                
                // КНОПКИ УПРАВЛЕНИЯ
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    // Shuffle
                    IconButton(onClick = { vm.toggleShuffle() }) { 
                        Icon(Icons.Rounded.Shuffle, null, tint = shuffleColor) 
                    }
                    
                    // Prev
                    IconButton(onClick = { vm.prev() }) { 
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp)) 
                    }
                    
                    // Play/Pause
                    Surface(modifier = Modifier.size(80.dp).clickable { 
                        if(isPlaying) vm.player?.pause() else vm.player?.play() 
                    }, shape = CircleShape, color = Color.White) {
                        Box(contentAlignment = Alignment.Center) { 
                            Icon(if(isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp)) 
                        }
                    }

                    // Next
                    IconButton(onClick = { vm.next() }) { 
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp)) 
                    }
                    
                    // Repeat (меняет иконку и цвет)
                    IconButton(onClick = { vm.toggleRepeat() }) { 
                        Icon(repeatIcon, null, tint = repeatColor) 
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    item {
                        Text(lyrics, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 36.sp, modifier = Modifier.padding(vertical = 32.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

// ... Остальные компоненты (MiniPlayerUI, TrackRowUI и т.д.) оставь как были, они работают
// Но я дублирую их ниже для целостности файла, если ты копируешь целиком

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp)) }
        items(tracks) { t -> TrackRowUI(t, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, onQ: (String) -> Unit) {
    val res by vm.searchResults.collectAsState()
    val sugs by vm.suggestions.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { onQ(it); vm.suggest(it); vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
        )
        LazyColumn {
            if (res.isEmpty()) {
                items(sugs) { s ->
                    Row(Modifier.fillMaxWidth().clickable { onQ(s); vm.search(s) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, color = Color.White, fontSize = 16.sp)
                        Icon(Icons.Default.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                items(res) { t -> TrackRowUI(t, vm) }
            }
        }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val favs by vm.likedTracks.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(favs) { t -> TrackRowUI(t, vm) }
    }
}

@Composable
fun TrackRowUI(t: Track, vm: MusicViewModel) {
    Row(Modifier.fillMaxWidth().clickable { vm.play(t, listOf(t)) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = Color.Gray)
    }
}

@Composable
fun BottomNavUI(curr: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        NavigationBarItem(selected = curr=="home", onClick={onSelect("home")}, icon={Icon(Icons.Default.Home, null)}, label={Text("Главная")})
        NavigationBarItem(selected = curr=="search", onClick={onSelect("search")}, icon={Icon(Icons.Default.Search, null)}, label={Text("Поиск")})
        NavigationBarItem(selected = curr=="lib", onClick={onSelect("lib")}, icon={Icon(Icons.Default.LibraryMusic, null)}, label={Text("Библиотека")})
    }
}
