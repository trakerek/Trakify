# Trakify

**Trakify** — lekki odtwarzacz muzyczny na Androida z obsługą kolejki, okładek albumów, kontrolkami w powiadomieniach/ekranie blokady oraz integracją z zewnętrznym API (pobieranie albumów / utworów). Aplikacja powstała jako projekt portfolio — pokazuje pełny flow: listy albumów, pobieranie utworów (Chaquopy + Python helper), odtwarzanie w serwisie z powiadomieniami i lockscreen controls.

## Funkcje
- Lista nowych albumow i top tracków (integracja z API).
- Odtwarzanie lokalnych plików pobranych z sieci.
- Kolejka odtwarzania (album → kolejne utwory pobierane i odtwarzane).
- Kontrolki w powiadomieniu i na ekranie blokady (play/pause/next/previous).
- Mini-player i pełny PlayerFragment z seekbarem, obrazkiem albumu i statusem buforowania.
- Obsługa pobierania (Chaquopy / Python) i zapisu plików w `getExternalFilesDir("Music")`.
- Obsługa stanu poprzez `MusicPlayerService` + `MusicPlayerManager` (broadcasty).

## Stack technologiczny
- Android (Java)
- MediaPlayer, MediaSessionCompat, NotificationCompat
- Glide (obrazki)
- Chaquopy (Python helper do pobierania)
- JSON -> model Track / Album
- Gradle (Android SDK 33+)

## Jak uruchomić
1. Otwórz projekt w Android Studio (wersja obsługująca Gradle 7/8).
   minSdk 29
   compileSdk 35
   android 10+
2. Uruchom plik spotify_api.py (jeśli go nie widac zmien na pasku wyszukiwanie plików po Projekt a nie Android)
3. Dodaj swoje klucze api z https://developer.spotify.com/documentation/web-api z dashboard)
4. Upewnij się, że masz skonfigurowane Chaquopy jeśli korzystasz z modułu `main.play_song`.
5. Zbuduj i uruchom na urządzeniu z Androidem.


