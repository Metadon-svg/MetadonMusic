package com.metadon.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*

class MusicViewModel : ViewModel() {
    private val baseUrl = "https://amhub.serveousercontent.com" // ЗАМЕНИ НА СВОЙ
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    // Данные
    val homeData = MutableStateFlow<Map<String, List<Track>>>(emptyMap()) // Для каруселей
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Загрузка...")

    // Состояния
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(true) // Для скелетона
    
    // Время
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)

    // Очередь воспроизведения
    private var queue: List<Track> = emptyList()

    fun connect() {
        val wsUrl = baseUrl.replace("http", "ws").removeSuffix("/") + "/ws"
        val request = Request.Builder().url(wsUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.value = true
                webSocket.send("{\"type\": \"INIT_HOME\"}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    when (json.get("type").asString) {
                        "INIT_HOME" -> {
                            val data = json.getAsJsonObject("data")
                            val rec = parseTracks(data.getAsJsonArray("rec"))
                            val new = parseTracks(data.getAsJsonArray("new"))
                            // Собираем данные для главной
                            homeData.value = mapOf("Рекомендации" to rec, "Новинки" to new)
                            isLoading.value = false // Выключаем скелетон
                        }
                        "SEARCH_RES" -> searchResults.value = parseTracks(json.getAsJsonArray("data"))
                        "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        })
    }

    private fun parseTracks(arr: com.google.gson.JsonArray): List<Track> {
        return arr.map {
            val o = it.asJsonObject
            Track(o.get("id").asString, o.get("title").asString, o.get("artist").asString, o.get("thumb").asString)
        }
    }

    fun startTimer() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                player?.let {
                    if (it.isPlaying) {
                        currentPos.value = it.currentPosition
                        totalDuration.value = it.duration.coerceAtLeast(0L)
                    }
                }
                delay(1000)
            }
        }
    }

    // Обновленный метод play: принимает список для очереди
    fun play(t: Track, contextList: List<Track> = emptyList()) {
        if (contextList.isNotEmpty()) {
            queue = contextList
        }
        currentTrack.value = t
        isPlaying.value = true
        
        // Запускаем стрим
        CoroutineScope(Dispatchers.IO).launch {
            val streamUrl = "$baseUrl/stream/${t.id}"
            val lyricUrl = "$baseUrl/lyrics/${t.id}"
            // Загрузка текста (простая реализация через http get, если сервер поддерживает, или через ws)
            // Пока просто заглушка или можно добавить запрос к серверу
            withContext(Dispatchers.Main) {
                player?.setMediaItem(MediaItem.fromUri(streamUrl))
                player?.prepare()
                player?.play()
            }
        }
    }

    // Логика переключения треков
    fun next() {
        if (queue.isEmpty()) return
        val current = currentTrack.value
        val index = queue.indexOf(current)
        if (index != -1 && index < queue.size - 1) {
            play(queue[index + 1])
        }
    }

    fun prev() {
        if (queue.isEmpty()) return
        val current = currentTrack.value
        val index = queue.indexOf(current)
        if (index > 0) {
            play(queue[index - 1])
        }
    }

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.value = pos.toLong()
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }

    fun search(q: String) { if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }
    fun getSuggestions(q: String) { if(q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}") }
    fun toggleLike(t: Track) { /* Логика лайка */ }
}
