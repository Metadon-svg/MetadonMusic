package com.metadon.music

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.*
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
    val curTrack by vm.currentTrack.collectAsState()
    val isPlayerFull = vm.isPlayerFull.value
    var tab by remember { mutableStateOf("home") }

    LaunchedEffect(Unit) {
        vm.player = ExoPlayer.Builder(LocalContext.current).build()
        vm.connect()
        vm.startTimer()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!isPlayerFull) {
                Column {
                    // ПАРЯЩИЙ МИНИПЛЕЕР С АНИМАЦИЕЙ
                    AnimatedVisibility(
                        visible = curTrack != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        curTrack?.let { MiniPlayerUI(it, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when(tab) {
                "home" -> HomeTabUI(vm)
                "search" -> SearchTabUI(vm)
                "lib" -> LibraryTabUI(vm)
            }
        }
    }

    AnimatedVisibility(visible = isPlayerFull, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

@Composable
fun MiniPlayerUI(t: Track, vm: MusicViewModel) {
    val isPlaying = vm.isPlaying.value
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { if(isPlaying) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !isPlaying }) {
                Icon(imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val isLiked = vm.likedTracks.collectAsState().value.any { it.id == t.id }
    
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color.Black)))) {
        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(32.dp))
            
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(t.artist, color = Color.Gray, fontSize = 18.sp)
                }
                IconButton(onClick = { vm.toggleLike(t) }) {
                    Icon(imageVector = if(isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if(isLiked) Color.Red else Color.White)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Slider(
                value = vm.currentPos.value.toFloat(),
                valueRange = 0f..vm.totalDuration.value.toFloat().coerceAtLeast(1f),
                onValueChange = { vm.seekTo(it) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatTime(vm.currentPos.value), color = Color.Gray)
                Text(formatTime(vm.totalDuration.value), color = Color.Gray)
            }
            
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = Color.Gray)
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToPrevious() })
                Surface(Modifier.size(80.dp).clickable { if(vm.isPlaying.value) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !vm.isPlaying.value }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) { Icon(imageVector = if(vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(48.dp)) }
                }
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.player?.seekToNext() })
                Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel) {
    var q by remember { mutableStateOf("") }
    val sugs by vm.suggestions.collectAsState()
    val res by vm.searchResults.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { q = it; vm.suggest(it); vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray) }
        )
        if (res.isEmpty()) {
            LazyColumn { items(sugs) { Text(it, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { q = it; vm.search(it) }.padding(16.dp)) } }
        } else {
            LazyColumn { items(res) { TrackRow(it) { vm.play(it) } } }
        }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val favs by vm.likedTracks.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        item {
            Row(Modifier.fillMaxWidth().clickable {}.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Color.Red, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Column { Text("Любимые треки", color = Color.White, fontWeight = FontWeight.Bold); Text("${favs.size} треков", color = Color.Gray) }
            }
        }
        item { Text("Твои лайки", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(favs) { TrackRow(it) { vm.play(it) } }
    }
}

@Composable
fun TrackRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(16.dp))
        Column { Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(t.artist, color = Color.Gray) }
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

fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60
    val m = (ms / (1000 * 60)) % 60
    return "%02d:%02d".format(m, s)
}
