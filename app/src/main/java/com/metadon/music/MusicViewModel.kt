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
    private val baseUrl = "https://amhub.serveousercontent.com" // ВПИШИ СВОЙ IP
    private val client = OkHttpClient()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)

    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)

    fun connect() {
        val wsUrl = baseUrl.replace("http", "ws").removeSuffix("/") + "/ws"
        client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("{\"type\": \"INIT_HOME\"}")
                ws.send("{\"type\": \"GET_FAV\", \"user_id\": 1}")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                val json = gson.fromJson(text, JsonObject::class.java)
                when(json.get("type").asString) {
                    "INIT_HOME" -> recTracks.value = parseTracks(json.getAsJsonObject("data").getAsJsonArray("rec"))
                    "SEARCH_RES" -> searchResults.value = parseTracks(json.getAsJsonArray("data"))
                    "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                    "FAV_RES" -> likedTracks.value = parseTracks(json.getAsJsonArray("data"))
                }
            }
        })
    }

    private fun parseTracks(arr: com.google.gson.JsonArray) = arr.map {
        val o = it.asJsonObject
        Track(o.get("id").asString, o.get("title").asString, o.get("artist").asString, o.get("thumb").asString)
    }

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    fun toggleLike(t: Track) {
        webSocket?.send("{\"type\": \"LIKE\", \"user_id\": 1, \"id\": \"${t.id}\", \"title\": \"${t.title}\", \"artist\": \"${t.artist}\", \"thumb\": \"${t.cover}\"}")
    }

    fun search(q: String) = webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}")
    fun suggest(q: String) = webSocket?.send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}")
    fun seekTo(pos: Float) { player?.seekTo(pos.toLong()); currentPos.value = pos.toLong() }
    
    fun startTimer() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                player?.let {
                    if (it.isPlaying) {
                        currentPos.value = it.currentPosition
                        totalDuration.value = it.duration.coerceAtLeast(0)
                    }
                }
                delay(1000)
            }
        }
    }
}
