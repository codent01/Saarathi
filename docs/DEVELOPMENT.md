# Developer guide: extending Saarathi

## Where to change what

| I want to… | Edit |
|---|---|
| Change how Krishna talks | `rag/PromptBuilder.kt` (`systemPrompt`). Keep it stable between turns: the KV prefix cache depends on it. |
| Add Hinglish words or a new life theme | `assets/lexicon.json` (triggers), then tag verses with the theme in `tools/scripture-compiler/verse_tags.json` and rebuild the DB |
| Tune which verses come up | `tools/scripture-compiler/verse_tags.json` (themes, weight, note) and `rag/Retriever.kt` scoring |
| Add a new screen | `ui/<feature>/`, a route in `ui/navigation/SaarathiNavHost.kt` |
| Add a setting | `data/prefs/SettingsRepository.kt` (`UserSettings` + keys), then `ui/settings/SettingsScreens.kt` |
| Store new user data | Room entity in `data/db/Entities.kt`, DAO in `Daos.kt`, bump `AppDatabase` version with a migration (see `MIGRATION_1_2` for the pattern; copy the SQL from the exported schema JSON) |
| Hand something to the chat from another screen | `AppContainer.pendingPrompt` (+ `pendingSendNow`, `pendingAttachment`, `pendingJournalLink`), consumed once by `ChatViewModel` |
| Change the native sampler or caching | `android/app/src/main/cpp/llama_jni.cpp` |
| Upgrade llama.cpp | `tools/fetch_llama.ps1 -Tag <newtag>` after deleting the old checkout; fix any API drift in `llama_jni.cpp` |

## Phase 2 (done on 2026-11-13: Daily Wisdom, embeddings, reader search). Remaining ideas:

1. **Embeddings.** Add `onnxruntime-android` and a small multilingual sentence model (e.g. `multilingual-e5-small`,
   ~110 MB int8). Precompute verse embeddings in `build_db.py` into a `gita_embeddings` table; in `Retriever` add a
   cosine score next to the theme/FTS scores. Keep the theme map; it is what makes short emotional messages work.
2. **Daily Wisdom.** `WorkManager` periodic worker at a user-chosen hour; pick the day's verse deterministically from
   the date, show a notification (POST_NOTIFICATIONS is already declared), open a small screen with the card and a
   reflection question, "Discuss with Krishna" sets `container.pendingPrompt`.
3. **Automatic memory extraction.** After a conversation ends, ask the model for 0–3 short facts the user explicitly
   stated about goals or ongoing struggles, show them for approval, store as `MemoryEntity`. Never auto-save.
4. **Scripture reader search** over `gita_fts` and `mb_fts` with a search bar in `LibraryScreen`.
5. **Conversation export** (JSON + Markdown) through the existing FileProvider.

## Phase 3 (done on 2026-11-13: journal, Karma Steps, Decision Compass, Gita journey)

How the four features are wired, for whoever extends them:

* **My Kurukshetra journal.** `JournalEntryEntity` + `JournalDao` (`data/db`), `JournalRepository`
  (`Phase3Repositories.kt`), screens in `ui/journal/JournalScreens.kt`, routes `journal` and `journal/edit/{id}`.
  "Reflect with Krishna" saves the entry, then sets `container.pendingAttachment` (an `Attachment` of kind
  `journal`), `pendingPrompt` and `pendingJournalLink`; `ChatViewModel` picks all three up, and when the first
  message creates the conversation it writes `linkedConversationId` back to the entry. `PromptBuilder.userTurn`
  labels a `journal` attachment differently from OCR/PDF text; the system prompt is unchanged.
* **Karma Steps.** `karma/KarmaSteps.extract` (pure, unit-tested) finds a numbered or dashed list of 2–7 items in
  a reply. `ChatScreen` shows "Save as Karma Steps (n)" under such a bubble and "Saved as Karma Steps" once rows
  exist for that `messageId`. `KarmaStepEntity` rows carry `conversationId`, `messageId`, `done`, `doneAt`. The
  empty-chat card (`KarmaStepsCard`) lists open steps plus those finished today; `ui/karma/KarmaStepsScreen.kt`
  lists everything. There are deliberately no reminders and no streak copy.
* **Decision Compass.** `compass/DecisionCompass` holds the seven steps and `compose()` (unit-tested);
  `ui/compass/DecisionCompassScreen.kt` collects answers, creates an `arjuna` conversation titled from the
  situation and sets `pendingPrompt` + `pendingSendNow`, so the chat sends the composed message as soon as the
  model is ready (otherwise it stays in the composer). `KrishnaOrchestrator` keeps a title another screen set.
* **Gita journey.** `VerseReadEntity` (one row per verse record) + `JourneyRepository`. The reader marks a verse
  read when its card has been fully on screen (`snapshotFlow` over `LazyListState.layoutInfo`), shows a progress
  bar in the chapter header and a reflection card at the end with "Write in journal", "Discuss", "Mark chapter as
  read" / "Read again". `journey/GitaJourney.reflectionPrompts` assigns each chapter a prompt from its own
  `chapters.json` themes with a small backtracking search so all 18 prompts differ (unit-tested).
  `ui/journey/JourneyScreen.kt` is the overview; it is reachable from the chat menu and the library.
* **Database** is at version 2 (`AppDatabase.MIGRATION_1_2`; the SQL matches `schemas/.../2.json` exactly).
* **Phone check:** `tools/phone_test_phase3.ps1` drives all four features over adb.

Ideas not done: journal export, reminders for Karma Steps (opt-in only, if ever), a Decision Compass history that
links back to its conversation, and reading-time stats for the journey.

## Phase 4

Voice is **deferred by the owner (2026-11-18)**: do not start it unless asked. The notes are kept for later at the
end of this section.

* **Vision**: llama.cpp supports Gemma 3's image projector (`mmproj` GGUF). Extend `LlamaNative.loadModel` with an
  optional projector path and add an image-token path in `generate`. Until then the app only OCRs images and says so.
* **Encrypted backup**: zip the Room DB + bookmarks + memories, encrypt with AES-GCM using a key from Android
  Keystore wrapped by a user passphrase, export via SAF.

Deferred, for later: **Voice** — `sherpa-onnx` Android AAR for streaming speech-to-text and Piper text-to-speech,
with the speech models as optional user imports like GGUF files. The `RECORD_AUDIO` permission is already declared.

## Conventions

* No network code anywhere; CI should grep the merged manifest for `INTERNET` and fail if present.
* Anything shown as scripture must come from `ScriptureRepository`. Never render model text inside a `ShlokaCard`.
* Keep the UI thread free: the native engine has its own thread; database and file work use `Dispatchers.IO`.
