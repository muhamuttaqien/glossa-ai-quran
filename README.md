# Glossa AI — Qur'an Version

Android reader with Uthmani Arabic text (Hafs), English and Indonesian translations, and verse-by-verse murottal from five reciters. The app interface is in English. Its navigation and reading layout take inspiration from Quran for Android; it does not contain that app's images or code.

## Features

- Browse and search all 114 surahs; open any of the 30 juz at its first ayah.
- Read all 604 fixed pages of the Madinah Mushaf (Hafs) or switch to verse-by-verse translations. Choose English (the default), Indonesian, or English + Indonesian; tap an ayah directly on a Mushaf page to open its translation and actions; taps near a verse boundary open the page's verse list for precise selection. In the combined view, English appears first.
- Save verse bookmarks, resume from the last selected or played verse, share a verse, and switch between light and dark reading. Each translation card keeps Listen, Bookmark, and Share in a compact actions menu. Light is the default.
- Choose Mishary Rashid Alafasy, Mahmoud Khalil Al-Husary, Muhammad Siddiq Al-Minshawi, Abdul Rahman Al-Sudais, or Saud Al-Shuraim.
- Tap an ayah to listen; playback advances through the rest of the surah. Previous, pause/resume and next controls are included.
- All Arabic Mushaf pages are bundled for offline reading. Previously opened surahs, page verse indices, juz starting points and audio URL lists are cached; the verse list for a new page and audio streams need internet. The selected qari and display mode are remembered.

## Run

Open this directory in Android Studio with JDK 17 and Android SDK 35 installed. Sync Gradle, then run the `app` configuration on an Android 8.0+ device or emulator with internet access. Alternatively, run `./gradlew assembleDebug` after configuring the SDK. The debug APK is written to `app/build/outputs/apk/debug/`.

This source project has not been compiled in the current workspace because JDK 17 is not installed here. Validate the page images, verse selection, API and audio on a device before distributing the app.

## Content and rights

The bundled Hafs page artwork comes from the [Quran Images collection](https://github.com/Miftah-Fentaw/Quran-Images), which credits the [Quran.com image generator](https://github.com/quran/quran.com-images) and the Madinah Mushaf. The [King Fahd Qur'an Printing Complex](https://qurancomplex.gov.sa/mushaf-printing) publishes the underlying Madinah Mushaf digital artwork and its usage rights. The PNGs are exact fixed page artwork, not Arabic text reflowed to the screen width. The bundled ayah hit boxes are adapted from [GitCreativeCode/quran-ayah-coords](https://github.com/GitCreativeCode/quran-ayah-coords), licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); see `app/src/main/assets/mushaf/AYAH_COORDS_ATTRIBUTION.txt`. The hit boxes add interaction without changing the artwork. Page verse indices, Arabic text (`quran-uthmani`), Indonesian translation (`id.indonesian`), English translation (`en.sahih`, Saheeh International), and streaming audio are loaded over HTTPS from [AlQuran Cloud](https://alquran.cloud/api). The API response supplies audio URLs and both translation edition names. Its [terms](https://alquran.cloud/terms-and-conditions) require attribution to translators and state that translators and reciters retain their rights. Obtain any permissions required for commercial distribution.

The light reading palette uses warm off-white pages and `#080808` Arabic text. Red `#EF4D4D` is reserved for selected tabs and the main playback button; neutral text and a muted gold mark the reading content. The index has a compact header and a bordered last-read card, translation cards use fine borders, and the light-mode player shares the page's off-white surface. The optional dark theme uses `#080808` (background), `#101010` (surface), `#171717` (cards), and `#F8F8F8` (text).

The launcher icon uses the supplied 128×128 Glossa AI `G.` artwork. Android density resources are generated at 48, 72, 96, 144, and 192 pixels; the original is kept in `design/glossa-icon-128.png`. A higher-resolution original would be preferable for a future 512×512 Play Store listing.

Read Arabic shows one fixed Mushaf page at a time with the original printed line breaks. Swipe right for the next page or left for the previous page; the page number remains visible without navigation buttons. In dark mode, the app remaps the page artwork's colors for light text on a dark background without changing its layout. Tap an ayah on the page to open its translation, listening, bookmark, and sharing actions. Taps near ayah boundaries or outside mapped text open the page's verse list instead, avoiding an uncertain selection. The fixed page artwork is not selectable word by word. The page's verse list and the selected surah's translation are obtained from AlQuran Cloud; a newly opened page requires a connection to select its verses. The first version plays audio while the app is open. Background playback, audio downloads, gapless playback, custom repeat, and full tafsir are not implemented yet.
