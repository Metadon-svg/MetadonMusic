from ytmusicapi import YTMusic
import yt_dlp

yt = YTMusic()

def get_best_thumb(thumbnails):
    if not thumbnails: return "https://images.unsplash.com/photo-1614680376593-902f74cf0d41?w=300"
    return thumbnails[-1]['url']

def search_music(query):
    try:
        results = yt.search(query, filter="songs", limit=20)
        return [{
            "id": str(r['videoId']),
            "title": str(r['title']),
            "artist": str(r['artists'][0]['name']),
            "artistId": str(r['artists'][0]['id']),
            "cover": get_best_thumb(r.get('thumbnails', [])),
            "duration": int(r.get('duration_seconds', 0))
        } for r in results]
    except: return []

def get_home_data():
    try:
        # Имитация твоего INIT_HOME: берем хиты и новинки
        rec = yt.search("Top Hits 2025 Global", filter="songs", limit=12)
        new = yt.search("New Music Releases", filter="songs", limit=12)
        
        def transform(data):
            return [{
                "id": str(r['videoId']),
                "title": str(r['title']),
                "artist": str(r['artists'][0]['name']),
                "artistId": str(r['artists'][0]['id']),
                "cover": get_best_thumb(r.get('thumbnails', []))
            } for r in data]
            
        return {"rec": transform(rec), "new": transform(new)}
    except: return {"rec": [], "new": []}

def get_artist_songs(artist_id):
    try:
        artist_data = yt.get_artist(artist_id)
        # Берем раздел с песнями
        songs = artist_data['songs']['results']
        return [{
            "id": str(r['videoId']),
            "title": str(r['title']),
            "artist": str(artist_data['name']),
            "artistId": str(artist_id),
            "cover": get_best_thumb(r.get('thumbnails', []))
        } for r in songs]
    except: return []

def get_stream_url(video_id):
    try:
        ydl_opts = {'format': 'bestaudio/best', 'quiet': True, 'noplaylist': True}
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)
            return str(info['url'])
    except: return ""

def get_lyrics(video_id):
    try:
        watch = yt.get_watch_playlist(videoId=video_id)
        if 'lyrics' not in watch: return "Текст песни не найден"
        lyrics_id = watch['lyrics']
        lyrics_res = yt.get_lyrics(browseId=lyrics_id)
        return str(lyrics_res['lyrics'])
    except: return "Не удалось загрузить текст"
