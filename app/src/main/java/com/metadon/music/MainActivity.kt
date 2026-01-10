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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val status = remember { AudioEngine.getEngineStatus() }
    
    Scaffold(
        bottomBar = { BottomNav() }
    ) { p ->
        Column(modifier = Modifier.padding(p).verticalScroll(rememberScrollState())) {
            Header()
            Text(status, color = Color.Green, modifier = Modifier.padding(16.dp))
            Categories()
            RecommendationList()
        }
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
            MiniPlayer()
        }
    }
}

@Composable
fun Header() {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Music", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Row {
            Icon(Icons.Default.Search, null, tint = Color.White)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(32.dp).background(Color.Gray, RoundedCornerShape(16.dp)))
        }
    }
}

@Composable
fun Categories() {
    val chips = listOf("Релакс", "Вечеринка", "Заряд энергии", "Веселая")
    LazyRow(contentPadding = PaddingValues(16.dp)) {
        items(chips) { chip ->
            AssistChip(
                onClick = {},
                label = { Text(chip) },
                modifier = Modifier.padding(end = 8.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.DarkGray, labelColor = Color.White)
            )
        }
    }
}

@Composable
fun RecommendationList() {
    val tracks = listOf("Женщина, я не танцую", "18 мне уже", "это путин виноват")
    Column(Modifier.padding(16.dp)) {
        Text("Рекомендации", style = MaterialTheme.typography.titleLarge, color = Color.White)
        tracks.forEach { title ->
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).background(Color.DarkGray).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White)
                    Text("Исполнитель", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.MoreVert, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun MiniPlayer() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color.Gray))
            Spacer(Modifier.width(12.dp))
            Text("Текущий трек...", color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}

@Composable
fun BottomNav() {
    NavigationBar(containerColor = Color.Black) {
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Библиотека") })
    }
}