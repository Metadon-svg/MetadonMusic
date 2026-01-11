package com.metadon.music

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*

data class Track(val id: String, val title: String, val artist: String, val cover: String)

class MusicViewModel : ViewModel() {
    // === ВВЕДИ СВОЮ ССЫЛКУ ТУТ ===
    private val baseUrl = "http://твой-айпи-или-домен:8000" 
    // =============================

    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = MutableStateFlow<Track?>(null)
    
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val gson = Gson()
    var player: ExoPlayer? = null

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
                    if (json.get("type").asString == "INIT_HOME") {
                        val data = json.getAsJsonObject("data")
                        recTracks.value = parse(data.getAsJsonArray("rec"))
                    } else if (json.get("type").asString == "SEARCH_RES") {
                        searchResults.value = parse(json.getAsJsonArray("data"))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.value = false
            }
        })
    }

    private fun parse(arr: com.google.gson.JsonArray) = arr.map {
        val o = it.asJsonObject
        Track(o.get("id").asString, o.get("title").asString, o.get("artist").asString, o.get("thumb").asString)
    }

    fun search(q: String) { if(q.length > 2) webSocket?.send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }

    fun play(t: Track) {
        currentTrack.value = t
        isPlaying.value = true
        val streamUrl = "${baseUrl.removeSuffix("/")}/stream/${t.id}"
        player?.setMediaItem(MediaItem.fromUri(streamUrl))
        player?.prepare()
        player?.play()
    }
}
