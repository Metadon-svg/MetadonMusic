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
    private val baseUrl = "https://amhub.serveousercontent.com" 
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)

    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)

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
                        "INIT_HOME" -> recTracks.value = parseTracks(json.getAsJsonObject("data").getAsJsonArray("rec"))
                        "SEARCH_RES" -> searchResults.value = parseTracks(json.get("data"))
                        "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                        "FAV_RES" -> likedTracks.value = parseTracks(json.get("data"))
                    }
                } catch (e: Exception) { e.printStackTrace() }
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

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.value = pos.toLong()
    }

    fun search(q: String) { if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }
    fun suggest(q: String) { if(q.length > 1) webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}") }
    fun toggleLike(t: Track) {
        webSocket?.send("{\"type\": \"LIKE\", \"user_id\": 1, \"id\": \"${t.id}\", \"title\": \"${t.title}\", \"artist\": \"${t.artist}\", \"thumb\": \"${t.cover}\"}")
    }
    fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(m, s)
    }
}
