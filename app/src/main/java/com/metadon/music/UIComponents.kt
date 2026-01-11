package com.metadon.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
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
    val ctx = LocalContext.current
    val curTrack by vm.currentTrack.collectAsState()
    val isFull = vm.isPlayerFull.value

    // Запрос уведомлений для управления в шторке
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.player = ExoPlayer.Builder(ctx).build()
        vm.connect()
        vm.startTimer()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (!isFull) {
                Column {
                    AnimatedVisibility(visible = curTrack != null) {
                        curTrack?.let { MiniPlayerUI(it, vm) }
                    }
                    BottomNavUI(vm)
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            HomeTabUI(vm)
        }
    }

    AnimatedVisibility(visible = isFull, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        FullPlayerUI(vm)
    }
}

@Composable
fun HomeTabUI(vm: MusicViewModel) {
    val data by vm.homeData.collectAsState()
    val loading = vm.isLoading.value

    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Музыка", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        
        if (loading) {
            items(5) { SkeletonItem() }
        } else {
            data.forEach { (title, tracks) ->
                item { Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(tracks) { t ->
                            Column(Modifier.width(140.dp).padding(end = 12.dp).clickable { vm.play(t, tracks) }) {
                                AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Text(t.title, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
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
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    val pos = vm.currentPos.value
    val dur = vm.totalDuration.value
    var showQueue by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(Modifier.padding(24.dp).fillMaxSize()) {
            IconButton(onClick = { vm.isPlayerFull.value = false }) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            
            Spacer(Modifier.weight(0.2f)) // ПРИПОДНЯЛИ ОБЛОЖКУ
            
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            
            Spacer(Modifier.weight(0.1f))

            // ТЕКСТ ОПУЩЕН НИЖЕ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(t.artist, color = Color.Gray, fontSize = 18.sp)
                }
                Icon(Icons.Default.FavoriteBorder, null, tint = Color.White, Modifier.size(28.dp))
            }

            Spacer(Modifier.height(32.dp))

            // ПРОГРЕСС
            Slider(value = pos.toFloat(), valueRange = 0f..dur.toFloat().coerceAtLeast(1f), onValueChange = { vm.seekTo(it) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(vm.formatTime(pos), color = Color.Gray); Text(vm.formatTime(dur), color = Color.Gray)
            }

            Spacer(Modifier.height(24.dp))

            // УПРАВЛЕНИЕ (В САМОМ НИЗУ)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, null, tint = if(vm.isShuffle.value) Color.Red else Color.Gray) }
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.prev() })
                
                Surface(Modifier.size(80.dp).clickable { if(vm.isPlaying.value) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !vm.isPlaying.value }, shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) { Icon(if(vm.isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(48.dp)) }
                }

                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp).clickable { vm.next() })
                IconButton(onClick = { vm.toggleRepeat() }) { Icon(Icons.Rounded.Repeat, null, tint = if(vm.repeatMode.value > 0) Color.Red else Color.Gray) }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // НИЖНИЕ КНОПКИ
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Icon(Icons.Default.Info, null, tint = Color.Gray)
                Icon(Icons.Default.PlaylistPlay, null, tint = Color.White, modifier = Modifier.clickable { showQueue = true })
            }
        }
    }
    
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Color(0xFF1A1A1A)) {
            LazyColumn(Modifier.fillMaxHeight(0.6f).padding(16.dp)) {
                item { Text("Очередь", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(vm.queue.collectAsState().value) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = t.cover, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
                        Text(t.title, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonItem() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(0.2f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))
    Row(Modifier.fillMaxWidth().padding(16.dp).alpha(alpha)) {
        Box(Modifier.size(56.dp).background(Color.Gray, RoundedCornerShape(8.dp)))
        Column(Modifier.padding(start = 16.dp)) {
            Box(Modifier.width(150.dp).height(14.dp).background(Color.Gray))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(100.dp).height(12.dp).background(Color.Gray))
        }
    }
}

@Composable
fun MiniPlayerUI(t: Track, vm: MusicViewModel) {
    Card(
        modifier = Modifier.padding(12.dp).fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play(); vm.isPlaying.value = !vm.isPlaying.value }) {
                Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun BottomNavUI(vm: MusicViewModel) {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = true, onClick = {}, icon = {Icon(Icons.Default.Home, null)}, label = {Text("Главная")})
        NavigationBarItem(selected = false, onClick = {}, icon = {Icon(Icons.Default.Explore, null)}, label = {Text("Навигатор")})
        NavigationBarItem(selected = false, onClick = {}, icon = {Icon(Icons.Default.LibraryMusic, null)}, label = {Text("Библиотека")})
    }
}
