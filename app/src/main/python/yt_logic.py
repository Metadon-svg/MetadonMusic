from ytmusicapi import YTMusic
import yt_dlp

yt = YTMusic()

def search_music(query):
    try:
        results = yt.search(query, filter="songs")
        return [{"id": r['videoId'], "title": r['title'], "artist": r['artists'][0]['name'], 
                 "artistId": r['artists'][0]['id'], "cover": r['thumbnails'][-1]['url']} for r in results]
    except: return []

def get_artist_songs(artist_id):
    try:
        artist_data = yt.get_artist(artist_id)
        results = artist_data['songs']['results']
        return [{"id": r['videoId'], "title": r['title'], "artist": artist_data['name'], 
                 "cover": r['thumbnails'][-1]['url']} for r in results]
    except: return []

def get_stream_url(video_id):
    ydl_opts = {'format': 'bestaudio/best', 'quiet': True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        return ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)['url']
