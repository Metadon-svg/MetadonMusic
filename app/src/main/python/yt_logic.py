from ytmusicapi import YTMusic
import yt_dlp

yt = YTMusic()

def search_music(query):
    # Ищем песни в YouTube Music
    results = yt.search(query, filter="songs")
    output = []
    for r in results:
        output.append({
            "id": r['videoId'],
            "title": r['title'],
            "artist": r['artists'][0]['name'],
            "cover": r['thumbnails'][-1]['url']
        })
    return output

def get_stream_url(video_id):
    # Получаем прямую ссылку на аудио-поток
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)
        return info['url']
