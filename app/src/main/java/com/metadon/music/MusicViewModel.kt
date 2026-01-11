package com.metadon.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*

class MusicViewModel : ViewModel() {
    private val baseUrl = "https://amhub.serveousercontent.com" 
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    val homeData = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val queue = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)

    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0) // 0 - off, 1 - all, 2 - one
    
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)
    val isLoading = mutableStateOf(true)

    fun connect() {
        val wsUrl = baseUrl.replace("http", "ws").removeSuffix("/") + "/ws"
        client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("{\"type\": \"INIT_HOME\"}")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                val json = gson.fromJson(text, JsonObject::class.java)
                when(json.get("type").asString) {
                    "INIT_HOME" -> {
                        val d = json.getAsJsonObject("data")
                        homeData.value = mapOf(
                            "Для вас" to parseTracks(d.get("rec")),
                            "Часто прослушиваемые" to parseTracks(d.get("new"))
                        )
                        isLoading.value = false
                    }
                    "SEARCH_RES" -> searchResults.value = parseTracks(json.get("data"))
                    "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                }
            }
        })
    }

    private fun parseTracks(obj: Any?): List<Track> {
        val list = obj as? com.google.gson.JsonArray ?: return emptyList()
        return list.map {
            val o = it.asJsonObject
            Track(o.get("id").asString, o.get("title").asString, o.get("artist").asString, o.get("thumb").asString)
        }
    }

    fun play(t: Track, newList: List<Track> = emptyList()) {
        if (newList.isNotEmpty()) queue.value = newList
        currentTrack.value = t
        isPlaying.value = true
        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    // УПРАВЛЕНИЕ ОЧЕРЕДЬЮ
    fun next() {
        val idx = queue.value.indexOf(currentTrack.value)
        if (idx < queue.value.size - 1) play(queue.value[idx + 1])
    }
    fun prev() {
        val idx = queue.value.indexOf(currentTrack.value)
        if (idx > 0) play(queue.value[idx - 1])
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

    fun search(q: String) { 
        if(q.length > 2) {
            webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}")
            // Сохраняем запрос для рекомендаций
            webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}")
        }
    }
    
    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}
