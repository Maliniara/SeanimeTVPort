# Przegląd danych wrażliwych — 2026-10-30

Zakres: bieżące źródła aplikacji i testów, konfiguracja budowania, pliki śledzone przez Git, lokalna konfiguracja AniList oraz 220 blobów w historii osiągalnej ze wszystkich lokalnych referencji Git. Nie obejmuje niezależnego repozytorium zapisanego w `backups/`, usuniętych obiektów Git, danych na urządzeniach ani pełnego audytu bibliotek i wtyczek instalowanych w czasie działania.

## Wynik

Nie znaleziono rzeczywistych tokenów użytkowników, haseł, kluczy prywatnych ani rozpoznanych kluczy dostawców zapisanych na stałe w kodzie. Literały tokenów w testach są danymi przykładowymi. Client ID AniList jest identyfikatorem publicznym, nie sekretem. Przegląd wzorców i historii nie gwarantuje wykrycia każdego możliwego sekretu.

### 1. Sekret OAuth jest dołączany do APK

- `SeanimeTV/local.properties` zawiera niepusty `ANILIST_CLIENT_SECRET`; plik jest ignorowany przez Git.
- `SeanimeTV/app/build.gradle.kts` wstawia go do `BuildConfig.ANILIST_CLIENT_SECRET`.
- `data/anilist/AniListConfig.kt` udostępnia tę wartość, a `feature/login/LoginViewModel.kt` wysyła ją przy wymianie kodu OAuth.
- Wartość sekretu nie została znaleziona w bieżących plikach śledzonych ani w sprawdzonej historii Git. Nie jest przytaczana w tym raporcie.
- APK zbudowane z obecną konfiguracją zawiera sekret możliwy do odzyskania. Rekomendacja: przed publiczną dystrybucją przejść na obsługiwany przez AniList przepływ dla klientów publicznych, bez sekretu w aplikacji. Po usunięciu zależności od sekretu rozważyć jego rotację, jeśli APK było udostępniane.

### 2. Szyfrowanie tokenów — wdrożone

- `TokenCipher.kt` używa AES-256-GCM z losowym IV dla każdego zapisu. Klucz powstaje w Android Keystore i nie jest zapisywany w plikach aplikacji.
- `AccountStore.kt` szyfruje zarówno aktywny token, jak i cały JSON zapisanych kont. `TokenDataStore.kt` także szyfruje zapisy.
- Migracje DataStore szyfrują istniejące wpisy przed udostępnieniem danych. Migracja starszego magazynu usuwa jego token dopiero po zatwierdzonym zapisie w magazynie kont.
- Błędny klucz lub zmodyfikowany szyfrogram powoduje błąd odczytu, bez przechodzenia na zapis jawny i bez interpretowania uszkodzenia jako pustej listy kont.
- Reguły `backup_rules.xml` i `data_extraction_rules.xml` wykluczają oba magazyny uwierzytelniania z kopii chmurowych i transferu urządzeń. Pozostałe dane nadal mogą podlegać backupowi.
- Token musi występować jawnie w pamięci podczas wysyłania żądania API; zmiana chroni zapis na dysku.

### 3. Logi mogą zawierać dane użytkownika lub adresy z parametrami

- `data/anilist/AniListApiClient.kt` w debug loguje treści żądań i odpowiedzi GraphQL. Nagłówek Authorization jest maskowany, lecz treść może zawierać dane konta i list.
- `data/plugin/JsHttpBridge.kt` loguje pełny URL przy błędzie, co może ujawnić token lub podpis w query string.
- `data/plugin/QuickJSEngine.kt` przekazuje argumenty konsoli wtyczek do logcat bez redakcji.
- Zgodnie z decyzją użytkownika pozostawiono logowanie danych kont. Kod autoryzacji nie loguje hasła użytkownika; nagłówek Authorization jest nadal maskowany. Dowolne logi wtyczek i adresy URL wymagają ostrożności przy udostępnianiu logcat, ponieważ ich treść zależy od zewnętrznego dostawcy.

Pozostawiono dotychczasowy przepływ OAuth. W obecnym Authorization Code Grant `client_secret` jest wymagany przy wymianie kodu na token; późniejsze wywołania GraphQL używają tokenu konta. AniList oferuje także Implicit Grant bez sekretu klienta: https://docs.anilist.co/guide/auth/implicit . Szyfrowanie tokenów kont nie usuwa sekretu OAuth z APK.
