@file:OptIn(ExperimentalMaterial3Api::class)

package com.metadon.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val isLoading = vm.isLoading.value
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
                Column(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = curTrack != null, enter = fadeIn() + slideInVertically { it / 2 }) {
                        curTrack?.let { MiniPlayerUI(it, isPlaying, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when (tab) {
                "home" -> HomeTabUI(vm, homeData, isLoading)
                "search" -> SearchTabUI(vm, q) { q = it }
                "lib" -> LibraryTabUI(vm)
            }
        }
    }

    AnimatedVisibility(visible = isFull, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

// --- ПОИСК (ТОЛЬКО ПО ENTER) ---
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, onQ: (String) -> Unit) {
    val res by vm.searchResults.collectAsState()
    val sugs by vm.suggestions.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, 
            onValueChange = { 
                onQ(it)
                vm.suggest(it) // Подсказки показываем сразу
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Поиск...") },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            singleLine = true,
            // ГЛАВНОЕ ИЗМЕНЕНИЕ: Поиск только по Enter
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.search(q)
                keyboardController?.hide()
            })
        )
        LazyColumn {
            // Если есть подсказки и нет результатов поиска
            if (res.isEmpty() && q.isNotEmpty()) {
                items(sugs) { s ->
                    Row(Modifier.fillMaxWidth().clickable { onQ(s); vm.search(s); keyboardController?.hide() }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, color = Color.White, fontSize = 16.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray)
                    }
                }
            } else {
                items(res) { t -> TrackRowUI(t, vm) }
            }
        }
    }
}

// --- ПОЛНЫЙ ПЛЕЕР (ДИЗАЙН ИЗ СКРИНШОТА) ---
@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val lyrics by vm.lyricsText.collectAsState()
    val pos = vm.currentPos.longValue
    val dur = vm.totalDuration.longValue
    val isPlaying = vm.isPlaying.value
    val isLiked = t.isLiked
    
    var playerTab by remember { mutableStateOf("track") }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).pointerInput(Unit){}) {
        // Фон - размытая обложка
        AsyncImage(model = t.cover, null, Modifier.fillMaxSize().blur(100.dp).alpha(0.6f), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))

        Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Верх
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                
                // Переключатель Трек/Видео (как на скрине)
                Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Text("Трек", color = if(playerTab=="track") Color.White else Color.Gray, modifier = Modifier.clickable{playerTab="track"}.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Видео", color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                IconButton(onClick = {}) { Icon(Icons.Default.Cast, null, tint = Color.White) }
            }

            Spacer(Modifier.height(30.dp))

            if (playerTab == "track") {
                // Обложка
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(30.dp)) {
                    AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(40.dp))
                
                // Название и Автор
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(t.artist, color = Color.White.copy(0.7f), fontSize = 18.sp, maxLines = 1)
                }

                Spacer(Modifier.height(20.dp))

                // РЯД КНОПОК: Лайк | Дизлайк | Сохранить | Поделиться
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    // Кнопка Лайка (Белая!)
                    IconButton(onClick = { vm.toggleLike(t) }) {
                        Icon(
                            if(isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp, 
                            null, 
                            tint = Color.White, // ВСЕГДА БЕЛАЯ
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.ThumbDown, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    
                    // Кнопка Сохранить (с текстом)
                    Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сохранить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                }

                Spacer(Modifier.height(24.dp))

                // Слайдер
                Slider(
                    value = pos.toFloat(),
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    onValueChange = { vm.seekTo(it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                )
                Row(Modifier.fillMaxWidth().padding(top = 0.dp), Arrangement.SpaceBetween) {
                    Text(vm.formatTime(pos), color = Color.Gray, fontSize = 12.sp)
                    Text(vm.formatTime(dur), color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))

                // Контроллеры
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    IconButton(onClick = { vm.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, null, tint = if(vm.isShuffle.value) Color.White else Color.Gray) }
                    IconButton(onClick = { vm.prev() }, modifier = Modifier.size(50.dp)) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    
                    Surface(modifier = Modifier.size(72.dp).clickable { if(isPlaying) vm.player?.pause() else vm.player?.play() }, shape = CircleShape, color = Color.White) {
                        Box(contentAlignment = Alignment.Center) { Icon(if(isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp)) }
                    }
                    
                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(50.dp)) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = { vm.toggleRepeat() }) { Icon(if(vm.repeatMode.value == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if(vm.repeatMode.value > 0) Color.White else Color.Gray) }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            // Нижние табы (ДАЛЕЕ / ТЕКСТ / ПОХОЖИЕ)
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceAround) {
                Text("ДАЛЕЕ", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("ТЕКСТ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("ПОХОЖИЕ", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ... ОСТАЛЬНЫЕ КОМПОНЕНТЫ (Home, Library, MiniPlayer) ОСТАЮТСЯ ТАКИМИ ЖЕ ...
@Composable
fun ShimmerItem() {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(0f, 1000f, infiniteRepeatable(tween(1200), RepeatMode.Restart))
    val brush = Brush.linearGradient(listOf(Color.DarkGray.copy(0.3f), Color.LightGray.copy(0.2f), Color.DarkGray.copy(0.3f)), start = Offset(translateAnim - 500f, translateAnim - 500f), end = Offset(translateAnim, translateAnim))
    Box(Modifier.padding(end = 16.dp).size(150.dp).clip(RoundedCornerShape(12.dp)).background(brush))
}

@Composable
fun HomeTabUI(vm: MusicViewModel, data: Map<String, List<Track>>, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
                listOf("Всё", "Релакс", "Сон", "Энергия").forEach {
                    SuggestionChip(onClick = {}, label = { Text(it, color = Color.White) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1E1E1E)), border = null, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        if (loading) {
            item { LazyRow(contentPadding = PaddingValues(16.dp)) { items(3) { ShimmerItem() } } }
        } else {
            data.forEach { (title, tracks) ->
                item { Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        items(tracks) { t ->
                            Column(Modifier.width(150.dp).padding(end = 16.dp).clickable { vm.play(t, tracks) }) {
                                AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.height(8.dp))
                                Text(t.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
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
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = Color.Gray)
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.isPlayerFull.value = true }) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxSize().blur(50.dp).alpha(0.7f), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)))
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { if (isPlaying) vm.player?.pause() else vm.player?.play() }) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
        }
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
