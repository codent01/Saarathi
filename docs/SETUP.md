# Setup and build guide

## 1. Toolchain (Windows)

Everything below was installed on the development PC with winget. Paths are the defaults winget uses.

| Tool | Version | Path |
|---|---|---|
| Microsoft OpenJDK | 17.0.21 | `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` |
| Android SDK command-line tools | latest | `C:\Android\Sdk\cmdline-tools\latest` |
| Android platform 35, build-tools 35.0.0, platform-tools | | `C:\Android\Sdk` |
| NDK | 27.2.12479018 | `C:\Android\Sdk\ndk\27.2.12479018` |
| CMake | 3.22.1 | `C:\Android\Sdk\cmake\3.22.1` |
| Git | 2.56 | `C:\Program Files\Git` |
| Python | 3.12 | `%LOCALAPPDATA%\Programs\Python\Python312` (scripture compiler only) |
| Tesseract OCR | 5.4 | `C:\Program Files\Tesseract-OCR` (scripture compiler only, for ~20 pages without a text layer) |

`android/local.properties` points at the SDK and `android/gradle.properties` pins the JDK, so no environment
variables are required to build. If you move the SDK or JDK, update those two files.

## 2. Fetch llama.cpp

The native inference engine is a shallow git checkout pinned to a tag:

```powershell
powershell -ExecutionPolicy Bypass -File tools\fetch_llama.ps1 -Tag b12319
```

It lands in `android/app/src/main/cpp/llama.cpp` and is ignored by git.

## 3. Build the scripture database

Only needed when the source PDFs or the curated files change; the generated `scripture.db` is committed.

```powershell
cd tools\scripture-compiler
python -m pip install pymupdf pytesseract pillow indic_transliteration beautifulsoup4
python compile_all.py
```

Steps and outputs:

| Script | Input | Output |
|---|---|---|
| `parse_shlokas.py` | `Bhagavad_Gita_As_It_Is-Only_Shlokas.pdf` | `work/gita_verses.json` (700 verses, IAST + generated Devanagari) |
| `fetch_besant.py` | Wikisource (public domain, one-time download) | `work/besant.json` |
| `parse_gita_edition.py` | Macmillan 1972 scan (text layer + OCR fallback) | `work/gita_edition.json` (page map, translations, purports) |
| `parse_mahabharata.py` | Ganguli Mahabharata PDF | `work/mb_passages.json` (9.5k attributed passages) |
| `build_db.py` | all of the above + `verse_tags.json` + `chapters.json` | `android/.../assets/scripture/scripture.db` and `dist/gita_as_it_is_pack.json` |

After rebuilding the database bump the number in `android/app/src/main/assets/scripture/scripture.version` so
installed apps replace their copy.

## 4. Build the APK

```powershell
cd android
.\gradlew.bat assembleDebug
```

The first build downloads Gradle 8.11 and the dependencies and compiles llama.cpp for `arm64-v8a` and `x86_64`
(x86_64 is only for the emulator; drop it from `saarathi.abis` in `gradle.properties` for a smaller APK). Output:
`android/app/build/outputs/apk/debug/app-debug.apk`.

A sideloadable release build (debug-signed; replace the signing config before distributing):

```powershell
.\gradlew.bat assembleRelease
```

## 5. Install on a phone

```powershell
C:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

## 6. Install a language model

Models are plain GGUF files. Recommended:

| Model | File | Size | Minimum RAM |
|---|---|---|---|
| Gemma 3 1B instruct, Q4_K_M (default) | `gemma-3-1b-it-Q4_K_M.gguf` | 0.8 GB | 6 GB; good on mid-range chips |
| Gemma 3 4B instruct, Q4_K_M (flagships) | `gemma-3-4b-it-Q4_K_M.gguf` | 2.4 GB | 12 GB and a Snapdragon 8-class CPU |

Both are in `models/` on the development PC (downloaded from the `ggml-org` repositories on Hugging Face; Gemma
is distributed under the Gemma Terms of Use, see `docs/LICENSES.md`).

Copy the file anywhere on the phone (for example `Download/`), then in the app open **Setup → Import .gguf**, pick the
file, and tap **Load model**. The file is copied into app-private storage, so the original can be deleted afterwards.

Over USB: `adb push models\gemma-3-4b-it-Q4_K_M.gguf /sdcard/Download/`

## 7. Optional: your own Gita edition

`dist/gita_as_it_is_pack.json` was built from your *Bhagavad-gita As It Is* PDF. Copy it to the phone and use
**Setup → Import edition pack**. Verse cards then prefer that edition's translation, show its page number, and the
expanded card shows the purport, clearly labelled as commentary. The pack is personal: it is never bundled in the
APK and should not be redistributed.

To open the original page from a card, also **Link Gita PDF** (the Macmillan scan) and **Link Mahabharata PDF** in
Setup. The app renders pages itself with Android's PdfRenderer; no external viewer is needed.

## 8. Driving the phone from the PC

`tools/phone_test.ps1 -Message "..."` sends a message through adb and prints first-token and total times plus the
engine log. Add `-NoRestart` to continue the current conversation. The debug build also allows copying a model
straight into app storage, which skips the file picker:

```powershell
adb shell "cat /sdcard/Download/model.gguf | run-as com.saarathi.app sh -c 'cat > files/models/model.gguf'"
```

## 9. Emulator smoke test

Create an x86_64 AVD with at least 4 GB RAM and use the 1B model. Generation on the emulator is slow but exercises the
whole pipeline. Real performance numbers only come from a physical arm64 phone.
