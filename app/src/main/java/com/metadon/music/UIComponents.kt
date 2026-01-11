package com.metadon.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
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
    val homeData by vm.homeData.collectAsState()
    val isPlaying = vm.isPlaying.value
    val isFull = vm.isPlayerFull.value
    val loading = vm.isLoading.value

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
                Column(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = curTrack != null) {
                        curTrack?.let { MiniPlayerUI(it, isPlaying, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when (tab) {
                "home" -> HomeTabUI(vm, homeData, loading)
                "search" -> SearchTabUI(vm, q) { q = it }
                "lib" -> LibraryTabUI(vm)
            }
        }
    }

    AnimatedVisibility(visible = isFull, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

// --- СКЕЛЕТОН ЗАГРУЗКИ ---
@Composable
fun ShimmerItem() {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(0f, 1000f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart))
    val brush = Brush.linearGradient(
        colors = listOf(Color.DarkGray.copy(0.3f), Color.LightGray.copy(0.2f), Color.DarkGray.copy(0.3f)),
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
    Column(Modifier.padding(8.dp)) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(brush))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(100.dp).height(12.dp).background(brush))
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, data: Map<String, List<Track>>, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        
        if (loading) {
            item { LazyRow { items(5) { ShimmerItem() } } }
            item { LazyRow { items(5) { ShimmerItem() } } }
        } else {
            data.forEach { (title, tracks) ->
                item { Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                        items(tracks) { t ->
                            Column(Modifier.width(160.dp).padding(8.dp).clickable { vm.play(t, tracks) }) {
                                AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(144.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Text(t.title, color = Color.White, maxLines = 1, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                                Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    // Горизонтальный градиент от обложки
    val gradient = Brush.horizontalGradient(listOf(Color(0xFF2C2C2C), Color(0xFF121212)))
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.background(gradient)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyrics by vm.currentLyrics.collectAsState()
    val isPlaying = vm.isPlaying.value
    var playerTab by remember { mutableStateOf("track") }
    var showMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit){}) {
        // --- ФОН: РАЗМЫТАЯ ФОТКА С ЯРКОСТЬЮ ---
        AsyncImage(
            model = t.cover, contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.4f),
            contentScale = ContentScale.Crop
        )

        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Header
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Трек", color = if(playerTab=="track") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="track" })
                        Spacer(Modifier.width(16.dp))
                        Text("Текст", color = if(playerTab=="lyrics") Color.White else Color.Gray, modifier = Modifier.clickable { playerTab="lyrics" })
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1A1A1A))) {
                        DropdownMenuItem(text = { Text("LRClib", color = Color.White) }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("YTMusic расшифровка", color = Color.White) }, onClick = { showMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Main Content
            if (playerTab == "track") {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(40.dp, RoundedCornerShape(24.dp))) {
                    AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(48.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text(t.artist, color = Color.White.copy(0.6f), fontSize = 18.sp)
                    }
                    IconButton(onClick = { vm.toggleLike(t) }) { Icon(Icons.Rounded.FavoriteBorder, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                }
            } else {
                // --- ТЕКСТ КАК В ЮТУБ МУЗЫКЕ ---
                LazyColumn(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    items(lyrics) { line ->
                        Text(
                            text = line.text,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp,
                            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
                        )
                    }
                }
            }

            // Bottom Controls (always visible or mostly)
            if (playerTab == "track") {
                Spacer(Modifier.height(24.dp))
                Slider(
                    value = vm.currentPos.value.toFloat(),
                    valueRange = 0f..vm.totalDuration.value.toFloat().coerceAtLeast(1f),
                    onValueChange = { vm.seekTo(it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(vm.formatTime(vm.currentPos.value), color = Color.White.copy(0.5f)); Text(vm.formatTime(vm.totalDuration.value), color = Color.White.copy(0.5f))
                }
                Spacer(Modifier.height(40.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shuffle, null, tint = Color.White.copy(0.5f))
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.prev() })
                    Surface(Modifier.size(80.dp).clickable { if(isPlaying) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !isPlaying }, shape = CircleShape, color = Color.White) {
                        Box(contentAlignment = Alignment.Center) { Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(48.dp)) }
                    }
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.next() })
                    Icon(Icons.Rounded.Repeat, null, tint = Color.White.copy(0.5f))
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Icon(Icons.Rounded.Info, null, tint = Color.Gray)
                Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White)
            }
        }
    }
}

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
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
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
        NavigationBarItem(selected = curr=="home", onClick={onSelect("home")}, icon={Icon(Icons.Default.Home, null)}, label={Text("Главная")})
        NavigationBarItem(selected = curr=="search", onClick={onSelect("search")}, icon={Icon(Icons.Default.Search, null)}, label={Text("Поиск")})
        NavigationBarItem(selected = curr=="lib", onClick={onSelect("lib")}, icon={Icon(Icons.Default.LibraryMusic, null)}, label={Text("Библиотека")})
    }
}
