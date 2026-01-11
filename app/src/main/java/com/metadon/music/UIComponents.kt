package com.metadon.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun MainAppScreen() {
    val vm: MusicViewModel = viewModel()
    val ctx = LocalContext.current
    
    val recTracks by vm.recTracks.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    
    val isConnected = vm.isConnected.value
    val isPlayerFull = vm.isPlayerFull.value
    val isPlaying = vm.isPlaying.value

    var tab by remember { mutableStateOf("home") }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.player = ExoPlayer.Builder(ctx).build()
        vm.connect()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!isPlayerFull) {
                Column {
                    currentTrack?.let { MiniPlayerUI(it, isPlaying, vm) }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p)) {
            when(tab) {
                "home" -> HomeTabUI(vm, recTracks)
                "search" -> SearchTabUI(vm, q, suggestions, searchResults) { q = it }
                "lib" -> LibraryTabUI(vm)
            }
        }
    }
    if (isPlayerFull) FullPlayerUI(currentTrack, isPlaying, vm)
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { TrackItemUI(it, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, sugs: List<String>, res: List<Track>, onQ: (String) -> Unit) {
    Column {
        TextField(
            value = q, onValueChange = { onQ(it); vm.getSuggestions(it); vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        if (res.isEmpty()) {
            LazyColumn { items(sugs) { Text(it, color = Color.Gray, modifier = Modifier.fillMaxWidth().clickable { onQ(it); vm.search(it) }.padding(16.dp)) } }
        } else {
            LazyColumn { items(res) { TrackItemUI(it, vm) } }
        }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val pls by vm.playlists.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
                listOf("Плейлисты", "Треки", "Альбомы", "Исполнители").forEach {
                    SuggestionChip(onClick = {}, label = { Text(it) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        item { 
            Row(Modifier.fillMaxWidth().clickable {}.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Column { Text("Понравившаяся музыка", color = Color.White); Text("Автоматический плейлист", color = Color.Gray, fontSize = 12.sp) }
            }
        }
        items(pls) { 
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Box(Modifier.size(56.dp).background(Color.DarkGray))
                Spacer(Modifier.width(16.dp))
                Text(it.name, color = Color.White)
            }
        }
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
        IconButton(onClick = { vm.toggleLike(t) }) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = Color.White) }
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(imageVector = Icons.Default.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                contentDescription = null, 
                tint = Color.White, 
                modifier = Modifier.size(32.dp).clickable {
                    if(isPlaying) vm.player?.pause() else vm.player?.play()
                    vm.isPlaying.value = !isPlaying
                }
            )
        }
    }
}

@Composable
fun FullPlayerUI(t: Track?, isPlaying: Boolean, vm: MusicViewModel) {
    if (t == null) return
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) { 
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)) 
            }
            Spacer(Modifier.height(40.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(48.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { 
                    Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(t.artist, color = Color.Gray, fontSize = 18.sp) 
                }
                Icon(imageVector = Icons.Outlined.AddCircleOutline, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.height(32.dp))
            Slider(value = 0.3f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("1:20", color = Color.Gray); Text("3:45", color = Color.Gray) }
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = Color.Gray)
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Surface(
                    Modifier.size(72.dp).clickable { 
                        if(isPlaying) vm.player?.pause() else vm.player?.play()
                        vm.isPlaying.value = !isPlaying
                    }, 
                    shape = CircleShape, 
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) { 
                        Icon(imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp)) 
                    }
                }
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Icon(imageVector = Icons.Default.Devices, contentDescription = null, tint = Color.White)
                Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun BottomNavUI(current: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = current=="home", onClick={onSelect("home")}, icon={Icon(imageVector = Icons.Default.Home, contentDescription = null)}, label={Text("Главная")})
        NavigationBarItem(selected = current=="search", onClick={onSelect("search")}, icon={Icon(imageVector = Icons.Default.Search, contentDescription = null)}, label={Text("Поиск")})
        NavigationBarItem(selected = current=="lib", onClick={onSelect("lib")}, icon={Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null)}, label={Text("Библиотека")})
    }
}
