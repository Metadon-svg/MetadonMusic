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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val isPlaying = vm.isPlaying.value
    val isFull = vm.isPlayerFull.value

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
            if (!isFull) {
                Column(modifier = Modifier.fillMaxWidth()) {
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

    AnimatedVisibility(
        visible = isFull,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        FullPlayerUI(vm)
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = t.cover,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
            }
            IconButton(onClick = { 
                if (isPlaying) vm.player?.pause() else vm.player?.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyrics by vm.currentLyrics.collectAsState()
    val pos = vm.currentPos.longValue
    val dur = vm.totalDuration.longValue
    val isPlaying = vm.isPlaying.value
    var playerTab by remember { mutableStateOf("track") }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).pointerInput(Unit) { }) {
        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) {
                    Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Трек", color = if(playerTab=="track") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="track" })
                        Spacer(Modifier.width(16.dp))
                        Text("Текст", color = if(playerTab=="lyrics") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="lyrics" })
                    }
                }
                IconButton(onClick = { }) { Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White) }
            }

            Spacer(Modifier.height(40.dp))

            if (playerTab == "track") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(40.dp, RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(lyrics) { line ->
                        Text(
                            text = line.text,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            if (playerTab == "track") {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(t.artist, color = Color.White.copy(0.6f), fontSize = 18.sp)
                    }
                    IconButton(onClick = { vm.toggleLike(t) }) {
                        Icon(imageVector = Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(32.dp))

                Slider(
                    value = pos.toFloat(),
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    onValueChange = { vm.seekTo(it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(vm.formatTime(pos), color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text(vm.formatTime(dur), color = Color.White.copy(0.5f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(40.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null, tint = Color.White.copy(0.5f))
                    IconButton(onClick = { vm.player?.seekToPrevious() }) {
                        Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    Surface(
                        modifier = Modifier.size(82.dp).clickable { if(isPlaying) vm.player?.pause() else vm.player?.play() },
                        shape = CircleShape, color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = if(isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(42.dp))
                        }
                    }
                    IconButton(onClick = { vm.player?.seekToNext() }) {
                        Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    Icon(imageVector = Icons.Rounded.Repeat, contentDescription = null, tint = Color.White.copy(0.5f))
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Icon(imageVector = Icons.Rounded.Info, contentDescription = null, tint = Color.Gray)
                Icon(imageVector = Icons.Rounded.PlaylistPlay, contentDescription = null, tint = Color.White)
            }
        }
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray) }
        )
        LazyColumn {
            if (res.isEmpty()) {
                items(sugs) { s ->
                    Row(Modifier.fillMaxWidth().clickable { onQ(s); vm.search(s) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, color = Color.White)
                        Icon(imageVector = Icons.Default.ArrowOutward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                items(res) { t -> TrackRowUI(t, vm) }
            }
        }
    }
}

@Composable
fun TrackRowUI(t: Track, vm: MusicViewModel) {
    Row(Modifier.fillMaxWidth().clickable { vm.play(t) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp)) }
        items(tracks) { t -> TrackRowUI(t, vm) }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val favs by vm.favoriteTracks.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(favs) { t -> TrackRowUI(t, vm) }
    }
}

@Composable
fun BottomNavUI(curr: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        NavigationBarItem(selected = curr=="home", onClick={onSelect("home")}, icon={Icon(imageVector = Icons.Default.Home, contentDescription = null)}, label={Text("Главная")})
        NavigationBarItem(selected = curr=="search", onClick={onSelect("search")}, icon={Icon(imageVector = Icons.Default.Search, contentDescription = null)}, label={Text("Поиск")})
        NavigationBarItem(selected = curr=="lib", onClick={onSelect("lib")}, icon={Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null)}, label={Text("Библиотека")})
    }
}
