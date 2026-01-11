package com.metadon.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*

class MusicViewModel : ViewModel() {
    private val baseUrl = "https://amhub.serveousercontent.com" // ВПИШИ СЮДА СВОЙ IP
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    // Данные
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Загрузка текста...")

    // Состояния UI
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)
    
    // Режимы воспроизведения
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0)

    // Очередь воспроизведения
    private var queue: List<Track> = emptyList()

    fun connect() {
        val wsUrl = baseUrl.replace("http", "ws").removeSuffix("/") + "/ws"
        val request = Request.Builder().url(wsUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.value = true
                webSocket.send("{\"type\": \"INIT_HOME\"}")
                webSocket.send("{\"type\": \"GET_FAV\", \"user_id\": 1}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    when (json.get("type").asString) {
                        "INIT_HOME" -> {
                            val data = json.getAsJsonObject("data")
                            recTracks.value = parseTracks(data.getAsJsonArray("rec"))
                        }
                        "SEARCH_RES" -> searchResults.value = parseTracks(json.getAsJsonArray("data"))
                        "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                        "FAV_RES" -> likedTracks.value = parseTracks(json.getAsJsonArray("data")).map { it.copy(isLiked = true) }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        })
    }

    private fun parseTracks(arr: com.google.gson.JsonArray): List<Track> {
        return arr.map {
            val o = it.asJsonObject
            Track(
                o.get("id").asString,
                o.get("title").asString,
                o.get("artist").asString,
                o.get("thumb").asString
            )
        }
    }

    // Запуск песни с обновлением очереди
    fun play(t: Track, contextList: List<Track> = emptyList()) {
        if (contextList.isNotEmpty()) queue = contextList
        currentTrack.value = t
        isPlaying.value = true
        
        // Проверка лайка
        t.isLiked = likedTracks.value.any { it.id == t.id }

        // Загрузка текста (эмуляция или запрос)
        loadLyrics(t.id)

        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    private fun loadLyrics(id: String) {
        // Здесь можно добавить реальный запрос к серверу за текстом
        // Пока просто сброс
        lyricsText.value = "Загрузка текста..."
        CoroutineScope(Dispatchers.IO).launch {
            // Эмуляция запроса
            delay(1000)
            // Если на сервере есть эндпоинт /lyrics/id, можно дернуть его через OkHttp
        }
    }

    // --- УПРАВЛЕНИЕ ОЧЕРЕДЬЮ (Prev/Next) ---
    fun next() {
        if (queue.isEmpty()) return
        val current = currentTrack.value
        val index = queue.indexOfFirst { it.id == current?.id }
        if (index != -1 && index < queue.size - 1) {
            play(queue[index + 1])
        }
    }

    fun prev() {
        if (queue.isEmpty()) return
        val current = currentTrack.value
        val index = queue.indexOfFirst { it.id == current?.id }
        if (index > 0) {
            play(queue[index - 1])
        } else {
            // Если начало списка, просто перезапускаем трек
            player?.seekTo(0)
        }
    }

    fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
        player?.shuffleModeEnabled = isShuffle.value
    }

    fun toggleRepeat() {
        repeatMode.value = (repeatMode.value + 1) % 3
        player?.repeatMode = when(repeatMode.value) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleLike(t: Track) {
        val newStatus = !t.isLiked
        t.isLiked = newStatus
        if (newStatus) likedTracks.value += t else likedTracks.value -= t
        webSocket?.send("{\"type\": \"LIKE\", \"user_id\": 1, \"id\": \"${t.id}\", \"title\": \"${t.title}\", \"artist\": \"${t.artist}\", \"thumb\": \"${t.cover}\"}")
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

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.value = pos.toLong()
    }

    fun search(q: String) { if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }
    
    // ВОТ ЭТА ФУНКЦИЯ, КОТОРОЙ НЕ ХВАТАЛО
    fun suggest(q: String) { if(q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}") }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}
