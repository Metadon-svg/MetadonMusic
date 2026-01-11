package com.metadon.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.io.IOException

class MusicViewModel : ViewModel() {
    // ВВЕДИ СВОЙ АДРЕС ТУТ
    private val baseUrl = "https://amhub.serveousercontent.com" 
    
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val newTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val favoriteTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentLyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)

    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableLongStateOf(0L)
    val totalDuration = mutableLongStateOf(0L)

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
                            newTracks.value = parseTracks(data.getAsJsonArray("new"))
                        }
                        "SEARCH_RES" -> {
                            searchResults.value = parseTracks(json.getAsJsonArray("data"))
                        }
                        "SUGGEST_RES" -> {
                            val listType = object : TypeToken<List<String>>() {}.type
                            suggestions.value = gson.fromJson(json.getAsJsonArray("data"), listType)
                        }
                        "FAV_RES" -> {
                            favoriteTracks.value = parseTracks(json.getAsJsonArray("data"))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.value = false
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
                o.get("thumb").asString,
                o.get("duration").asInt
            )
        }
    }

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        // Использование твоего идеально настроенного стриминга
        val streamUrl = "${baseUrl.removeSuffix("/")}/stream/${t.id}"
        player?.setMediaItem(MediaItem.fromUri(streamUrl))
        player?.prepare()
        player?.play()
        loadLyrics(t.id)
    }

    private fun loadLyrics(videoId: String) {
        val request = Request.Builder().url("${baseUrl.removeSuffix("/")}/lyrics/$videoId").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = gson.fromJson(body, JsonObject::class.java)
                val linesArray = json.getAsJsonArray("lines")
                val listType = object : TypeToken<List<LyricsLine>>() {}.type
                currentLyrics.value = gson.fromJson(linesArray, listType)
            }
        })
    }

    fun startTimer() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                player?.let {
                    if (it.isPlaying) {
                        currentPos.longValue = it.currentPosition
                        totalDuration.longValue = it.duration.coerceAtLeast(0L)
                    }
                }
                delay(1000)
            }
        }
    }

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.longValue = pos.toLong()
    }

    fun search(q: String) {
        if (q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}")
    }

    fun suggest(q: String) {
        if (q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}")
    }

    fun toggleLike(t: Track) {
        val msg = JsonObject().apply {
            addProperty("type", "LIKE")
            addProperty("user_id", 1)
            addProperty("id", t.id)
            addProperty("title", t.title)
            addProperty("artist", t.artist)
            addProperty("thumb", t.cover)
            addProperty("duration", t.duration)
        }
        webSocket?.send(gson.toJson(msg))
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}
