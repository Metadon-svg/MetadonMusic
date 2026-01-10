package com.metadon.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Темная тема как в YT Music
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    Scaffold(
        containerColor = Color.Black,
        bottomBar = { BottomNav() }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Основной контент
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TopBar()
                CategoryChips()
                
                Text(
                    "Здравствуйте, Metadon!",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                RecommendationsSection()
                Spacer(Modifier.height(100.dp)) // Чтобы плеер не перекрывал контент
            }

            // Плавающий мини-плеер
            Box(Modifier.align(Alignment.BottomCenter)) {
                MiniPlayer()
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircleFilled, "Logo", tint = Color.Red, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text("Music", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Row {
            Icon(Icons.Default.Cast, null, tint = Color.White)
            Spacer(Modifier.width(20.dp))
            Icon(Icons.Default.Notifications, null, tint = Color.White)
            Spacer(Modifier.width(20.dp))
            Icon(Icons.Default.Search, null, tint = Color.White)
            Spacer(Modifier.width(20.dp))
            Box(Modifier.size(28.dp).background(Color.Cyan, RoundedCornerShape(14.dp)))
        }
    }
}

@Composable
fun CategoryChips() {
    val chips = listOf("Релакс", "Вечеринка", "Заряд энергии", "Веселая", "Романтика")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(chips) { chip ->
            Surface(
                modifier = Modifier.padding(end = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.DarkGray.copy(alpha = 0.5f)
            ) {
                Text(chip, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun RecommendationsSection() {
    val tracks = listOf(
        TrackData("Женщина, я не танцую", "Костюшкин Стас", "https://i.ytimg.com/vi/placeholder1.jpg"),
        TrackData("18 мне уже", "Руки Вверх!", "https://i.ytimg.com/vi/placeholder2.jpg"),
        TrackData("это путин виноват", "mvlancore", "https://i.ytimg.com/vi/placeholder3.jpg"),
        TrackData("КАЖДЫЙ ДЕНЬ", "Nomad Punk", "https://i.ytimg.com/vi/placeholder4.jpg")
    )

    Column(Modifier.padding(16.dp)) {
        tracks.forEach { track ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.cover,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(track.artist, color = Color.Gray, fontSize = 14.sp)
                }
                Icon(Icons.Default.MoreVert, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun MiniPlayer() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = Color(0xFF212121),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(Color.Gray, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Женщина, я не танцую", color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text("Костюшкин Стас", color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.Cast, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun BottomNav() {
    NavigationBar(containerColor = Color(0xFF0F0F0F)) {
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Сэмплы") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Навигатор") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Библиотека") })
    }
}

data class TrackData(val title: String, val artist: String, val cover: String)
