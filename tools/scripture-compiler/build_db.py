"""Assemble the bundled scripture database and the user's personal edition pack.

Outputs
  android/app/src/main/assets/scripture/scripture.db   (bundled; public-domain content only)
  dist/gita_as_it_is_pack.json                          (NOT bundled; built from the user's own PDF)

Inputs (all under work/): gita_verses.json, besant.json, gita_edition.json, mb_passages.json
plus verse_tags.json and chapters.json next to this script.

Run every parser first, or run `python compile_all.py`.
"""
from __future__ import annotations

import json
import re
import sqlite3
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
WORK = HERE / "work"
ASSET_DIR = ROOT / "android" / "app" / "src" / "main" / "assets" / "scripture"
DIST = ROOT / "dist"
DB_PATH = ASSET_DIR / "scripture.db"
PACK_PATH = DIST / "gita_as_it_is_pack.json"

SCHEMA = """
PRAGMA journal_mode = OFF;
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE sources(
  key TEXT PRIMARY KEY, title TEXT NOT NULL, edition TEXT, author TEXT, kind TEXT NOT NULL,
  license TEXT, file_name TEXT, pages INTEGER, bundled INTEGER NOT NULL);
CREATE TABLE gita_chapters(
  chapter INTEGER PRIMARY KEY, title TEXT NOT NULL, title_edition TEXT, summary TEXT, themes TEXT,
  verse_count INTEGER NOT NULL, first_page_shlokas INTEGER, first_page_edition INTEGER);
CREATE TABLE gita_verses(
  id INTEGER PRIMARY KEY, chapter INTEGER NOT NULL, verse_start INTEGER NOT NULL, verse_end INTEGER NOT NULL,
  ref TEXT NOT NULL UNIQUE, speaker TEXT, iast TEXT NOT NULL, devanagari TEXT NOT NULL,
  page_shlokas INTEGER, page_edition INTEGER, themes TEXT, keywords TEXT, weight REAL NOT NULL DEFAULT 0.5);
CREATE INDEX idx_gita_verses_ch ON gita_verses(chapter, verse_start);
CREATE TABLE gita_translations(
  verse_id INTEGER NOT NULL, source_key TEXT NOT NULL, text TEXT NOT NULL, page INTEGER,
  PRIMARY KEY(verse_id, source_key));
CREATE TABLE gita_commentary(
  verse_id INTEGER NOT NULL, source_key TEXT NOT NULL, text TEXT NOT NULL, page INTEGER,
  PRIMARY KEY(verse_id, source_key));
CREATE TABLE mb_passages(
  id INTEGER PRIMARY KEY, book INTEGER NOT NULL, parva TEXT NOT NULL, sub_parva TEXT, section INTEGER NOT NULL,
  section_roman TEXT, speaker TEXT, text TEXT NOT NULL, page INTEGER NOT NULL, gita_chapter INTEGER);
CREATE INDEX idx_mb_book_section ON mb_passages(book, section);
CREATE VIRTUAL TABLE gita_fts USING fts4(content, tokenize=unicode61);
CREATE VIRTUAL TABLE mb_fts USING fts4(content, tokenize=unicode61);
"""

SPEAKER_ALIASES = {
    "Vaishampayana": "Vaisampayana", "Vasudeva": "Krishna", "Kesava": "Krishna", "Keshava": "Krishna",
    "Govinda": "Krishna", "The Holy One": "Krishna", "The Blessed Lord": "Krishna", "Janardana": "Krishna",
    "Madhava": "Krishna", "Hrishikesa": "Krishna", "Partha": "Arjuna", "Dhananjaya": "Arjuna",
    "Thus": "", "Having": "", "Then": "", "When": "", "And": "", "But": "", "He": "", "She": "", "They": "",
}


def norm_speaker(s: str) -> str:
    s = (s or "").strip()
    return SPEAKER_ALIASES.get(s, s)


def load(name: str):
    return json.loads((WORK / name).read_text(encoding="utf-8"))


def main() -> None:
    verses = load("gita_verses.json")
    besant = load("besant.json")
    edition = load("gita_edition.json") if (WORK / "gita_edition.json").exists() else []
    mb = load("mb_passages.json")
    tags = {k: v for k, v in json.loads((HERE / "verse_tags.json").read_text(encoding="utf-8")).items() if not k.startswith("_")}
    chapters = json.loads((HERE / "chapters.json").read_text(encoding="utf-8"))

    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(parents=True, exist_ok=True)
    if DB_PATH.exists():
        DB_PATH.unlink()
    db = sqlite3.connect(DB_PATH)
    db.executescript(SCHEMA)

    db.executemany("INSERT INTO meta VALUES(?,?)", [
        ("schema_version", "1"),
        ("built_at", time.strftime("%Y-%m-%dT%H:%M:%S")),
        ("gita_sanskrit_source", "shlokas_pdf"),
        ("gita_default_translation", "besant1922"),
    ])
    db.executemany("INSERT INTO sources VALUES(?,?,?,?,?,?,?,?,?)", [
        ("shlokas_pdf", "Bhagavad-gītā As It Is — Slokas Only", "Roman transliteration of the 700 verses", "",
         "gita_sanskrit", "Sanskrit text of the Gita is in the public domain", "Bhagavad_Gita_As_It_Is-Only_Shlokas.pdf", 39, 1),
        ("besant1922", "The Bhagavad-Gîtâ", "Fourth edition, 1922 (Theosophical Publishing House)", "Annie Besant",
         "gita_translation", "Public domain (author died 1933); text from Wikisource", "", 0, 1),
        ("ganguli", "The Mahabharata of Krishna-Dwaipayana Vyasa", "Kisari Mohan Ganguli translation, 1883-1896 (sacred-texts edition)", "Kisari Mohan Ganguli",
         "mahabharata", "Public domain", "Mahabharata (Unabridged in English).pdf", 2328, 1),
        ("as_it_is", "Bhagavad-gītā As It Is", "Original 1972 Macmillan edition", "A. C. Bhaktivedanta Swami Prabhupāda",
         "gita_edition", "Copyright Bhaktivedanta Book Trust; imported from the user's own PDF, never bundled",
         "Bhagavad_gita-As_It_Is-Original_authorized_Macmillan_edition.pdf", 1034, 0),
    ])

    # ---- Gita verses ---------------------------------------------------------------------
    edition_by_ref: dict[str, dict] = {}
    for r in edition:
        for v in range(r["verse_start"], r["verse_end"] + 1):
            edition_by_ref.setdefault(f"{r['chapter']}.{v}", r)

    verse_ids: dict[str, int] = {}
    for i, v in enumerate(verses, start=1):
        ref = v["ref"]
        tag = tags.get(ref) or tags.get(f"{v['chapter']}.{v['verse_start']}") or {}
        themes = " ".join(tag.get("themes", []))
        keywords = tag.get("note", "")
        weight = float(tag.get("weight", 0.4 if not tag else 0.6))
        ed = edition_by_ref.get(f"{v['chapter']}.{v['verse_start']}")
        db.execute(
            "INSERT INTO gita_verses VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (i, v["chapter"], v["verse_start"], v["verse_end"], ref, v.get("speaker", ""),
             "\n".join(v["iast"]), "\n".join(v["devanagari"]), v["page"], ed["page"] if ed else None,
             themes, keywords, weight))
        verse_ids[ref] = i
        # Besant: one entry per numbered verse; combined verses get the joined text
        parts = []
        for n in range(v["verse_start"], v["verse_end"] + 1):
            t = besant.get(f"{v['chapter']}.{n}")
            if t and t not in parts:
                parts.append(t)
        if parts:
            db.execute("INSERT INTO gita_translations VALUES(?,?,?,?)", (i, "besant1922", " ".join(parts), None))
        fts_text = " ".join(parts) + " " + themes + " " + keywords + " " + themes.replace("_", " ")
        db.execute("INSERT INTO gita_fts(docid, content) VALUES(?,?)", (i, fts_text))

    # ---- chapters -------------------------------------------------------------------------
    titles_edition = {}
    for v in verses:
        titles_edition.setdefault(v["chapter"], v.get("chapter_title", ""))
    for c in chapters:
        ch = c["chapter"]
        count = sum(v["verse_end"] - v["verse_start"] + 1 for v in verses if v["chapter"] == ch)
        first_sh = min((v["page"] for v in verses if v["chapter"] == ch), default=None)
        first_ed = min((r["page"] for r in edition if r["chapter"] == ch), default=None)
        db.execute("INSERT INTO gita_chapters VALUES(?,?,?,?,?,?,?,?)",
                   (ch, c["title"], titles_edition.get(ch, ""), c["summary"], " ".join(c["themes"]), count, first_sh, first_ed))

    # ---- Mahabharata ----------------------------------------------------------------------
    gita_marker = re.compile(r"\[\(Bhagavad Gita Chapter ([IVXL]+)\)\]")
    roman = {"I": 1, "V": 5, "X": 10, "L": 50}

    def rom(s: str) -> int:
        t, p = 0, 0
        for ch_ in reversed(s):
            val = roman[ch_]
            t += -val if val < p else val
            p = max(p, val)
        return t

    gita_ch = 0
    for p in mb:
        text = p["text"]
        m = gita_marker.search(text)
        if m:
            gita_ch = rom(m.group(1))
            text = gita_marker.sub("", text)
            text = re.sub(r"\(\[This where is the Bhagavad Gita proper starts.*?\]\)", "", text, flags=re.S).strip()
        in_gita = p["book"] == 6 and 25 <= p["section"] <= 42
        if not in_gita:
            gita_ch = 0
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) < 40:
            continue
        db.execute("INSERT INTO mb_passages VALUES(?,?,?,?,?,?,?,?,?,?)",
                   (p["idx"] + 1, p["book"], p["parva"], p.get("sub_parva", ""), p["section"], p["section_roman"],
                    norm_speaker(p["speaker"]), text, p["page"], gita_ch if in_gita else None))
        db.execute("INSERT INTO mb_fts(docid, content) VALUES(?,?)", (p["idx"] + 1, text))

    db.execute("INSERT INTO meta VALUES('gita_verse_rows', ?)", (str(len(verses)),))
    db.execute("INSERT INTO meta VALUES('mb_passage_rows', ?)", (str(len(mb)),))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"scripture.db written: {DB_PATH.stat().st_size / 1e6:.1f} MB")

    # ---- personal edition pack (not bundled) ------------------------------------------------
    pack = {
        "format": "saarathi-gita-edition-pack/1",
        "source": {
            "key": "as_it_is",
            "title": "Bhagavad-gītā As It Is",
            "edition": "Original 1972 Macmillan edition (user's own PDF)",
            "file_name": "Bhagavad_gita-As_It_Is-Original_authorized_Macmillan_edition.pdf",
            "pages": 1034,
            "note": "Derived from the owner's personal copy for private use inside Saarathi. Not for redistribution.",
        },
        "verses": [
            {"ref": f"{r['chapter']}.{r['verse_start']}" if r["verse_start"] == r["verse_end"] else f"{r['chapter']}.{r['verse_start']}-{r['verse_end']}",
             "chapter": r["chapter"], "verse_start": r["verse_start"], "verse_end": r["verse_end"],
             "page": r["page"], "translation": r["translation"], "commentary": r["purport"]}
            for r in edition
        ],
    }
    PACK_PATH.write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"edition pack written: {PACK_PATH} ({len(edition)} verse records)")

    # ---- verse embeddings (same MiniLM model the app bundles) ---------------------------------
    try:
        import sys
        sys.path.insert(0, str(HERE))
        from embed import write_embeddings
        print(f"embedded verses: {write_embeddings(DB_PATH)}")
    except Exception as e:  # noqa: BLE001
        print(f"WARNING: embeddings skipped ({e}); retrieval will fall back to themes + keywords")


if __name__ == "__main__":
    main()
