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
    private val baseUrl = "https://amhub.serveousercontent.com" // ВПИШИ СВОЙ IP
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    // Данные
    val homeData = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    val lyricsText = MutableStateFlow("Текст не найден")

    // Состояния
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(true)
    val repeatMode = mutableStateOf(Player.REPEAT_MODE_OFF)
    val isShuffle = mutableStateOf(false)
    
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)

    // Для поиска (Debounce)
    private var searchJob: Job? = null

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
                            val d = json.getAsJsonObject("data")
                            homeData.value = mapOf(
                                "Рекомендуем" to parseTracks(d.get("rec")),
                                "Все хиты" to parseTracks(d.get("new")),
                                "Новинки" to parseTracks(d.get("rec")).shuffled()
                            )
                            isLoading.value = false
                        }
                        "SEARCH_RES" -> searchResults.value = parseTracks(json.get("data"))
                        "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                        "FAV_RES" -> likedTracks.value = parseTracks(json.get("data")).map { it.copy(isLiked = true) }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        })
    }

    private fun parseTracks(obj: Any?): List<Track> {
        val list = obj as? com.google.gson.JsonArray ?: return emptyList()
        return list.map {
            val o = it.asJsonObject
            Track(
                o.get("id").asString,
                o.get("title").asString,
                o.get("artist").asString,
                o.get("thumb").asString
            )
        }
    }

    // Поиск с задержкой (чтобы не баговало)
    fun search(q: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500) // Ждем полсекунды, пока юзер закончит писать
            if(q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}")
            if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}")
        }
    }

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        // Проверяем лайк
        t.isLiked = likedTracks.value.any { it.id == t.id }
        
        CoroutineScope(Dispatchers.IO).launch {
            // Загружаем текст
            // Тут можно добавить запрос к серверу за текстом
        }

        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    fun toggleLike(t: Track) {
        // Оптимистичное обновление UI
        val newStatus = !t.isLiked
        t.isLiked = newStatus
        // Обновляем список лайков локально
        if (newStatus) {
            likedTracks.value = listOf(t) + likedTracks.value
        } else {
            likedTracks.value = likedTracks.value.filter { it.id != t.id }
        }
        // Шлем на сервер
        webSocket?.send("{\"type\": \"LIKE\", \"user_id\": 1, \"id\": \"${t.id}\", \"title\": \"${t.title}\", \"artist\": \"${t.artist}\", \"thumb\": \"${t.cover}\"}")
    }

    fun toggleRepeat() {
        val next = when(repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeatMode.value = next
        player?.repeatMode = next
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

    fun seekTo(pos: Float) { player?.seekTo(pos.toLong()); currentPos.value = pos.toLong() }
    fun formatTime(ms: Long): String = "%02d:%02d".format((ms / 1000) / 60, (ms / 1000) % 60)
}
