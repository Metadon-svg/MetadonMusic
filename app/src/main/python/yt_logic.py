from ytmusicapi import YTMusic
import yt_dlp

# Инициализация с таймаутом
yt = YTMusic()

def get_home_data():
    try:
        # Ищем через поиск, так как get_home() требует авторизации
        rec = yt.search("Trending Songs Global", filter="songs", limit=10)
        new = yt.search("New Hits 2025", filter="songs", limit=10)
        
        def parse(items):
            return [{"id": str(i['videoId']), "title": str(i['title']), "artist": str(i['artists'][0]['name']), 
                     "artistId": str(i['artists'][0]['id']), "cover": str(i['thumbnails'][-1]['url'])} for i in items if 'videoId' in i]
        
        return {"rec": parse(rec), "new": parse(new)}
    except Exception as e:
        return {"rec": [], "new": []}

def search_music(query):
    try:
        res = yt.search(query, filter="songs", limit=20)
        return [{"id": str(i['videoId']), "title": str(i['title']), "artist": str(i['artists'][0]['name']), 
                 "artistId": str(i['artists'][0]['id']), "cover": str(i['thumbnails'][-1]['url'])} for i in res if 'videoId' in i]
    except: return []

def get_artist_songs(artist_id):
    try:
        artist = yt.get_artist(artist_id)
        songs = artist['songs']['results']
        return [{"id": str(i['videoId']), "title": str(i['title']), "artist": str(artist['name']), 
                 "artistId": str(artist_id), "cover": str(i['thumbnails'][-1]['url'])} for i in songs]
    except: return []

def get_stream_url(video_id):
    try:
        ydl_opts = {'format': 'bestaudio/best', 'quiet': True, 'noplaylist': True}
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            return ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)['url']
    except: return ""
