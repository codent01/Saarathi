# Testing

## Automated

| Suite | How to run | Covers |
|---|---|---|
| JVM unit tests | `cd android && .\gradlew.bat testDebugUnitTest` | `QueryAnalyzer` intent and theme detection (English + Hinglish), explicit verse refs, character names, crisis flag, follow-up theme inheritance |
| Scripture compiler self-checks | `python tools/scripture-compiler/compile_all.py` | verse counts per chapter (700 total), missing verses, empty translations, Mahabharata parva/speaker statistics |

Results are recorded in `docs/TEST-RESULTS.md` after each run.

## Manual acceptance checklist (run on a physical phone)

Offline
- [ ] Enable airplane mode before first launch; complete onboarding, setup, import model, chat. Everything works.
- [ ] `adb shell dumpsys package com.saarathi.app | findstr INTERNET` shows no INTERNET permission.
- [ ] Kill the app during a reply; relaunch; the interrupted message shows "(stopped)" and history is intact.

Scripture
- [ ] "Krishna, I am afraid of failing" produces a reply that cites a Gita verse by reference and the card shows Devanagari, IAST and a labelled translation.
- [ ] Every card's chapter/verse exists in the Slokas PDF (spot-check 5 cards against the linked PDF page).
- [ ] "What does Gita 2.47 mean?" shows exactly 2.47.
- [ ] "Why did Karna fight for Duryodhana?" attaches Mahabharata passages with speaker and section, and the reply does not invent events absent from them.
- [ ] Ask for a verse that does not exist ("Gita 2.99"): no card appears and the reply does not claim one.
- [ ] After importing the edition pack, the same cards show the As It Is translation, page number, and the purport under "Commentary".

Conversation
- [ ] "Hare Krishna" gets a short friendly reply with no verse card.
- [ ] "I am afraid to change my job" then "But what if I fail?" keeps the job context.
- [ ] A message written out of anger ("I want to make him regret it") gets gentle pushback, not agreement.
- [ ] Arjuna Mode asks a question before advising.
- [ ] A crisis phrase gets a warm reply with the helpline footer.

Attachments
- [ ] Attach a screenshot with text: the reply uses the text; the chip says visual details are not understood.
- [ ] Attach a text-layer PDF and ask for a summary.
- [ ] Attach a photo with no text: the chip says no text was found; no claims about the picture.

Memory and data
- [ ] Memory off by default; saved memory appears in replies only when on.
- [ ] Clear conversations / clear memories work; bookmarks survive.

Interface
- [ ] Avatar renders in header, bubbles, splash, launcher icon.
- [ ] Sanskrit renders without broken conjuncts on the device font.
- [ ] Rotate the phone mid-reply; the stream continues.
- [ ] All three themes keep readable contrast.

Reliability
- [ ] Import a non-GGUF file: clear error, nothing loaded.
- [ ] Load the 4B model on a 6 GB phone: fit warning shown before loading.
- [ ] 20-turn conversation stays responsive; context trimming kicks in without errors.
