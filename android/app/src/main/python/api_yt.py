# api_yt.py  — fallback-friendly downloader (yt-dlp / youtube_dl / pytubefix)
import os
import json
import urllib.parse
import urllib.request
import traceback
import sys
import re

# ---------- konfiguracja ----------
API_KEY = os.environ.get("YOUTUBE_API_KEY", "")  # ustaw jeśli chcesz użyć YouTube Data API
# -----------------------------------

# backend detection
_backend = None
_YDL = None
try:
    # prefer yt-dlp
    from yt_dlp import YoutubeDL as _YDL
    _backend = "yt_dlp"
except Exception as e:
    print("⚠️ yt_dlp import failed:", e)
    _YDL = None

if _YDL is None:
    try:
        import youtube_dl as _ytdl_mod
        _YDL = _ytdl_mod.YoutubeDL
        _backend = "youtube_dl"
    except Exception as e:
        print("⚠️ youtube_dl import failed:", e)
        _YDL = None

# pytubefix / pytube fallback (for direct stream usage)
YouTube = None
pytube_request = None
_import_source = None
if _YDL is None:
    try:
        from pytubefix import __main__ as _pymain
        if hasattr(_pymain, "YouTube"):
            YouTube = _pymain.YouTube
            _import_source = "pytubefix.__main__"
    except Exception:
        pass

    if YouTube is None:
        try:
            from pytubefix.pytubefix import __main__ as _pymain2
            if hasattr(_pymain2, "YouTube"):
                YouTube = _pymain2.YouTube
                _import_source = "pytubefix.pytubefix.__main__"
        except Exception:
            pass

    if YouTube is None:
        try:
            from pytubefix import YouTube as _yt
            YouTube = _yt
            _import_source = "pytubefix.YouTube"
        except Exception:
            pass

    try:
        import pytubefix.request as pytube_request
    except Exception:
        try:
            import pytubefix.pytubefix.request as pytube_request
        except Exception:
            pytube_request = None

print("🔍 youtube backend:", _backend, "pytubefix source:", _import_source)

# ---------- utilities ----------
def _sanitize_filename(name: str) -> str:
    keep = "-_.() abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    res = "".join(c if c in keep else "_" for c in (name or "song"))
    return res.strip()

def _choose_downloaded_file(out_dir, base_name):
    """Znajdź plik najbliższy base_name w katalogu (używane gdy backend doda rozszerzenie)."""
    base_no_ext = os.path.splitext(base_name)[0]
    candidates = []
    for root, dirs, files in os.walk(out_dir):
        for f in files:
            if f.startswith(base_no_ext):
                candidates.append(os.path.join(root, f))
    if not candidates:
        # fallback: wybierz największy plik w katalogu out_dir
        files = [os.path.join(out_dir, f) for f in os.listdir(out_dir) if os.path.isfile(os.path.join(out_dir, f))]
        if not files:
            return None
        files.sort(key=lambda p: os.path.getsize(p), reverse=True)
        return files[0]
    candidates.sort(key=lambda p: os.path.getsize(p), reverse=True)
    return candidates[0]

# ---------- search ----------
def search_youtube(query: str):
    """Zwraca URL do pierwszego wyniku lub None."""
    q = (query or "").strip()
    if not q:
        return None

    # 1) gdy mamy yt-dlp / youtube_dl - użyj wbudowanego "ytsearch1:"
    if _YDL is not None:
        try:
            opts = {"quiet": True, "no_warnings": True, "skip_download": True, "noplaylist": True}
            with _YDL(opts) as ydl:
                search = f"ytsearch1:{q}"
                info = ydl.extract_info(search, download=False)
                # yt-dlp zwraca dict z 'entries'
                entries = info.get("entries") if isinstance(info, dict) else None
                if entries and len(entries) > 0:
                    vid = entries[0].get("webpage_url") or entries[0].get("url")
                    print("🔹 search via", _backend, "->", vid)
                    return vid
        except Exception as e:
            print("⚠️ search via", _backend, "failed:", e)
            traceback.print_exc()

    # 2) jeśli jest klucz YouTube Data API - użyj go (może skończyć się na limicie)
    if API_KEY:
        try:
            qesc = urllib.parse.quote(q)
            url = f"https://www.googleapis.com/youtube/v3/search?part=snippet&q={qesc}&type=video&maxResults=1&key={API_KEY}"
            with urllib.request.urlopen(url, timeout=15) as resp:
                txt = resp.read().decode("utf-8")
                data = json.loads(txt)
                items = data.get("items", [])
                if items:
                    vid = items[0]["id"].get("videoId")
                    if vid:
                        video_url = f"https://www.youtube.com/watch?v={vid}"
                        print("🔹 search via Data API ->", video_url)
                        return video_url
        except Exception as e:
            print("⚠️ YouTube Data API search failed:", e)
            traceback.print_exc()

    # 3) fallback: prosty crawl strony wyników (niezalecane, brittle)
    try:
        qesc = urllib.parse.quote(q)
        url = f"https://www.youtube.com/results?search_query={qesc}"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            # znajdź pierwszy wystąpienie "/watch?v=..."
            m = re.search(r"\/watch\?v=([A-Za-z0-9_\-]{6,})", html)
            if m:
                vid = m.group(1)
                video_url = f"https://www.youtube.com/watch?v={vid}"
                print("🔹 search via web-scrape ->", video_url)
                return video_url
    except Exception as e:
        print("⚠️ web-scrape search failed:", e)
        traceback.print_exc()

    print("🔹 Brak wyników")
    return None

# ---------- download ----------
def download_audio(video_url: str, filepath: str):
    """
    Pobierz audio do filepath (nie zawsze dokładnie takiego rozszerzenia).
    Zwraca pełną ścieżkę do pliku lub None.
    """
    print("▶ download_audio called:", video_url, filepath)
    out_dir = os.path.dirname(filepath) or "."
    base_name = os.path.basename(filepath) or "song"
    base_name = _sanitize_filename(base_name)
    os.makedirs(out_dir, exist_ok=True)

    # 1) yt-dlp / youtube_dl backend
    if _YDL is not None:
        try:
            # zapisz z placeholderem rozszerzenia — backend doda właściwe
            outtmpl = os.path.join(out_dir, base_name + ".%(ext)s")
            opts = {
                "format": "bestaudio/best",
                "outtmpl": outtmpl,
                "noplaylist": True,
                "quiet": True,
                "no_warnings": True,
                # nie wymuszamy konwersji do mp3 (na urządzeniu może nie być ffmpeg)
                # "postprocessors": [{"key":"FFmpegExtractAudio", "preferredcodec":"mp3", "preferredquality":"192"}],
            }
            with _YDL(opts) as ydl:
                info = ydl.extract_info(video_url, download=True)
                # spróbuj znaleźć zapisany plik
                saved = None
                # info może zawierać 'requested_downloads' albo 'url' + ext - spróbujemy heurystyk
                # odpalemy search w katalogu
                saved = _choose_downloaded_file(out_dir, base_name)
                if saved:
                    print("✅ downloaded (via)", _backend, "->", saved)
                    return saved
                else:
                    print("⚠️ Could not find downloaded file after", _backend)
        except Exception as e:
            print("⚠️ download via", _backend, "failed:", e)
            traceback.print_exc()

    # 2) pytubefix fallback - używaj strumieni pytubefix/pytube
    if YouTube is not None:
        try:
            yt = YouTube(video_url)
            print("🔹 pytubefix YouTube object, title:", getattr(yt, "title", "<no-title>"))
            # wybierz audio stream
            stream = None
            try:
                stream = yt.streams.filter(only_audio=True).first()
            except Exception:
                # alternatywna heurystyka
                streams = getattr(yt, "streams", None)
                if streams:
                    for s in streams:
                        if getattr(s, "type", "") == "audio" or getattr(s, "mime_type", "").startswith("audio"):
                            stream = s
                            break

            if stream is None:
                print("🔴 pytubefix: no audio stream found")
            else:
                # nazwa pliku zgodna z base_name; pytubefix/pytube może mieć .download(output_path, filename)
                try:
                    saved = stream.download(output_path=out_dir, filename=base_name)
                    saved_path = saved if saved else os.path.join(out_dir, base_name)
                except TypeError:
                    saved = stream.download(out_dir)
                    saved_path = saved if saved else os.path.join(out_dir, base_name)

                # jeśli zwrócono katalog, poszukaj pliku wewnątrz
                if os.path.isdir(saved_path):
                    candidate = _choose_downloaded_file(saved_path, base_name)
                    if candidate:
                        dest = os.path.join(out_dir, base_name + os.path.splitext(candidate)[1])
                        try:
                            os.rename(candidate, dest)
                            try:
                                os.rmdir(saved_path)
                            except Exception:
                                pass
                            saved_path = dest
                        except Exception:
                            pass

                if os.path.exists(saved_path) and os.path.isfile(saved_path):
                    print("✅ downloaded (via pytubefix) ->", saved_path)
                    return saved_path
                else:
                    print("🔴 pytubefix download result missing:", saved_path)
        except Exception as e:
            print("⚠️ pytubefix download failed:", e)
            traceback.print_exc()

    # nic nie zadziałało
    print("🔴 download_audio: all backends failed")
    return None

# expose helpers for other modules
__all__ = ["search_youtube", "download_audio", "_backend", "YouTube"]
