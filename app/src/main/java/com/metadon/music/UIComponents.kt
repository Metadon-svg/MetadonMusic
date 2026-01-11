package com.metadon.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var q by remember { mutableStateOf("") }

    // Подключаемся автоматически при старте
    LaunchedEffect(Unit) {
        vm.player = ExoPlayer.Builder(ctx).build()
        vm.connect()
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Column {
                vm.currentTrack.collectAsState().value?.let { MiniPlayerUI(it, vm) }
                BottomNavigationBarUI()
            }
        }
    ) { p ->
        Column(Modifier.padding(p)) {
            // Логотип сверху
            Text("Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            
            // Поиск
            TextField(
                value = q, onValueChange = { q = it; vm.search(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Поиск песен...") },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
            )

            // Список песен
            LazyColumn {
                val list = if (q.isEmpty()) vm.recTracks.collectAsState().value else vm.searchResults.collectAsState().value
                items(list) { track ->
                    TrackItemUI(track) { vm.play(track) }
                }
            }
        }
    }

    if (vm.isPlayerFull.value) FullPlayerUI(vm)
}

@Composable
fun TrackItemUI(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(t.artist, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun MiniPlayerUI(t: Track, vm: MusicViewModel) {
    Surface(Modifier.fillMaxWidth().height(64.dp).clickable { vm.isPlayerFull.value = true }, color = Color(0xFF1A1A1A)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(t.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { 
                if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play()
                vm.isPlaying.value = !vm.isPlaying.value 
            }) {
                Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerUI(vm: MusicViewModel) {
    val t = vm.currentTrack.collectAsState().value ?: return
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.isPlayerFull.value = false }, Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(40.dp))
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)))
            Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Text(t.artist, color = Color.Gray, fontSize = 18.sp)
            Spacer(Modifier.height(40.dp))
            Button(onClick = { 
                if (vm.isPlaying.value) vm.player?.pause() else vm.player?.play()
                vm.isPlaying.value = !vm.isPlaying.value 
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text(if (vm.isPlaying.value) "Пауза" else "Играть", color = Color.Black)
            }
        }
    }
}

@Composable
fun BottomNavigationBarUI() {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
    }
}
