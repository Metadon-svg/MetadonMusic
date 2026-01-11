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
    private val baseUrl = "https://amhub.serveousercontent.com" // ЗАМЕНИ НА СВОЙ IP
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    // --- ДАННЫЕ ---
    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Загрузка текста...")
    
    // Данные для главной (Карусели)
    val homeData = MutableStateFlow<Map<String, List<Track>>>(emptyMap())

    // --- СОСТОЯНИЯ ---
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(true) // Индикатор загрузки для скелетона
    
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)
    
    // Режимы
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0)

    // Очередь
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
                            val rec = parseTracks(data.getAsJsonArray("rec"))
                            val new = parseTracks(data.getAsJsonArray("new"))
                            
                            // Заполняем homeData для каруселей
                            homeData.value = mapOf(
                                "Для вас" to rec,
                                "Новинки" to new,
                                "В тренде" to rec.shuffled()
                            )
                            isLoading.value = false // Выключаем скелетон
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

    fun play(t: Track, contextList: List<Track> = emptyList()) {
        if (contextList.isNotEmpty()) queue = contextList
        currentTrack.value = t
        isPlaying.value = true
        
        // Проверка лайка
        t.isLiked = likedTracks.value.any { it.id == t.id }
        
        // Загрузка текста (заглушка или реальный запрос)
        loadLyrics(t.id)

        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    private fun loadLyrics(id: String) {
        lyricsText.value = "Загрузка текста..."
        // Тут можно добавить запрос к серверу через OkHttp, если нужно
    }

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
            player?.seekTo(0)
        }
    }

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.value = pos.toLong()
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

    fun search(q: String) { if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }
    fun suggest(q: String) { if(q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}") }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}
