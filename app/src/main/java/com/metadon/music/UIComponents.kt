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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun MainAppScreen() {
    val vm: MusicViewModel = viewModel()
    val recTracks by vm.recTracks.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    
    val isPlayerFull = vm.isPlayerFull.value
    val isPlaying = vm.isPlaying.value

    var tab by remember { mutableStateOf("home") }
    var q by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!isPlayerFull) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // МИНИПЛЕЕР С АНИМАЦИЕЙ
                    AnimatedVisibility(
                        visible = currentTrack != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        currentTrack?.let { MiniPlayerUI(it, isPlaying, vm) }
                    }
                    BottomNavUI(tab) { tab = it }
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when(tab) {
                "home" -> HomeTabUI(vm, recTracks)
                "search" -> SearchTabUI(vm, q) { q = it }
                "lib" -> LibraryTabUI(vm)
            }
        }
    }
    
    // ПОЛНОЭКРАННЫЙ ПЛЕЕР С АНИМАЦИЕЙ
    AnimatedVisibility(
        visible = isPlayerFull,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        FullPlayerUI(currentTrack, isPlaying, vm)
    }
}

@Composable
fun MiniPlayerUI(t: Track, isPlaying: Boolean, vm: MusicViewModel) {
    Card(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            Icon(imageVector = Icons.Default.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp).clickable {
                    if (isPlaying) vm.player?.pause() else vm.player?.play()
                    vm.isPlaying.value = !isPlaying
                }
            )
        }
    }
}

@Composable
fun FullPlayerUI(t: Track?, isPlaying: Boolean, vm: MusicViewModel) {
    if (t == null) return
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color.Black)) // Темный градиент
    )) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { vm.isPlayerFull.value = false }) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Трек", color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Видео", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Icon(imageVector = Icons.Default.Cast, contentDescription = null, tint = Color.White)
            }

            Spacer(Modifier.height(40.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            
            Spacer(Modifier.height(40.dp))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(t.artist, color = Color.White.copy(0.7f), fontSize = 18.sp)

            Spacer(Modifier.height(24.dp))
            // КНОПКИ ДЕЙСТВИЙ (Лайк, дизлайк и т.д.)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionBtn(Icons.Outlined.ThumbUp, "64")
                ActionBtn(Icons.Outlined.ThumbDown, "")
                ActionBtn(Icons.Outlined.PlaylistAdd, "Сохранить")
                ActionBtn(Icons.Outlined.Share, "24")
            }

            Spacer(Modifier.height(24.dp))
            // РАБОЧАЯ ПЕРЕМОТКА
            Slider(
                value = pos.toFloat(),
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                onValueChange = { vm.player?.seekTo(it.toLong()); vm.currentPos.value = it.toLong() },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.Gray, fontSize = 12.sp)
                Text(vm.formatTime(dur), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
            // УПРАВЛЕНИЕ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = Color.Gray)
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, Modifier.size(40.dp).clickable { vm.player?.seekToPrevious() })
                Surface(Modifier.size(72.dp).clickable { 
                    if (isPlaying) vm.player?.pause() else vm.player?.play()
                    vm.isPlaying.value = !isPlaying
                }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, Modifier.size(40.dp).clickable { vm.player?.seekToNext() })
                Icon(Icons.Default.Repeat, null, tint = Color.Gray)
            }

            Spacer(Modifier.weight(1f))
            // НИЖНИЕ ТАБЫ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Text("ДАЛЕЕ", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("ТЕКСТ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("ПОХОЖИЕ", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel, q: String, onQ: (String) -> Unit) {
    val res by vm.searchResults.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { onQ(it); vm.search(it); vm.getSuggestions(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Искать...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
        )
        LazyColumn { items(res) { TrackItemUI(it, vm) } }
    }
}

@Composable
fun BottomNavUI(current: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = current=="home", onClick={onSelect("home")}, icon={Icon(Icons.Default.Home,null)}, label={Text("Главная")})
        NavigationBarItem(selected = current=="search", onClick={onSelect("search")}, icon={Icon(Icons.Default.Search,null)}, label={Text("Поиск")})
        NavigationBarItem(selected = current=="lib", onClick={onSelect("lib")}, icon={Icon(Icons.Default.LibraryMusic,null)}, label={Text("Библиотека")})
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel, tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Главная", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(tracks) { TrackItemUI(it, vm) }
    }
}

@Composable
fun LibraryTabUI(vm: MusicViewModel) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Тут будут плейлисты", color = Color.Gray) }
}
