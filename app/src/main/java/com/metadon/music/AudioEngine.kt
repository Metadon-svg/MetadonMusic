package com.metadon.music

object AudioEngine {
    init {
        System.loadLibrary("music_engine")
    }
    external fun getEngineStatus(): String
}