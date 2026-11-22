# Status, limitations and device matrix

## Phase 1 deliverables

| Item | State |
|---|---|
| Native Android app (Kotlin, Compose, Room) | done |
| Krishna avatar, icon, splash | done (user-chosen artwork in `assets/art/krishna_avatar.png`) |
| Divine Ember theme + Kurukshetra + Eternal Calm | done |
| WhatsApp-style chat UI with streaming, actions, starters | done |
| Real offline inference (llama.cpp JNI, GGUF) | done, pending device verification (see TESTING.md) |
| Bhagavad Gita import | done at compile time (bundled DB) + on-device edition pack import + PDF linking |
| Mahabharata import | done at compile time (bundled DB, 9.5k attributed passages) + PDF linking |
| Local scripture retrieval (themes + FTS + explicit refs) | done |
| Verified scripture references (cards from DB only) | done |
| English / Hinglish responses | done via prompt; quality depends on the model |
| Local conversation history, search, delete | done |
| Attachments (image OCR, PDF text, text files) | done (Phase 2 item delivered early) |
| Arjuna Mode (guided reflection) | done as a conversation mode with its own prompt (Phase 2 item delivered early) |
| Memory controls (manual memories, toggle, delete) | done (manual only; no automatic memory extraction) |
| Bookmarks: verses and messages, Saved screen | done |
| Scripture library + chapter reader + in-app PDF page viewer | done |
| Settings: theme, text size, language, length, style, model params, data deletion | done |
| Diagnostics screen | done |
| **Phase 2** Daily Wisdom (local notification, deterministic verse of the day, reflection, discuss) | done |
| **Phase 2** Embedding re-ranking (MiniLM ONNX, verse vectors precomputed) | done |
| **Phase 2** Scripture reader search (Gita + Mahabharata, bookmarks for passages) | done |
| **Phase 3** My Kurukshetra journal (private entries, tags, search, "Reflect with Krishna" as a journal attachment, linked conversation) | done |
| **Phase 3** Karma Steps (save a reply's numbered/dashed list, tick them on the empty-chat screen, Karma Steps screen) | done, no reminders by design |
| **Phase 3** Decision Compass (seven guided questions composed into one Arjuna-Mode conversation) | done |
| **Phase 3** 18-chapter Gita journey (per-chapter verses-read progress, one reflection prompt per chapter, overview screen) | done |
| Airplane-mode test | not yet run (the phone test below ran with Wi-Fi on; the app has no INTERNET permission either way) |
| First device run (Moto G82 5G, Snapdragon 695, 8 GB) | done on 2026-11-13, see TEST-RESULTS.md |

## Known limitations

* **Measured on one phone so far** (Moto G82 5G, Snapdragon 695, 8 GB RAM, Android 17). With Gemma 3 1B: model load 2 s,
  first word 6–8 s on a fresh turn, 0.7 s for a short greeting, about 6.5 tokens/s while writing. The 4B model loads
  (2.9 GB RSS) but is several times slower on this mid-range chip, so 4B is a flagship-only choice in practice.
* **The 1B model rarely writes verse references itself.** It talks warmly and stays in character, but tends to explain a
  teaching without citing "Gita 2.47". The app compensates: verses the user asks for by number are always shown, and the
  strongest retrieved verse is offered as a separate "related teaching" card. A 4B model follows the citation rule far better.
* **Bundled English translation is Annie Besant's 1922 text** (public domain). It is accurate but old-fashioned.
  Import your own edition pack to see modern wording; 8 of 700 verses have no Besant text because of footnote
  formatting on Wikisource and show the imported edition only.
* **Edition pack coverage:** 662 of 700 verses were located in the Macmillan scan (25 verse headings were missed by
  OCR, and 19 translations came out empty). Those verses fall back to Besant.
* **On-device import of the scanned Gita is not attempted.** The spec asked for it; the scan has no usable Sanskrit
  text layer and phone OCR of 1972 Devanagari would produce wrong verses. The compile-time pipeline is the honest
  alternative. Personal PDFs with a text layer import fine as attachments.
* **Embeddings are English-only.** The bundled MiniLM model understands English well and Hinglish poorly; the
  curated theme map still carries Hinglish messages, and embeddings only re-rank. A multilingual model would add
  ~100 MB to the APK and was left out for now.
* **Small models hallucinate.** The app blocks fabricated verse cards, but the model can still say something
  inaccurate about the Mahabharata in prose. The prompt forbids it; it cannot prevent it entirely.
* **Prompt processing is the slow part, and it is mitigated three ways.** The system prompt is evaluated into the KV
  cache right after the model loads; every stored turn is replayed verbatim so the cache prefix keeps matching (a
  fourth turn on the test phone reused 1911 of 1924 tokens); and only the newest message is processed. Thread count
  defaults to the number of performance cores because one thread on an efficiency core slows the whole model down.
* **Vision and encrypted backup** are not started (Phase 4). **Voice is deferred** by the owner and will not be
  built until asked.
* **Karma Steps depend on the model writing an explicit list.** The prompt tells Krishna to avoid bullet lists, so
  the "Save as Karma Steps" action appears only when a reply contains a numbered or dashed list of 2–7 items (the
  1B model does this reliably when asked for "a numbered list"). Advice given in prose is not turned into steps.
* **Journey progress counts verse cards that have been fully on screen** in the reader (combined verses such as
  1.21-22 are one record, so chapter 1 has 39 records for 47 verses). "Mark chapter as read" is there for chapters
  read elsewhere; "Read again" resets a chapter.
* **Decision Compass is a composer, not an engine.** The seven answers become one message in Arjuna Mode; the
  quality of the questioning that follows is the model's. The last step is a reflection and the app never states
  a verdict.
* **Database schema is version 2.** Phones with the Phase 1/2 build migrate in place (three new tables); nothing
  is deleted. Settings has "Clear journal & steps" and "Reset Gita journey" next to the existing data controls.
* **Daily Wisdom relies on WorkManager**, which some phone makers throttle aggressively; the verse of the day is
  always reachable from the chat menu even if a notification is delayed.
* **Release signing** uses the debug key so the APK can be sideloaded; replace before any distribution.

## Minimum and recommended devices

| | Minimum | Recommended |
|---|---|---|
| Android | 9 (API 28) | 12+ |
| CPU | 64-bit ARMv8.2 with dot-product (Snapdragon 670 / 710 / 845 or newer, Dimensity 700+, Exynos 9810+) | Snapdragon 8 Gen 1+, Dimensity 8000+ / 9000+, Tensor G2+ |
| RAM | 6 GB (1B model) | 12 GB (4B model); 8 GB phones should use 1B |
| Storage free | 2 GB (1B) | 4 GB (4B) |
| Model | gemma-3-1b-it Q4_K_M | gemma-3-4b-it Q4_K_M on Snapdragon 8-series / Dimensity 9000-class chips only |

Phones with older cores (e.g. Snapdragon 636/660, Helio P60) are detected and refused with a clear message.
