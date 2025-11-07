# Trakify

**Trakify** — lekki odtwarzacz muzyczny na Androida z obsługą kolejki, okładek albumów, kontrolkami w powiadomieniach/ekranie blokady oraz integracją z zewnętrznym API (pobieranie albumów / utworów). Aplikacja powstała jako projekt portfolio — pokazuje pełny flow: listy albumów, pobieranie utworów (Chaquopy + Python helper), odtwarzanie w serwisie z powiadomieniami i lockscreen controls.

## Funkcje
- Lista nowych wydawnictw i top tracków (integracja z API).
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

## Jak uruchomić (dla deva)
1. Otwórz projekt w Android Studio (wersja obsługująca Gradle 7/8).
2. Upewnij się, że masz skonfigurowane Chaquopy jeśli korzystasz z modułu `main.play_song`.
3. Zbuduj i uruchom na urządzeniu z Androidem.

## Uwaga o API keys i bezpieczeństwie
> **Krótko:** NIE wrzucaj kluczy API ani sekretów bezpośrednio do kodu ani zasobów aplikacji. Plik `.apk` może zostać zdekompilowany i klucze wydobyte.

Zalecenia:
- Traktuj klucze jako tajne — trzymaj je na serwerze i udostępniaj tylko endpoint, który robi żądania do zewnętrznego API.
- Jeśli musisz, wstrzykuj klucze **podczas builda** przez zmienne środowiskowe i `buildConfigField`, ale pamiętaj — nadal będą widoczne w dekompilowanym APK.
- Dodatkowe utrudnienia: obfuskacja (R8/ProGuard), przechowywanie części sekretów w NDK (native) — to tylko utrudnienia, **nie** pełne zabezpieczenie.
- Najbezpieczniej: autoryzacja OAuth (tokeny krótkotrwałe) albo własne API pośredniczące.

## Licencja
Możesz dodać np. `MIT` jeśli chcesz udostępnić kod.

---

