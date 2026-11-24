# Carbon Europe — moduł lokalny

Moduł jest generowany z **dostarczonego ISO użytkownika**, a nie z plików gry
pobranych z internetu. `default.xex` wyodrębniono lokalnie narzędziem
`tools/inspect_xex.py`.

| Pole | Wartość |
|---|---|
| Title ID | `454107EC` |
| Media ID | `573FDB37` |
| Wersja | `0.0.0.4` |
| Entry point | `0x829436C0` |
| XEX SHA-256 | `f72ce8d9bab3284e4555a1675d19df13f9f665c0ade014a7666a924dce7e10a1` |
| XEX SHA-1 | `90c2181d9f67612b017da1ce23b07d735e2d3f58` |
| Generator | oficjalne ReXGlue SDK `v0.10.0`, Linux ARM64 |
| Mapowania funkcji | **72 429** |
| Źródła C++ gościa | 170 plików `carbon_europe_recomp.*.cpp` |

`generated/` i `image.bin` to lokalne wyniki analizy własnego XEX. Nie są
zależnościami pobranymi z internetu. Samo wygenerowanie i skompilowanie kodu nie
stanowi testu działania gry.

## Wybrane rozwiązanie

Projekt [codepdbh/NFS-CARBON-360-ANDROID-RECOMP](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP)
ma działającą warstwę Androida, ale wspiera inne wydanie, Media ID `5E74E60D`,
XEX `b1e423...`, entry `0x82943460`. Jego adresy hooków i kod gry nie zostały
podstawione pod nasze wydanie. Także port
[nfscarbon-nx](https://github.com/Kaays/nfscarbon-nx) dokumentuje, że wydanie
`573FDB37` ma inne adresy i potrzebuje własnego manifestu.

Zamiast natywnego renderera zależnego od tamtych adresów moduł używa backendu
**Xenos/Vulkan** oraz nowo wygenerowanego kodu PowerPC. Runtime, SDL i backend
GPU pobrano bez modyfikacji z wydania Android `v0.3.3`. Sumę APK sprawdzono
względem `SHA256SUMS-v0.3.3.txt`. Zależności i hash są zapisane w
[`runtime-provenance.json`](runtime-provenance.json).

Nagłówki i `ReXApp` pochodzą z dokładnie wskazanego przez to wydanie commitu
`codepdbh/nfsmw-android`:
`a7e6e4c3ca71064d0eea56d91fcc8aaf63418fc7`. Jeden lokalny patch poprawia szerokość
rejestru w inline asm `FPSCRPlatform::setcsr` dla Clang 21; nie zmienia ABI.

## Naprawy analizy

Pierwsza walidacja wykazała trzy rzeczywiste cele bez granic funkcji:
`0x82A65770`, `0x82321990`, `0x82A623E8`. Dodano je w
[`carbon_europe.toml`](carbon_europe.toml), po czym ponowne codegen zakończyło się
powodzeniem.

Emiter zgłosił trzy gałęzie warunkowe. Jedna miała etykietę w tej samej funkcji;
przywrócono `goto`. Dwie pozostałe wskazywały na oryginalne dwie instrukcje
`li r3,0; blr` przed wyodrębnionym fragmentem funkcji. Ich instrukcje i względny
adres skoku sprawdzono w `image.bin` przed wiernym przetłumaczeniem powrotu z 0.
Narzędzie [`tools/repair_local_branches.py`](../../tools/repair_local_branches.py)
odmawia tej zamiany, jeśli bajty nie pasują. Po naprawie w źródłach gościa nie
ma placeholderów `REX_FATAL`.

Moduł odzyskuje także pominięte oryginalne wejścia `blr` i ośmioinstrukcyjne
adaptery callbacków na podstawie pełnego wzorca instrukcji, zachowując istniejące
mapowania. Nie zamienia nieznanych funkcji w puste stuby.

## Funkcja pominięta przy ładowaniu — 0.1.2

Nowe logi z telefonu zawierały `Call to invalid or unregistered function at guest
address 0x824DAC58`, a następnie `SIGABRT`. Dodano adres do manifestu i wykonano
ponowny codegen oficjalnym SDK v0.10.0. Z jego wyniku wydzielono niezmienione ciało
funkcji do `supplemental_824DAC58.cpp`; pochodzenie i hash są zapisane w JSON obok.
Funkcja przygotowuje trzy argumenty i wykonuje skok do `0x822FDE60`.

Istniejące 72 429 mapowań zachowano. Dodatkowy adres jest rejestrowany w
`OnPreLaunchModule`, po porównaniu wszystkich sześciu instrukcji z załadowanego
XEX. Pełny przyszły codegen z nowym manifestem dołączy adres do zwykłych plików;
CMake automatycznie pomija wtedy dodatkowy plik, aby uniknąć podwójnej definicji.

Test `tools/test_carbon_entry.sh` wykonuje rzeczywiste wygenerowane ciało z
kontrolowanym celem skoku: sprawdza rozszerzenie znaku, odczyt big endian,
argumenty, zachowanie LR/CTR i wynik. Nie zastępuje testu pełnego ładowania gry.

## Tablice wywołań i wspólne bloki — 0.1.3

Log 0.1.2 potwierdził rejestrację 0x824DAC58, a potem `SIGABRT` przy
`0x82322188`. W nie wykonywanych sekcjach `.rdata` i `.data` znaleziono brakujące
adresy należące do zakresów wykonywalnych PE, zapisane w ciągłych tablicach
wskaźników. To również cele skoków obliczanych wewnątrz funkcji. Lista 334 adresów,
ich odwołania i hash XEX są w `indirect_entries.json`.

Ponowny codegen SDK wygenerował wszystkie 334 wejścia. Sześć zależnych skoków
wymaga wejścia do istniejących etykiet w czterech już wygenerowanych funkcjach.
Każdy opcode `b`/`bc`, przesunięcie i brak bitu link są sprawdzane w image.bin.
Wejście kopiuje oryginalny wygenerowany kod rodzica i jego hostowe zmienne
pomocnicze, po czym przechodzi do właściwej etykiety, pomijając guestowy prolog.
Zapobiega to ponownemu tworzeniu ramki stosu. Skok warunkowy zachowuje oryginalny
warunek; przejście wraca z funkcji po zakończeniu docelowego bloku.

`supplemental_indirect.cpp` zawiera **340** dodatkowych implementacji i tablicę
rejestracji. Przed rejestracją runtime porównuje osiem oryginalnych słów każdego
wejścia. `supplemental_indirect.json` zawiera adresy, hashe ciał i rodziców bloków.
Nieznane instrukcje/skoki nie są zastępowane pustymi implementacjami.

Odtworzenie dodatkowego codegen i ekstrakcji po przygotowaniu image.bin:

```sh
python rex-launcher/tools/discover_indirect_entries.py
mc-linux -- /bin/bash -lc 'cd /workspace/rex-launcher/build/indirect-codegen; \
  LD_PRELOAD=/opt/rex-build/librex-host-shm.so \
  LD_LIBRARY_PATH=/opt/rex-build/host-libs/usr/lib/aarch64-linux-gnu \
  /opt/rex-build/rex-sdk/linux-arm64/bin/rexglue codegen carbon_europe.toml'
python rex-launcher/tools/extract_indirect_entries.py
mc-linux -- /bin/bash /workspace/rex-launcher/tools/test_indirect_entries.sh
```

Test wykonuje funkcję z nowego crasha na czterech ścieżkach dispatchu, pięć
wariantów mapowania stanu i 14 wariantów zasobów. Dodatkowo sprawdza wejście do
bloku `0x827A919C`, bezpośrednio i przez skok z `0x827A9214`, w tym pominięcie
prologu gościa, ramkę stosu i wynik. Testy przeszły; pozostałe dodatkowe ciała
sprawdzono przez kompilację, linkowanie i kontrolę symboli, nie przez gameplay.

## Przypisanie kontrolera — 0.1.4

Użytkownik potwierdził start gry w 0.1.3, lecz przyciski ekranowe nie reagowały.
Przyczyną był brak `SetDeviceAssignment` we własnej fabryce `InputSystem`.
SDK sprawdza obecność policy przed enumeracją/odczytem drivera, więc wszystkie
odczyty wracały jako odłączenie kontrolera. Fabryka ustawia teraz `SlotAssignment`.
`OnPreLaunchModule` zapisuje do logu `Android XInput user 0: status=...`.
Test `test_rex_input.sh` potwierdza odtworzenie problemu i odczyt przez warstwę
SDK po poprawce; nie zastępuje testu dotyku w interfejsie Androida.

## Callback kariery — 0.1.5

Log `rex-launcher-1793712531684.txt`, sesja procesu 4697, kończy się o
14:28:47.280 wywołaniem niezarejestrowanego **0x825BF858** i `SIGABRT`.
Kontroler ma wcześniej status 0, a 340 dodatkowych wejść jest zarejestrowanych.

Adres callbacku jest tworzony przez `lis r11,0x825C` w **0x825BC79C** oraz
`addi r4,r11,-1960` w **0x825BC7AC**. Poprzednia analiza tablic danych nie
obejmowała tej konstrukcji. Dodano adres do manifestu i ponowiono oficjalny
codegen SDK; wydzielono jego niezmienione ośmioinstrukcyjne ciało do
`supplemental_825BF858.cpp`. Rejestracja sprawdza wszystkie osiem słów XEX,
a CMake pomija dodatkowy plik, jeśli przyszły pełny codegen zawiera już symbol.
Zachowano wcześniejsze mapowania i 340 dodatkowych implementacji.

`tools/extract_career_entry.py` sprawdza osiem instrukcji, pełną sekwencję
konstruowania wskaźnika oraz względny skok bez linku do istniejącego
**0x825BF1F8**. Metadane, pochodzenie i hashe są w
`supplemental_825BF858.json`; hash tego manifestu trafia do metadanych APK.
Generator zakończył analizę i zapis. Trzy ostrzeżenia o gałęziach w innych
funkcjach są tymi samymi lokalnymi gałęziami opisanymi wcześniej; nie kopiowano
ich ponownie do modułu. Nowe ciało nie zawiera nierozwiązanych gałęzi.

Aby odtworzyć ekstrakcję, utwórz `build/career-codegen` z kopią lokalnego
`default.xex` oraz bieżącego `carbon_europe.toml`, wykonaj tam codegen SDK,
następnie:

```sh
python rex-launcher/tools/extract_career_entry.py
mc-linux -- /bin/bash /workspace/rex-launcher/tools/test_career_entry.sh
mc-linux -- /bin/bash /workspace/rex-launcher/tools/build_carbon_module.sh
mc-linux -- /bin/bash /workspace/rex-launcher/tools/build_arm64.sh
```

Test wykonuje rzeczywistą wygenerowaną funkcję, zastępując tylko docelowe
wywołanie kontrolowanym sprawdzeniem ABI. Weryfikuje dwa adresy obiektu,
`stb`, `stw`, sąsiednie bajty/słowa, rozszerzenie znaku, argumenty, LR/CTR,
rejestr nieulotny, brak ramki stosu i przekazanie wyniku. Nie jest testem pełnej
kariery ani zapisu gry.

## Uruchamianie

`CarbonActivity` w procesie `:carbon` korzysta z SDL do lifecycle Surface/audio.
Przed startem Java porównuje hash XEX z powyższą wartością i zapisuje tylko
bootstrap `default.xex` w prywatnym folderze (4 857 856 B). Deskryptor oryginalnego
ISO jest duplikowany w module. `OnLoadXexImage` montuje rzeczywisty
`rexlauncher::make_saf_disc` (VFS SDK nad deskryptorem SAF) i przełącza aliasy
`game:` i `d:` na ISO. Od wersji 0.1.1 montowanie nie otwiera ścieżki
`/proc/self/fd` ani nie mapuje całej płyty. Pliki są czytane przez `pread`,
a opcjonalne mapowanie dotyczy tylko żądanego zakresu konkretnego pliku.
Assety pozostają w ISO; nie jest tworzona dodatkowa kopia 6 GB.

`InputRouter` obsługuje fizycznego pada przed dispatch do SDL. OSD i pad używają
tego samego rzeczywistego `InputDriver` ReXGlue, przetestowanego na układzie
big endian `X_INPUT_STATE`. Argumenty `android_gpu_driver_*` trafiają do
oryginalnego loadera SDK przed utworzeniem Vulkan.

## Odtworzenie kompilacji na tym urządzeniu

Po przygotowaniu narzędzi i bibliotek w `/opt/rex-build` (Linux ARM64):

```sh
# Z nadrzędnego folderu wybranego w aplikacji:
python rex-launcher/tools/inspect_xex.py 'Need for Speed - Carbon (Europe) x360.iso' \
  --output rex-launcher/ports/game_europe/default.xex

mc-linux -- /bin/bash -lc 'cd /workspace/rex-launcher/ports/game_europe; \
  LD_PRELOAD=/opt/rex-build/librex-host-shm.so \
  LD_LIBRARY_PATH=/opt/rex-build/host-libs/usr/lib/aarch64-linux-gnu \
  /opt/rex-build/rex-sdk/linux-arm64/bin/rexglue codegen carbon_europe.toml'

node rex-launcher/tools/decode_xex.mjs rex-launcher/ports/game_europe/default.xex \
  rex-launcher/ports/game_europe/image.bin
python rex-launcher/tools/repair_local_branches.py
mc-linux -- /bin/bash /workspace/rex-launcher/tools/build_carbon_module.sh
mc-linux -- /bin/bash /workspace/rex-launcher/tools/build_arm64.sh
```

`librex-host-shm.so` jest wyłącznie adapterem narzędzia analizy: PRoot nie ma
`/dev/shm`, dlatego dla jednego procesu generatora używane jest `memfd_create`.
Nie trafia do APK. ReXGlue nie jest uruchamiany z `--force` w celu pominięcia
walidacji.

CMake modułu przyjmuje `REX_SDK_SOURCE`, `REX_INSTALLED_INCLUDE` i
`REX_RUNTIME_LIB_DIR`; ścieżki można podać jawnie na innym komputerze. Wyjście:
`build/carbon-module/libcarbon_europe.so` i biblioteki runtime. Skrypt pakowania
dołącza je do końcowego APK tylko wtedy, gdy moduł faktycznie istnieje.

Kod gościa od wersji 0.2.0 kompilowany jest z `-O2`, bez LTO, jednym procesem kompilatora z uwagi
na dostępną pamięć telefonu. To wariant eksperymentalny, nie optymalizacja
wydajności gry. Ograniczenia Xenos, funkcje nieodkryte przez analizę, audio,
wyścigi i zapisy muszą zostać zweryfikowane na działającej aplikacji. Nie
deklarujemy przetestowanej rozgrywki.

## Optymalizacja i branding — 0.2.0

Całe 170 plików gościa oraz dodatkowe wejścia i moduł są ponownie kompilowane
z `-O2`. Zachowano `-fno-strict-aliasing`, `-ffp-contract=off`, ABI ARMv8-A
oraz wcześniejsze naprawy rejestracji. Runtime/GPU pozostają tymi samymi
bibliotekami upstream. Testy callbacków, bloków i wejścia XInput wykonano
ponownie z `-O2`. Nie wykonywano pełnego pomiaru FPS, więc nie przypisujemy
zmianie liczbowego wzrostu wydajności.

`tools/extract_iso_icon.py` odczytuje katalog zasobów XEX (nagłówek 0x2FF),
zasób 454107EC i obraz tytułu XDBF 0x8000. Sprawdza zakresy i CRC PNG,
zachowując jego oryginalne bajty. Pochodzenie i SHA-256 zapisuje w
`icon-provenance.json`. Pakowanie `aapt2 --no-crunch` zachowuje hash obrazu;
walidator APK porównuje go z ikoną i metadanymi.
