package com.metadon.music

import androidx.compose.runtime.mutableStateOf
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
    private val gson = Gson()
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    var player: ExoPlayer? = null

    val recTracks = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val favoriteTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
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
                send("{\"type\": \"INIT_HOME\"}")
                send("{\"type\": \"GET_FAV\", \"user_id\": 1}") // Имитируем user_id=1
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = gson.fromJson(text, JsonObject::class.java)
                when(json.get("type").asString) {
                    "INIT_HOME" -> {
                        val d = json.getAsJsonObject("data")
                        recTracks.value = parse(d.getAsJsonArray("rec"))
                    }
                    "SEARCH_RES" -> searchResults.value = parse(json.getAsJsonArray("data"))
                    "SUGGEST_RES" -> suggestions.value = json.getAsJsonArray("data").map { it.asString }
                    "FAV_RES" -> favoriteTracks.value = parse(json.getAsJsonArray("data"))
                    "PLAYLISTS_LIST" -> playlists.value = json.getAsJsonArray("data").map { 
                        val o = it.asJsonObject
                        Playlist(o.get("id").asInt, o.get("name").asString)
                    }
                }
            }
        })
    }

    private fun send(txt: String) = webSocket?.send(txt)

    fun getSuggestions(q: String) { if(q.length > 1) send("{\"type\": \"SUGGEST\", \"query\": \"$q\"}") }
    fun search(q: String) { if(q.length > 2) send("{\"type\": \"SEARCH\", \"query\": \"$q\"}") }
    
    fun toggleLike(t: Track) {
        send("{\"type\": \"LIKE\", \"user_id\": 1, \"id\": \"${t.id}\", \"title\": \"${t.title}\", \"artist\": \"${t.artist}\", \"thumb\": \"${t.cover}\"}")
    }

    private fun parse(arr: com.google.gson.JsonArray) = arr.map {
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
}
