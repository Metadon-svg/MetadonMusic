package com.metadon.music

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun MainAppScreen() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    val curTrack by vm.currentTrack.collectAsState()
    val recTracks by vm.recTracks.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    
    var tab by remember { mutableStateOf("home") }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (vm.player == null) {
            vm.player = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { vm.isPlaying.value = p }
                })
            }
        }
        vm.connect()
        vm.startTimer()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!vm.isPlayerFull.value) {
                Column {
                    AnimatedVisibility(visible = curTrack != null) {
                        curTrack?.let { MiniPlayerUI(it, vm.isPlaying.value, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            if (tab == "home") HomeTabUI(vm, recTracks)
            else if (tab == "search") SearchTabUI(vm, q) { q = it }
            else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Библиотека", color = Color.White) }
        }
    }

    AnimatedVisibility(visible = vm.isPlayerFull.value, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

@Composable
fun TrackItemUI(t: Track, vm: MusicViewModel) {
    Row(Modifier.fillMaxWidth().clickable { vm.play(t) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Card(
        modifier = Modifier.padding(8.dp).fillMaxWidth().height(60.dp).clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value
    val isPlaying = vm.isPlaying.value

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color.Black)))) {
        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(40.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(48.dp))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(t.artist, color = Color.White.copy(0.7f), fontSize = 18.sp)
            
            Spacer(Modifier.height(32.dp))
            Slider(
                value = pos.toFloat(),
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                onValueChange = { vm.seekTo(it) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.Gray); Text(vm.formatTime(dur), color = Color.Gray)
            }
            
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = Color.Gray)
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Surface(Modifier.size(72.dp).clickable { if(isPlaying) vm.player?.pause() else vm.player?.play() }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) { 
                        Icon(imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp)) 
                    }
                }
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { t -> TrackItemUI(t, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, onQ: (String) -> Unit) {
    val res by vm.searchResults.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { onQ(it); vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск музыки...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray) }
        )
        LazyColumn { items(res) { t -> TrackItemUI(t, vm) } }
    }
}

@Composable
fun BottomNavUI(curr: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = curr=="home", onClick={onSelect("home")}, icon={Icon(imageVector = Icons.Default.Home, contentDescription = null)}, label={Text("Главная")})
        NavigationBarItem(selected = curr=="search", onClick={onSelect("search")}, icon={Icon(imageVector = Icons.Default.Search, contentDescription = null)}, label={Text("Поиск")})
        NavigationBarItem(selected = curr=="lib", onClick={onSelect("lib")}, icon={Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null)}, label={Text("Библиотека")})
    }
}
