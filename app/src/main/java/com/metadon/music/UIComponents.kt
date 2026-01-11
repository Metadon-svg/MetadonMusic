package com.metadon.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
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
import androidx.media3.common.Player

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
                addListener(object : Player.Listener {
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
                    AnimatedVisibility(visible = curTrack != null, enter = fadeIn() + slideInVertically { it }) {
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

// --- ДИЗАЙН SIMPMUSIC HOME ---
@Composable
fun HomeTabUI(vm: MusicViewModel, data: Map<String, List<Track>>, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // Верхние чипсы
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp)) {
                listOf("Все", "Релакс", "Сон", "Тренировка").forEach { 
                    SuggestionChip(
                        onClick = {}, 
                        label = { Text(it, color = Color.White) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1A1A1A)),
                        border = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        
        if (loading) {
            items(3) { 
                Column(Modifier.padding(16.dp)) {
                    Box(Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray))
                    Spacer(Modifier.height(12.dp))
                    LazyRow { items(3) { ShimmerItem() } } 
                }
            }
        } else {
            data.forEach { (title, tracks) ->
                item { 
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)) 
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(tracks) { t ->
                            Column(Modifier.width(150.dp).padding(end = 16.dp).clickable { vm.play(t) }) {
                                AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Text(t.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                                Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// --- СКЕЛЕТОН ---
@Composable
fun ShimmerItem() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(0.3f, 0.6f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse))
    Box(Modifier.padding(end = 16.dp).size(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.Gray.copy(alpha)))
}

// --- МИНИ-ПЛЕЕР (ЦВЕТНОЙ) ---
@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { vm.isPlayerFull.value = true }
    ) {
        // Задний фон - размытая картинка
        AsyncImage(
            model = t.cover, contentDescription = null, 
            modifier = Modifier.fillMaxSize().blur(50.dp), 
            contentScale = ContentScale.Crop
        )
        // Затемнение
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))
        
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                Text(t.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

// --- ПОЛНЫЙ ПЛЕЕР (С МЕНЮ) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value
    val isLiked = t.isLiked
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(Modifier.padding(16.dp)) {
                MenuItem(Icons.Default.Download, "Скачать")
                MenuItem(Icons.Default.PlaylistAdd, "Добавить в плейлист")
                MenuItem(Icons.Default.QueueMusic, "Играть далее")
                MenuItem(Icons.Default.LibraryAdd, "Добавить в очередь")
                MenuItem(Icons.Default.Person, "Исполнители")
                MenuItem(Icons.Default.Timer, "Таймер сна")
                MenuItem(Icons.Default.Share, "Поделиться")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        // Фоновый градиент от обложки
        AsyncImage(model = t.cover, null, Modifier.fillMaxSize().blur(100.dp).alpha(0.5f), contentScale = ContentScale.Crop)
        
        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            
            Spacer(Modifier.weight(1f)) // ТОЛКАЕМ ВНИЗ
            
            AsyncImage(model = t.cover, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).shadow(30.dp))
            
            Spacer(Modifier.height(40.dp))
            
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(t.artist, color = Color.LightGray, fontSize = 18.sp, maxLines = 1)
                }
                // ЛАЙК С ЗАПОЛНЕНИЕМ
                IconButton(onClick = { vm.toggleLike(t) }) {
                    Icon(
                        imageVector = if(isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if(isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Slider(
                value = pos.toFloat(),
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                onValueChange = { vm.seekTo(it) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.LightGray, fontSize = 12.sp)
                Text(vm.formatTime(dur), color = Color.LightGray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            // КНОПКИ УПРАВЛЕНИЯ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleRepeat() }) { 
                    Icon(Icons.Rounded.Repeat, null, tint = if(vm.repeatMode.value > 0) Color.White else Color.Gray) 
                }
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                
                Surface(Modifier.size(72.dp).clickable { if(vm.isPlaying.value) vm.player?.pause() else vm.player?.play() }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) { 
                        Icon(if(vm.isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp)) 
                    }
                }
                
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }
            }
            
            Spacer(Modifier.height(40.dp))
            
            // ТАБЫ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("ДАЛЕЕ", color = Color.Gray, fontWeight = FontWeight.Bold)
                Text("ТЕКСТ", color = Color.White, fontWeight = FontWeight.Bold)
                Text("ПОХОЖИЕ", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().clickable {}.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

// --- БИБЛИОТЕКА ---
@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val favs by vm.likedTracks.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
                listOf("Плейлисты", "Треки", "Альбомы", "Исполнители").forEach {
                    SuggestionChip(onClick = {}, label = { Text(it, color = Color.White) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1A1A1A)), border = null, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable {}.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF6441A5), Color(0xFF2a0845))), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ThumbUp, null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Понравившаяся музыка", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Автоматический плейлист", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White).clickable {}, contentAlignment = Alignment.Center) {
                Row { Icon(Icons.Default.Add, null, tint = Color.Black); Text("Создать", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
        items(favs) { TrackRowUI(it, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, onQ: (String) -> Unit) {
    val res by vm.searchResults.collectAsState()
    val sugs by vm.suggestions.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { onQ(it); vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            trailingIcon = { if(q.isNotEmpty()) Icon(Icons.Default.Close, null, Modifier.clickable { onQ("") }, tint = Color.White) }
        )
        LazyColumn {
            if (res.isEmpty() && q.isNotEmpty()) {
                items(sugs) { s ->
                    Row(Modifier.fillMaxWidth().clickable { onQ(s); vm.search(s) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, color = Color.White, fontSize = 16.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowOutward, null, tint = Color.Gray)
                    }
                }
            } else {
                items(res) { TrackRowUI(it, vm) }
            }
        }
    }
}

@Composable
fun TrackRowUI(t: Track, vm: MusicViewModel) {
    Row(Modifier.fillMaxWidth().clickable { vm.play(t) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
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
