# main.py
import api_yt

def play_song(query: str, output_path: str):
    """
    Wywoływane z Androida:
      mainModule.callAttr("play_song", title, path)
    Musi zwrócić pełną ścieżkę do pobranego pliku (albo None).
    """
    print("▶ play_song called:", query, output_path)
    video = api_yt.search_youtube(query)
    if not video:
        print("🔹 Brak wyników")
        return None
    saved = api_yt.download_audio(video, output_path)
    print("▶ play_song result:", saved)
    return saved
