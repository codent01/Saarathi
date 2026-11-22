# Test results — 2026-11-12 (Phase 1 build)

## Build

| Target | Result | Output |
|---|---|---|
| `assembleDebug` (arm64-v8a + x86_64, unstripped) | success | `android/app/build/outputs/apk/debug/app-debug.apk` (176 MB) |
| `assembleRelease` (minified, debug-signed) | success | `android/app/build/outputs/apk/release/app-release.apk` (95 MB), copy in `dist/` |
| Native llama.cpp b12319 via NDK 27.2 | compiled for both ABIs | `libsaarathi_llama.so` |
| Merged manifest permissions | `POST_NOTIFICATIONS`, `RECORD_AUDIO`, WorkManager's `WAKE_LOCK`/`RECEIVE_BOOT_COMPLETED`/`FOREGROUND_SERVICE` | **no `INTERNET`, no `ACCESS_NETWORK_STATE`** (checked with `aapt dump permissions`) |

## JVM unit tests (`testDebugUnitTest`)

`com.saarathi.app.rag.QueryAnalyzerTest`: 7 tests, 0 failures (0.2 s).

| Test | Checks |
|---|---|
| emotionalMessageIsSupportWithThemes | "working very hard but nothing is going right… giving up" → SUPPORT with failure/depression/work themes |
| hinglishFearIsDetected | "mujhe bahut dar lagta hai exam ka" → SUPPORT, fear |
| greetingIsCasual | "Hare Krishna!", "hi" → CASUAL (fixed: greeting outranks the scripture-question trigger) |
| explicitVerseReferenceIsScripture | "chapter 2 verse 47", "gita 18.66" → SCRIPTURE with the parsed refs |
| characterQuestionIsScripture | "Why did Karna fight for Duryodhana?" → SCRIPTURE, both names found |
| crisisIsFlagged | English and Hinglish crisis phrases → CRISIS |
| shortFollowUpInheritsThemes | "but what if I fail?" after a work/fear message keeps the thread's themes |

## Scripture compiler checks

| Check | Result |
|---|---|
| Verses parsed from the Slokas PDF | 657 records = 700 verses, 18 chapters, counts per chapter match the standard edition |
| Devanagari generation | spot-checked 2.47, 1.21-22, 18.66, 2.29 against printed shlokas; sandhi joins and avagraha correct |
| Besant translation | 692/700 verses (missing 3.42, 6.23, 8.20, 9.14, 10.31, 11.8, 17.19, 18.14, 18.32 due to Wikisource footnote layout) |
| Edition page map (Macmillan scan) | 675/700 verses located; 19 translations empty; 25 verse headings not found |
| Mahabharata | 9,554 passages, 18 books, speakers attributed (Vaisampayana 2.3k, Bhishma 2.1k, Sanjaya 1.2k, Krishna 137 after alias merge) |
| Database queries | FTS on "fear/afraid" and on "karna duryodhana" return rows; 2.47 card has Sanskrit, IAST, translation, pages 6 (Slokas) and 161 (edition) |

## Phone run — 2026-11-13, Moto G82 5G (Snapdragon 695: 2×A78 + 6×A55, 8 GB RAM, Android 17 / API 37)

Driven over adb with `tools/phone_test.ps1`; debug build with optimized native code.

| Step | Result |
|---|---|
| Install, onboarding, Setup | ok; scripture database self-installed (657 verse rows, 9,554 passages) |
| Gemma 3 4B Q4_K_M load | ok in 5 s, 2.9 GB RSS, but generation several times too slow for this chip (dropped) |
| Gemma 3 1B Q4_K_M load | 2 s; warm-up of the 514-token system prompt runs in the background |
| Turn 1 "I am afraid of changing my job" | SUPPORT, themes work/change/fear, prompt 725 tokens (507 reused), first word 8.5 s, 21 s total |
| Turn 2 "But what if I fail?" | context kept (themes doubt/failure/work/change), prompt 1014 (852 reused), first word 6.7 s, related-teaching card 6.38 |
| Turn 3 "What does Gita 2.47 mean?" | SCRIPTURE, 2.47 retrieved first; model explained it without writing the reference, so the explicit-reference card rule was added |
| Turn 4 "Hare Krishna!" | CASUAL, no verse, prompt 1924 (1911 reused), first word 0.66 s |
| Memory after 4 turns | 1.7 GB RSS, 0.3 MB swapped |
| Retest "What does Gita 2.47 mean?" after the fixes | verified 2.47 card attached (cites=1), prompt 581 tokens (507 reused), first word 2.7 s, 20 s total |

Bugs found and fixed during the run: empty native batch (`n_tokens` never set); weights repacked into anonymous memory
and swapped to zram on an 8 GB phone (now mmap-only for models over 1.5 GB); full-size SWA KV cache (now
window-sized); four threads landing on efficiency cores (now performance-core count); the debug variant compiled ggml
at -O0 (now Release for all variants); model loads queued behind the warm-up (now interrupt it); "giving up" matched
the charity theme.

## Phase 2 run — 2026-11-13, same phone (`tools/phone_test_phase2.ps1`)

| Check | Result |
|---|---|
| JVM unit tests | 11 tests, 0 failures (`QueryAnalyzerTest` 7, `RetrievalScoringTest` 4 incl. tokenizer conventions) |
| Compile-time embeddings | 652 verses embedded with quantized MiniLM; sanity: fear-vs-fear-verse 0.65, fear-vs-banyan -0.02 |
| Embedding model on the phone | ready in 328 ms after the LLM warm-up |
| "Everyone keeps comparing me to my cousin and it makes me feel small" | SUPPORT, theme jealousy; retrieved 12.13-14, 4.22, 18.27 (all about envy and equanimity); related-teaching card 12.13-14; first word 7.1 s |
| Reader search "anger" | 15 verses (2.56, 2.62 first) and 20 Mahabharata passages with speaker and section; bookmarks work |
| Daily Wisdom screen (menu → Today's verse) | 13 Nov 2026 → Gita 6.32 with reflection and discuss button |
| Test notification | posted on channel `daily_wisdom` with a content intent back into the app (seen in `dumpsys notification`) |

## Phase 3 run — 2026-11-13, same phone (`tools/phone_test_phase3.ps1`, build 0.3.0-phase3)

| Check | Result |
|---|---|
| Build | `assembleDebug` 222 MB, `assembleRelease` 156 MB (debug-signed), copies in `dist/`; merged manifest still has no `INTERNET` / `ACCESS_NETWORK_STATE` (`aapt dump permissions`) |
| JVM unit tests | 23 tests, 0 failures: `QueryAnalyzerTest` 7, `RetrievalScoringTest` 4, `KarmaStepsTest` 6, `DecisionCompassTest` 3, `GitaJourneyTest` 3 |
| Database migration 1 → 2 | Installed over the Phase 2 build with existing conversations; app started normally, old conversations intact, migration SQL identical to Room's exported `2.json` |
| Journal: create "Job offer" with body and tags, Reflect with Krishna | Chat opened with chip `JOURNAL · Job offer` (163 characters read) and the reflect prompt pre-filled; reply after 43 s (prompt 727 tokens, 507 reused) addressed the entry's content (Pune, startup, father); entry then shows "reflected with Krishna" and "Open the conversation about this entry" |
| Karma Steps: "give me three small steps … as a numbered list" | Reply contained a 3-item numbered list (18 s, prompt 701); "Save as Karma Steps (3)" shown under the bubble, became "Saved as Karma Steps"; new chat shows the Karma Steps card "0 of 3 done today" with checkboxes; ticking one gave "1 of 3 done today"; Karma Steps screen listed Open · 2 / Done · 1 with "Open the conversation" |
| Decision Compass: seven answers about a startup offer vs a bank job | Each step rendered with its question; "Begin with Krishna" created an Arjuna Mode conversation and sent the composed message automatically (prompt 952 tokens, first word 15.7 s, 43 s total); the reply asked what specifically excites the user about each option instead of giving a verdict |
| Gita journey | Overview "0 of 657 verses read · 0 of 18 chapters complete" with one reflection prompt per chapter; scrolling part of chapter 1 gave "14 of 39 verses read"; the reader's end card shows the chapter 1 prompt ("Arjuna's bow slipped from his hand…"), "Mark chapter as read" made it "Complete · 39 verses" and "1 of 18 chapters complete"; "Write in journal" opened the editor pre-filled with "Chapter 1 · Arjuna's Despair" and the prompt |
| Memory after the run | 1.5 GB RSS with the 1B model loaded |

Fixed during the run: Karma Steps from one reply were listed in reverse order; the orchestrator overwrote the
"Compass: …" conversation title with the first message. A second, fully scripted pass after a cold restart
(startup primed the 514-token system prompt in 16.7 s) reproduced every result above with the fixes in place:
journal reply in 13 s with the tags `#work #family` shown, a second 3-step list saved ("2 of 6 done today"),
and the Compass conversation listed in history as "Compass: Whether to take the startup offer in Pun…".

## Not yet tested

Airplane mode on the phone, OCR of a screenshot, PDF page rendering, the edition pack import, a scheduled (not
test) Daily Wisdom notification firing at the chosen hour, the 4B model on a flagship, journal search with many
entries, a Karma Steps list spanning several days, and the rest of the manual checklist in `TESTING.md`.
