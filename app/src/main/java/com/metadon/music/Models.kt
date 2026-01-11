package com.metadon.music

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String,
    val isLiked: Boolean = false
)
