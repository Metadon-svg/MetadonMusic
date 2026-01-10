package com.metadon.music

import com.chaquo.python.Python

object YouTubeService {
    private val py = Python.getInstance()
    private val module = py.getModule("yt_logic")

    fun search(query: String): List<Map<String, Any>> {
        val results = module.callAttr("search_music", query).asList()
        return results.map { it.asMap().mapKeys { it.key.toString() }.mapValues { it.value } }
    }

    fun getUrl(videoId: String): String {
        return module.callAttr("get_stream_url", videoId).toString()
    }
}
