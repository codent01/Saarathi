<p align="center">
  <img src="assets/art/krishna_avatar.png" alt="Saarathi Logo" width="150"/>
</p>

<h1 align="center">🙏 Saarathi</h1>
<p align="center">
  <strong>Your Offline AI Krishna Companion</strong><br/>
  Chat with Krishna, explore the Bhagavad Gita, journal your thoughts — all powered by on-device AI. No internet required.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white"/>
  <img src="https://img.shields.io/badge/llama.cpp-On_Device_AI-FF6F00?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Version-0.3.0-blue?style=for-the-badge"/>
</p>

---

## What is Saarathi?

**Saarathi** (सारथी — "charioteer") is a fully offline Android app that lets you have meaningful conversations with an AI persona inspired by Lord Krishna's teachings from the **Bhagavad Gita** and **Mahabharata**. 

*Every Arjuna needs a Saarathi.*

Unlike cloud-based chatbots, Saarathi runs a small language model **entirely on your device** using llama.cpp, ensuring complete privacy. Every verse cited is retrieved from a local scripture database and verified before display — the AI never fabricates scripture.

---

## Features

### 💬 Chat with Krishna
Engage in thoughtful conversations with an AI Krishna persona. Ask questions about life, dharma, purpose, relationships, and decisions. Every response is grounded in authentic scriptural references.

### 📖 Scripture Library
- **18-Chapter Gita Reader** — Browse all chapters and verses with Sanskrit text, transliteration, and English translation
- **PDF Page Viewer** — Read the full Bhagavad Gita As It Is
- **Shloka Cards** — Save and revisit your favorite verses

### 🌅 Daily Wisdom
Receive daily notifications with curated verses and teachings from the Gita to start your day with clarity.

### 📓 Private Journal
Write your reflections, track spiritual insights, and document your personal journey — all stored locally.

### 🧭 Decision Compass
Facing a tough choice? The Decision Compass uses Gita principles to help you think through decisions with structured wisdom.

### 🪜 Karma Steps Tracker
Set intentions and track your daily karma (actions) aligned with your personal dharma.

### 📚 18-Chapter Gita Journey
A guided, chapter-by-chapter study plan through the entire Bhagavad Gita with progress tracking.

### 📎 Smart Attachments
Import PDFs and images — the app extracts text using ML Kit OCR (with Devanagari support) and uses it as context for conversations.

### 🔒 Fully Offline & Private
- No internet permission — your data never leaves your device
- No cloud APIs, no telemetry, no tracking
- All AI inference runs on-device via llama.cpp

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                │
│         Material 3 · Dynamic Theming · Coil         │
├─────────────────────────────────────────────────────┤
│                  Navigation Layer                   │
│    Chat · Library · Journal · Karma · Settings      │
├─────────────────────────────────────────────────────┤
│              RAG Pipeline (Retrieval)                │
│  QueryAnalyzer → Lexicon → Retriever → Embedder    │
│  → RetrievalScoring → KrishnaOrchestrator           │
│  → PromptBuilder → LlamaEngine                      │
├─────────────────────────────────────────────────────┤
│              On-Device AI Engine                    │
│      llama.cpp (C++ via JNI) · ONNX Embeddings     │
├─────────────────────────────────────────────────────┤
│                Local Data Layer                     │
│    Room DB · DataStore Prefs · Scripture SQLite     │
└─────────────────────────────────────────────────────┘
```

**Key Design Principles:**
- **Offline-only** — INTERNET permission is explicitly removed
- **Citation, not generation** — the model never writes scripture; it only cites verified verses
- **Public domain content** — all scripture texts are from public-domain sources bundled in the APK
- **Rule-based retrieval first** — lexicon matching before semantic search for accuracy

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3, Coil |
| **AI Inference** | llama.cpp (C++ via JNI/CMake) |
| **Embeddings** | ONNX Runtime |
| **OCR** | ML Kit Text Recognition + Devanagari |
| **Database** | Room (conversations, journal, karma, journey) |
| **Preferences** | DataStore |
| **Background** | WorkManager |
| **Navigation** | Navigation Compose |
| **Build** | Gradle KTS, CMake, NDK 27.2 |
| **Min SDK** | 28 (Android 9) |
| **Target SDK** | 35 |
| **Java** | 17 |

---

## Project Structure

```
Saarathi/
├── android/
│   └── app/
│       └── src/main/
│           ├── java/com/saarathi/        # Kotlin source
│           │   ├── ui/                    # Compose screens
│           │   ├── data/                  # Room entities & DAOs
│           │   ├── ai/                    # RAG pipeline & LLM engine
│           │   ├── scripture/             # Verse retrieval & validation
│           │   └── service/               # WorkManager & notifications
│           ├── cpp/                       # llama.cpp JNI bridge
│           └── res/                       # Resources & assets
├── assets/
│   └── art/                               # App artwork
├── scripture-sources/                     # Public-domain texts (not in git)
├── tools/
│   ├── fetch_llama.ps1                    # Download llama.cpp
│   ├── make_icons.ps1                     # Generate app icons
│   └── scripture-compiler/                # Build scripture database
└── docs/
    ├── ARCHITECTURE.md
    ├── DEVELOPMENT.md
    ├── SETUP.md
    └── TESTING.md
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- NDK 27.2 (installed via SDK Manager)
- CMake 3.22+
- A GGUF model file (e.g., Phi-3-mini Q4_K_M)

### Build Steps

1. **Clone the repo**
   ```bash
   git clone https://github.com/codent01/Saarathi.git
   cd Saarathi
   ```

2. **Fetch llama.cpp sources**
   ```powershell
   ./tools/fetch_llama.ps1
   ```

3. **Open in Android Studio**
   - Open the `android/` directory
   - Sync Gradle and let it download dependencies

4. **Build & Run**
   - Select a device/emulator (API 28+)
   - Build → Run

5. **Load a model**
   - On first launch, the app guides you through downloading/loading a GGUF model
   - Recommended: Phi-3-mini (Q4_K_M quantization) for best quality/size balance

---

## Documentation

| Document | Description |
|----------|------------|
| [SETUP.md](docs/SETUP.md) | Environment setup and build instructions |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design and component details |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | Development workflow and guidelines |
| [TESTING.md](docs/TESTING.md) | Testing strategy and instructions |
| [LICENSES.md](docs/LICENSES.md) | Third-party licenses and attributions |

---

## Roadmap

- [x] Phase 1 — Core chat with Krishna persona
- [x] Phase 2 — Scripture library and verse cards
- [x] Phase 3 — Journal, Karma Steps, Decision Compass, Daily Wisdom
- [ ] Phase 4 — Community features, enhanced Gita Journey, sharing

---

## Privacy

Saarathi is designed with privacy as a core principle:
- **No internet access** — the app has no INTERNET permission
- **No data collection** — zero analytics, telemetry, or tracking
- **On-device AI** — all inference happens locally
- **Local storage only** — conversations, journal, and data stay on your device

---

## License

This project uses public-domain scripture texts. See [LICENSES.md](docs/LICENSES.md) for third-party library attributions.

---

<p align="center">
  <i>"You have the right to work, but never to the fruit of work."</i><br/>
  <strong>— Bhagavad Gita 2.47</strong>
</p>
