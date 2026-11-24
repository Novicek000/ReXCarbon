#!/usr/bin/env python3
"""Maintain matching English/default and Polish launcher resource keys."""
from pathlib import Path
from xml.sax.saxutils import escape
ROOT=Path(__file__).resolve().parents[1]/'app/src/main/res'
STRINGS={
'app_name':('ReX Carbon','ReX Carbon'),
'garage':('Garage','Garaż'),'display':('Display','Obraz'),'drivers':('Drivers','Sterowniki'),'tools':('Tools','Narzędzia'),
'own_night':('OWN THE\nNIGHT.','PRZEJMIJ\nNOC.'),'hero_caption':('The canyon is calling.','Kanion czeka na Ciebie.'),
'native_port':('XBOX 360 · RECOMPILED FOR ANDROID','XBOX 360 · REKOMPILACJA NA ANDROIDA'),
'game_image':('GAME IMAGE','OBRAZ GRY'),'no_iso':('Choose your Carbon ISO to get started','Wybierz ISO Carbon, aby rozpocząć'),
'choose_iso':('Choose ISO','Wybierz ISO'),'change_iso':('Change ISO','Zmień ISO'),'launch':('START ENGINE  ›','URUCHOM SILNIK  ›'),
'ready':('READY TO RACE','GOTOWY DO JAZDY'),'setup':('SET UP YOUR RIDE','PRZYGOTUJ SWÓJ WÓZ'),
'ready_body':('Your game, your settings. Hit the streets.','Twoja gra, Twoje ustawienia. Ruszaj na ulice.'),
'setup_body':('Select your own Xbox 360 Carbon Europe ISO. Your settings and saves stay on this device.','Wybierz własne ISO Xbox 360 Carbon Europe. Ustawienia i zapisy pozostają na tym urządzeniu.'),
'quick_setup':('QUICK SETUP','SZYBKIE USTAWIENIA'),'resolution':('Output resolution','Rozdzielczość obrazu'),
'resolution_native':('Native screen · %1$d × %2$d','Natywna ekranu · %1$d × %2$d'),
'resolution_custom':('Custom · %1$d × %2$d','Własna · %1$d × %2$d'),
'custom':('Custom resolution','Własna rozdzielczość'),'width':('Width (px)','Szerokość (px)'), 'height':('Height (px)','Wysokość (px)'),
'apply':('Apply','Zastosuj'),'cancel':('Cancel','Anuluj'),'edit_custom':('Edit custom size','Zmień własny rozmiar'),
'bad_size':('Use 320–8192 px width, 180–8192 px height, width ≥ height and at most 16,777,216 pixels.','Szerokość: 320–8192 px, wysokość: 180–8192 px, szerokość ≥ wysokość i maks. 16 777 216 pikseli.'),
'scaling':('Image scaling','Skalowanie obrazu'),'aspect':('Keep aspect ratio','Zachowaj proporcje'),'stretch':('Stretch','Rozciągnij'),'integer':('Integer scaling','Skalowanie całkowite'),
'refresh':('Game refresh rate','Częstotliwość gry'),'orientation':('Game orientation','Orientacja gry'),'sensor':('Automatic · landscape sensor','Automatyczna · czujnik poziomu'),
'landscape':('Landscape','Pozioma'),'reverse_landscape':('Reverse landscape','Odwrócona pozioma'),
'video_hint':('Video changes apply next time you launch. Higher resolutions can reduce performance.','Zmiany obrazu działają od następnego uruchomienia. Wyższa rozdzielczość może obniżyć wydajność.'),
'current_driver':('CURRENT GPU DRIVER','AKTUALNY STEROWNIK GPU'),'system_driver':('System driver','Sterownik systemowy'),
'import_driver':('Import Turnip / Mesa ZIP','Importuj ZIP Turnip / Mesa'),'use_system':('Use system driver','Użyj sterownika systemowego'),
'driver_hint':('Driver changes apply to the next game session. Use an Android ARM64 driver compatible with your GPU.','Zmiana sterownika działa w następnej sesji gry. Użyj sterownika Android ARM64 zgodnego z Twoim GPU.'),
'system_selected':('System driver selected for the next session.','Wybrano sterownik systemowy dla następnej sesji.'),
'export_logs':('Export logs (.txt)','Wyciągnij logi (.txt)'),'inspect_iso':('Check ISO / XDVDFS','Sprawdź ISO / XDVDFS'),
'diagnostics':('Vulkan & controller diagnostics','Diagnostyka Vulkan i kontrolera'),
'detailed_logs':('Detailed runtime logs','Szczegółowe logi runtime'),
'logs_hint':('Leave off for less CPU and storage overhead. Turn on before reproducing a problem. Errors are always recorded.','Wyłączone zmniejszają obciążenie CPU i pamięci. Włącz przed odtworzeniem błędu. Błędy są zawsze zapisywane.'),
'build_info':('ReX Carbon 0.3.0 · ARM64 · Europe 573FDB37','ReX Carbon 0.3.0 · ARM64 · Europe 573FDB37'),
'working':('Working…','Przetwarzanie…'),'prepared':('Engine ready. Loading Carbon…','Silnik gotowy. Ładowanie Carbon…'),
'choose_first':('Choose an ISO to start.','Wybierz ISO, aby rozpocząć.'),'session_end':('Back in the garage.','Powrót do garażu.'),
'start_failed':('Game could not start: %1$s. Export logs for details.','Nie udało się uruchomić gry: %1$s. Wyciągnij logi, aby sprawdzić szczegóły.'),
'error':('Error: %1$s','Błąd: %1$s'),'installed':('Installed: %1$s. Ready for the next session.','Zainstalowano: %1$s. Gotowy dla następnej sesji.'),
'logs_saved':('Logs saved.','Logi zapisane.'),'cannot_iso':('Cannot open ISO','Nie można otworzyć ISO'),
'cannot_save_iso':('Cannot save ISO selection','Nie zapisano wyboru ISO'),'no_module':('Game module unavailable','Moduł gry niedostępny'),
'menu':('Menu','Menu'),'osd_description':('Xbox 360 touch controller','Kontroler dotykowy Xbox 360'),
'osd_hidden':('Touch controls hidden','Sterowanie dotykowe ukryte'),'osd_active':('Touch controls active','Sterowanie dotykowe aktywne'),
'diag_start':('Opening ISO and initializing Vulkan…','Otwieranie ISO i inicjalizacja Vulkan…'),
'controller_touch':('Touch controller','Kontroler dotykowy'),
'xex_mismatch':('This module requires Carbon Europe, Media ID 573FDB37. Selected XEX SHA-256: %1$s','Moduł wymaga Carbon Europe, Media ID 573FDB37. SHA-256 wybranego XEX: %1$s'),
'gpu_label':('GPU','GPU'),'saved_size':('Custom resolution saved.','Zapisano własną rozdzielczość.'),
'boot_hint':('No extraction. Assets stream directly from your ISO.','Bez rozpakowywania. Dane są czytane bezpośrednio z ISO.'),
}
STRINGS.update({'driver_folder': ('Cannot create driver directory', 'Nie można utworzyć katalogu sterowników'), 'install_folder': ('Cannot create installation directory', 'Nie można utworzyć katalogu instalacji'), 'cannot_zip': ('Cannot open ZIP', 'Nie można otworzyć ZIP'), 'zip_path': ('Invalid ZIP path', 'Nieprawidłowa ścieżka ZIP'), 'zip_duplicate': ('ZIP path outside directory or duplicate', 'ZIP: ścieżka poza katalogiem lub duplikat'), 'zip_folder': ('Cannot create ZIP directory', 'Nie można utworzyć katalogu ZIP'), 'zip_large': ('ZIP package is too large', 'Zbyt duża paczka ZIP'), 'zip_meta': ('Missing meta.json in ZIP root', 'Brak meta.json w katalogu głównym ZIP'), 'driver_api': ('Driver requires Android API %1$d', 'Sterownik wymaga Android API %1$d'), 'driver_library': ('Invalid libraryName', 'Nieprawidłowe libraryName'), 'driver_abi': ('Library is not ELF64 AArch64', 'Biblioteka nie jest ELF64 AArch64'), 'driver_save': ('Cannot save driver settings', 'Nie zapisano ustawień sterownika'), 'carbon_folder': ('Cannot create Carbon data directory', 'Nie można utworzyć katalogu danych Carbon'), 'log_target': ('Cannot open log output file', 'Nie można otworzyć pliku docelowego logów')})
STRINGS.update({'image_enhancements': ('IMAGE ENHANCEMENTS', 'ULEPSZENIA OBRAZU'), 'filter_off': ('Off', 'Wyłączone'), 'fsr_quality': ('FSR 1 quality', 'Jakość FSR 1'), 'ultra_quality': ('Ultra Quality · 1.3×', 'Bardzo wysoka · 1,3×'), 'quality': ('Quality · 1.5×', 'Jakość · 1,5×'), 'balanced': ('Balanced · 1.7×', 'Zrównoważona · 1,7×'), 'performance': ('Performance · 2×', 'Wydajność · 2×'), 'sharpness': ('FSR / CAS sharpness · %1$d%%', 'Ostrość FSR / CAS · %1$d%%'), 'antialiasing': ('Anti-aliasing', 'Wygładzanie krawędzi'), 'anisotropy': ('Anisotropic filtering', 'Filtrowanie anizotropowe'), 'game_default': ('Game default', 'Ustawienie gry'), 'internal_scale': ('Internal render scale', 'Wewnętrzna skala renderowania'), 'filter_hint': ('With FSR 1, resolution selects the final output and quality selects a lower input resolution. CAS sharpens at the selected resolution. Sharpness affects both filters. FSR 1 does not generate frames. Higher render scales and filtering can reduce FPS and use more GPU memory. All changes apply next launch.', 'Przy FSR 1 rozdzielczość określa obraz końcowy, a jakość — niższą rozdzielczość wejściową. CAS wyostrza obraz w wybranym rozmiarze. Ostrość dotyczy obu filtrów. FSR 1 nie generuje klatek. Wyższa skala i filtrowanie mogą obniżyć FPS i zwiększyć użycie pamięci GPU. Zmiany działają od następnego uruchomienia.'), 'filter_error': ('Image filter failed: %1$s. Try turning the filter off in Display.', 'Błąd filtra obrazu: %1$s. Spróbuj wyłączyć filtr w zakładce Obraz.')})
for language,index in [('values',0),('values-pl',1)]:
 folder=ROOT/language;folder.mkdir(parents=True,exist_ok=True)
 def xml(s):return escape(s).replace("'","\\'").replace('\n','\\n')
 (folder/'strings.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+''.join(f'    <string name="{key}">{xml(value[index])}</string>\n' for key,value in STRINGS.items())+'</resources>\n')
print(f'{len(STRINGS)} resource strings per language')
