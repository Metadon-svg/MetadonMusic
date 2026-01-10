from ytmusicapi import YTMusic
import yt_dlp

yt = YTMusic()

def search_music(query):
    try:
        results = yt.search(query, filter="songs")
        output = []
        for r in results:
            output.append({
                "id": str(r['videoId']),
                "title": str(r['title']),
                "artist": str(r['artists'][0]['name']),
                "cover": str(r['thumbnails'][-1]['url'])
            })
        return output
    except:
        return []

def get_stream_url(video_id):
    ydl_opts = {'format': 'bestaudio/best', 'quiet': True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)
        return info['url']
