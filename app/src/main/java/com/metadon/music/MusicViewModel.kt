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
    private val baseUrl = "https://amhub.serveousercontent.com" // ПРОВЕРЬ IP
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

    // Состояния
    val isConnected = mutableStateOf(false)
    val isPlayerFull = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val currentPos = mutableStateOf(0L)
    val totalDuration = mutableStateOf(0L)
    
    // Режимы: 0 = Выкл, 1 = Всё, 2 = Одна
    val repeatMode = mutableStateOf(0) 
    val isShuffle = mutableStateOf(false)

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
            Track(o.get("id").asString, o.get("title").asString, o.get("artist").asString, o.get("thumb").asString)
        }
    }

    fun initPlayer(ctx: android.content.Context) {
        if (player == null) {
            player = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { isPlaying.value = p }
                    
                    // ГЛАВНОЕ: Ловим окончание трека
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            totalDuration.value = duration.coerceAtLeast(0L)
                        }
                        if (state == Player.STATE_ENDED) {
                            // Если песня кончилась -> включаем следующую
                            next()
                        }
                    }
                })
            }
            startTimer()
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
        
        t.isLiked = likedTracks.value.any { it.id == t.id }
        loadLyrics(t.id)

        player?.setMediaItem(MediaItem.fromUri("$baseUrl/stream/${t.id}"))
        player?.prepare()
        player?.play()
    }

    private fun loadLyrics(id: String) {
        lyricsText.value = "Загрузка текста..."
        CoroutineScope(Dispatchers.IO).launch {
            // Эмуляция или запрос к серверу
        }
    }

    fun next() {
        // Если включен повтор ОДНОЙ песни (2), просто мотаем в начало
        if (repeatMode.value == 2) {
            player?.seekTo(0)
            player?.play()
            return
        }

        if (queue.isEmpty()) return
        val current = currentTrack.value
        var index = queue.indexOfFirst { it.id == current?.id }
        
        // Если Shuffle включен, выбираем рандомный индекс
        if (isShuffle.value) {
            index = (queue.indices).random()
        } else {
            index += 1
        }

        // Если дошли до конца
        if (index >= queue.size) {
            if (repeatMode.value == 1) {
                // Если повтор ВСЕГО (1) -> идем в начало
                index = 0
            } else {
                // Если повтор ВЫКЛ (0) -> стоп
                return 
            }
        }
        
        play(queue[index])
    }

    fun prev() {
        if (queue.isEmpty()) return
        val current = currentTrack.value
        val index = queue.indexOfFirst { it.id == current?.id }
        if (index > 0) play(queue[index - 1]) else play(queue[0])
    }

    fun seekTo(pos: Float) {
        player?.seekTo(pos.toLong())
        currentPos.value = pos.toLong()
    }

    fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
    }

    fun toggleRepeat() {
        // Цикл: 0 (Выкл) -> 1 (Всё) -> 2 (Одна) -> 0
        repeatMode.value = (repeatMode.value + 1) % 3
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
