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
import androidx.compose.ui.graphics.vector.ImageVector // ДОБАВЛЕН ВАЖНЫЙ ИМПОРТ
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
    val searchResults by vm.searchResults.collectAsState()
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
                Column {
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

// --- ФУНКЦИЯ ДЛЯ ПУНКТОВ МЕНЮ (ИСПРАВЛЕНА) ---
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

// --- ПОЛНЫЙ ПЛЕЕР С МЕНЮ И ТЕКСТОМ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value
    val isLiked = t.isLiked
    val isPlaying = vm.isPlaying.value
    
    // Состояние для меню
    var showMenu by remember { mutableStateOf(false) }
    // Состояние для табов (Трек/Текст)
    var playerTab by remember { mutableStateOf("track") }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                MenuItem(Icons.Default.Download, "Скачать")
                MenuItem(Icons.Default.PlaylistAdd, "Добавить в плейлист")
                MenuItem(Icons.Default.QueueMusic, "Играть далее")
                MenuItem(Icons.Default.Timer, "Таймер сна")
                MenuItem(Icons.Default.Share, "Поделиться")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        // Фоновый блюр
        AsyncImage(
            model = t.cover, 
            contentDescription = null, 
            modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f), 
            contentScale = ContentScale.Crop
        )
        
        // Затемнение фона
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)))

        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Верхняя панель
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                
                // Переключатель Трек / Текст
                Surface(color = Color.Black.copy(0.3f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (playerTab == "track") Color.White.copy(0.1f) else Color.Transparent)
                                .clickable { playerTab = "track" }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Трек", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (playerTab == "lyrics") Color.White.copy(0.1f) else Color.Transparent)
                                .clickable { playerTab = "lyrics" }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Текст", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, null, tint = Color.White)
                }
            }

            Spacer(Modifier.height(32.dp))

            if (playerTab == "track") {
                // ОБЛОЖКА
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(50.dp, spotColor = Color.Black)
                ) {
                    AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                
                Spacer(Modifier.weight(1f))
                
                // ИНФО И ЛАЙК
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = Color.White.copy(0.7f), fontSize = 18.sp, maxLines = 1)
                    }
                    IconButton(onClick = { vm.toggleLike(t) }) {
                        Icon(
                            imageVector = if (t.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (t.isLiked) Color.White else Color.White.copy(0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // СЛАЙДЕР
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
                    Icon(Icons.Rounded.Shuffle, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(28.dp))
                    IconButton(onClick = { vm.prev() }) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    
                    Surface(
                        modifier = Modifier.size(80.dp).clickable { if (isPlaying) vm.player?.pause() else vm.player?.play() },
                        shape = CircleShape, color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                        }
                    }

                    IconButton(onClick = { vm.next() }) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    Icon(Icons.Rounded.Repeat, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(28.dp))
                }
            } else {
                // ЭКРАН ТЕКСТА
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Текст песни будет здесь", color = Color.White.copy(0.8f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(30.dp))
            
            // НИЖНИЕ ТАБЫ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("ДАЛЕЕ", color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("ТЕКСТ", color = if (playerTab == "lyrics") Color.White else Color.White.copy(0.5f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("ПОХОЖИЕ", color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    // Парящий миниплеер с градиентом от обложки
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF252525)) // Резервный цвет
            .clickable { vm.isPlayerFull.value = true }
    ) {
        // Фоновая картинка с блюром для цвета
        AsyncImage(
            model = t.cover,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.6f),
            contentScale = ContentScale.Crop
        )
        
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.White.copy(0.7f), fontSize = 13.sp, maxLines = 1)
            }
            IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
                listOf("Всё", "Релакс", "Сон", "Энергия").forEach {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(it, color = Color.White) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF222222)),
                        border = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        item { Text("Слушать снова", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { t -> TrackRowUI(t, vm) }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    val favs by vm.likedTracks.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Библиотека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        }
        item {
            // Кнопка Понравившиеся
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
            // Кнопка Создать
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White).clickable {}, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Новый плейлист", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        items(favs) { t -> TrackRowUI(t, vm) }
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
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
        )
        LazyColumn {
            if (res.isEmpty() && q.isNotEmpty()) {
                items(sugs) { s ->
                    Row(Modifier.fillMaxWidth().clickable { onQ(s); vm.search(s) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, color = Color.White, fontSize = 16.sp)
                        // ЗАМЕНИЛИ AutoMirrored на обычную стрелку
                        Icon(Icons.Default.ArrowForward, null, tint = Color.Gray)
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
    Row(Modifier.fillMaxWidth().clickable { vm.play(t) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
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
