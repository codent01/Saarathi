# Third-party content and licenses

| Component | License | Notes |
|---|---|---|
| llama.cpp / ggml | MIT | vendored at tag b12319 |
| Gemma 3 models (ggml-org GGUF) | Gemma Terms of Use | not bundled; the user installs the file. Redistributing a Gemma-powered app requires passing on the Gemma prohibited-use policy |
| Sanskrit text of the Bhagavad Gita | public domain | IAST from the user's "Slokas Only" PDF; Devanagari generated |
| Annie Besant, *The Bhagavad-Gîtâ* (4th ed., 1922) | public domain | fetched once from Wikisource |
| K. M. Ganguli, *The Mahabharata* (1883–1896) | public domain | sacred-texts edition, from the user's PDF |
| *Bhagavad-gītā As It Is* (1972 Macmillan) | © Bhaktivedanta Book Trust | never bundled; personal edition pack only |
| PdfBox-Android | Apache 2.0 | |
| ONNX Runtime (onnxruntime-android) | MIT | runs the embedding model on the phone |
| all-MiniLM-L6-v2 (Xenova ONNX export, quantized) | Apache 2.0 | 23 MB sentence-embedding model bundled in `assets/embeddings/`; also used at compile time to embed the verses |
| ML Kit text recognition (bundled models) | Google APIs ToS | offline bundled variants |
| Coil, Room, Compose, DataStore, WorkManager | Apache 2.0 | |
| indic_transliteration, PyMuPDF (AGPL), pytesseract, BeautifulSoup | various | build-time tools only, not in the APK |
| Krishna avatar artwork | provided by the app owner | generated, original |
