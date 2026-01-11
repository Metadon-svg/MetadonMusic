package com.metadon.music

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String,
    val duration: Int = 0
)

data class LyricsLine(
    val time: Double,
    val text: String
)

data class Playlist(
    val id: Int,
    val name: String
)
