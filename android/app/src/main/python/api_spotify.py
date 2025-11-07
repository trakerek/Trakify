import requests as rq
import json
import os

SPOTIFY_CLIENT_ID = "WPISZ SWOJE API KEY  https://developer.spotify.com/documentation/web-api"
SPOTIFY_CLIENT_SECRET = "WPISZ SWOJE API KEY  https://developer.spotify.com/documentation/web-api"
def get_token():
    """Get Spotify access token"""
    token_resp = rq.post(
        "https://accounts.spotify.com/api/token",
        data={
            "grant_type": "client_credentials",
            "client_id": SPOTIFY_CLIENT_ID,
            "client_secret": SPOTIFY_CLIENT_SECRET,
        }
    )
    return token_resp.json().get("access_token")

def search_spotify(q: str, limit=20):
    """Search for tracks, albums, and artists on Spotify"""
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}
    r = rq.get(
        "https://api.spotify.com/v1/search",
        headers=headers,
        params={"q": q, "type": "track,album,artist", "limit": limit}
    )
    data = r.json()

    # Return the full response - Java will parse what it needs
    return json.dumps(data)

def get_new_releases(limit=20):
    """Get new album releases"""
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}
    r = rq.get(
        "https://api.spotify.com/v1/browse/new-releases",
        headers=headers,
        params={"limit": limit, "country": "US"}
    )
    data = r.json()

    # Simplify the response for Android
    albums = []
    if "albums" in data and "items" in data["albums"]:
        for album in data["albums"]["items"]:
            albums.append({
                "id": album.get("id"),
                "name": album.get("name"),
                "artist": album["artists"][0]["name"] if album.get("artists") else "Unknown",
                "image": album["images"][0]["url"] if album.get("images") else "",
                "release_date": album.get("release_date", ""),
            })
    return json.dumps({"albums": albums})

def get_featured_playlists(limit=10):
    """Get featured playlists"""
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}
    r = rq.get(
        "https://api.spotify.com/v1/browse/featured-playlists",
        headers=headers,
        params={"limit": limit, "country": "US"}
    )
    data = r.json()

    playlists = []
    if "playlists" in data and "items" in data["playlists"]:
        for playlist in data["playlists"]["items"]:
            playlists.append({
                "id": playlist.get("id"),
                "name": playlist.get("name"),
                "description": playlist.get("description", ""),
                "image": playlist["images"][0]["url"] if playlist.get("images") else "",
            })
    return json.dumps({"playlists": playlists})

def get_album_tracks(album_id: str):
    """Get tracks from a specific album"""
    album_id = album_id.split("/")[-1]
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}
    r = rq.get(
        f"https://api.spotify.com/v1/albums/{album_id}/tracks",
        headers=headers,
        params={"limit": 40}
    )
    data = r.json()

    tracks = []
    if "items" in data:
        for track in data["items"]:
            tracks.append({
                "id": track.get("id"),
                "name": track.get("name"),
                "artist": track["artists"][0]["name"] if track.get("artists") else "Unknown",
                "duration_ms": track.get("duration_ms", 0),
                "track_number": track.get("track_number", 0),
            })
    return json.dumps({"tracks": tracks})

# def get_top_tracks(limit=20):
#     """Get popular tracks (using a popular playlist as proxy)"""
#     token = get_token()
#     headers = {"Authorization": f"Bearer {token}"}
#
#     # Get "Today's Top Hits" playlist
#     r = rq.get(
#         # "Global Top 50"
#         "https://api.spotify.com/v1/playlists/37i9dQZEVXbMDoHDwVN2tF/tracks",
#         headers=headers,
#         params={"limit": limit}
#     )
#     data = r.json()
#
#     tracks = []
#     if "items" in data:
#         for item in data["items"]:
#             if item.get("track"):
#                 track = item["track"]
#                 tracks.append({
#                     "id": track.get("id"),
#                     "name": track.get("name"),
#                     "artist": track["artists"][0]["name"] if track.get("artists") else "Unknown",
#                     "album": track["album"]["name"] if track.get("album") else "",
#                     "image": track["album"]["images"][0]["url"] if track.get("album") and track["album"].get("images") else "",
#                     "duration_ms": track.get("duration_ms", 0),
#                 })
#     return json.dumps({"tracks": tracks})

def _parse_playlist_tracks_response(r):
    data = r.json()
    items = data.get("items", [])
    tracks = []
    for item in items:
        track = item.get("track")
        if not track:
            continue
        album = track.get("album") or {}
        artists = track.get("artists") or [{"name":"Unknown"}]
        tracks.append({
            "id": track.get("id"),
            "name": track.get("name"),
            "artist": artists[0].get("name", "Unknown"),
            "album": album.get("name",""),
            "image": (album.get("images") or [{}])[0].get("url",""),
            "duration_ms": track.get("duration_ms", 0),
        })
    return tracks, len(items)

def _search_playlist_and_get_id(token, query="top hits", limit=40):
    headers = {"Authorization": f"Bearer {token}"}
    try:
        r = rq.get("https://api.spotify.com/v1/search",
                   headers=headers,
                   params={"q": query, "type": "playlist", "limit": limit},
                   timeout=10)
    except Exception:
        return None

    # Sprawdź, czy Spotify odpowiedziało poprawnie
    if r.status_code != 200:
        print(f"[Spotify Search] HTTP {r.status_code}: {r.text}")
        return None

    try:
        j = r.json()
    except Exception as e:
        print(f"[Spotify Search] JSON decode failed: {e}, raw: {r.text[:200]}")
        return None

    if not isinstance(j, dict) or "playlists" not in j:
        print(f"[Spotify Search] Unexpected response: {j}")
        return None

    playlists = j.get("playlists", {}).get("items", [])
    if not playlists:
        print(f"[Spotify Search] No playlists found for query '{query}'")
        return None

    for p in playlists:
        if p and not p.get("public") is False:
            return p.get("id")

    return None

def get_top_tracks(country,limit=40):
    country = str(country).upper()
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}

    last_error = None
    queries_map = {
        "PL": ["Top 50 - Polska", "Polskie Hity", "Top Hits Polska"],
        "US": ["Top 50 - USA", "Today's Top Hits", "Top Hits USA"],
        "GLOBAL": ["Top 50 - Global", "Today's Top Hits", "Top Hits"]
    }

    search_queries = queries_map.get(country, queries_map["GLOBAL"])  # fallback do global


    for q in search_queries:
        pid = _search_playlist_and_get_id(token, q)
        if not pid:
            continue
        try:
            r = rq.get(f"https://api.spotify.com/v1/playlists/{pid}/tracks",
                       headers=headers, params={"limit": limit}, timeout=10)
        except Exception as e:
            last_error = {"type": "request_exception", "message": str(e), "playlist_id": pid}
            continue

        if r.status_code != 200:
            try:
                err = r.json()
            except Exception:
                err = {"text": r.text}
            last_error = {"status_code": r.status_code, "error": err, "playlist_id": pid, "raw": r.text}
            continue

        tracks, items_count = _parse_playlist_tracks_response(r)
        if tracks:
            return json.dumps({
                "tracks": tracks,
                "status_code": 200,
                "playlist_id": pid,
                "items_count": items_count,
                "tracks_parsed": len(tracks),
                "search_query": q
            })
        else:
            last_error = {"status_code": 200, "message": "no parseable tracks", "items_count": items_count, "playlist_id": pid, "search_query": q}

    # jeśli nic nie dało -> zwróć ostatni błąd
    return json.dumps({
        "tracks": [],
        "error": last_error or {"message":"no playable playlists found"},
        "status": "no_data"
    })
def get_artist_albums(artist_id: str, limit=20):
    """Get albums from a specific artist"""
    token = get_token()
    headers = {"Authorization": f"Bearer {token}"}
    r = rq.get(
        f"https://api.spotify.com/v1/artists/{artist_id}/albums",
        headers=headers,
        params={"limit": limit, "include_groups": "album,single"}
    )
    data = r.json()

    albums = []
    if "items" in data:
        for album in data["items"]:
            albums.append({
                "id": album.get("id"),
                "name": album.get("name"),
                "image": album["images"][0]["url"] if album.get("images") else "",
                "release_date": album.get("release_date", ""),
                "total_tracks": album.get("total_tracks", 0),
            })
    return json.dumps({"albums": albums})

if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        command = sys.argv[1]
        if command == "new_releases":
            print(get_new_releases())
        elif command == "featured":
            print(get_featured_playlists())
        elif command == "top_tracks":
            print(get_top_tracks("PL",40))
        elif command == "album_tracks" and len(sys.argv) > 2:
            print(get_album_tracks(sys.argv[2]))
        elif command == "artist_albums" and len(sys.argv) > 2:
            print(get_artist_albums(sys.argv[2]))
        else:
            print(search_spotify(command))