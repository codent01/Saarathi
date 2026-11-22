"""Parse 'Bhagavad_Gita_As_It_Is-Only_Shlokas.pdf' into work/gita_verses.json.

Each record: chapter, verse_start, verse_end, ref, speaker, iast (list of pada lines),
devanagari (list), page (1-based page in the PDF), chapter_title.
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

import pymupdf

from devanagari import iast_lines_to_devanagari, normalize_iast

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "scripture-sources" / "Bhagavad_Gita_As_It_Is-Only_Shlokas.pdf"
OUT = Path(__file__).resolve().parent / "work" / "gita_verses.json"

VERSE_RE = re.compile(r"^Bg\s+(\d+)\.(\d+)(?:\s*[-–]\s*(\d+))?\s*$")
CHAPTER_RE = re.compile(r"^Chapter\s+([A-Za-z-]+)\s*:\s*(.*)$")
SPEAKER_RE = re.compile(r"uvāca\s*$")

SPEAKER_NAMES = {
    "dhṛtarāṣṭra uvāca": "Dhritarashtra",
    "sañjaya uvāca": "Sanjaya",
    "arjuna uvāca": "Arjuna",
    "śrī-bhagavān uvāca": "Krishna",
    "śrī bhagavān uvāca": "Krishna",
}


def clean(line: str) -> str:
    line = normalize_iast(line).replace(" ", " ")
    # the PDF uses ṁ (dot above); normalize_iast maps it to ṃ for transliteration,
    # but we keep the printed form for display, so undo that one here.
    line = line.replace("ṃ", "ṁ")
    return re.sub(r"\s+", " ", line).strip()


def main() -> None:
    doc = pymupdf.open(SRC)
    verses: list[dict] = []
    chapter_title = ""
    chapter_num = 0
    pending_title: list[str] = []
    current: dict | None = None
    speaker = ""

    def flush():
        nonlocal current
        if current is not None:
            # join wrapped fragments: very short trailing lines belong to the previous pada
            lines: list[str] = []
            for l in current["iast"]:
                if lines and (len(l) < 10 or l.startswith("’")):
                    lines[-1] = lines[-1] + " " + l
                else:
                    lines.append(l)
            current["iast"] = lines
            current["devanagari"] = iast_lines_to_devanagari(lines)
            verses.append(current)
            current = None

    for pno in range(doc.page_count):
        for raw in doc[pno].get_text().splitlines():
            line = clean(raw)
            if not line:
                continue
            m = VERSE_RE.match(line)
            if m:
                flush()
                ch = int(m.group(1)); v1 = int(m.group(2)); v2 = int(m.group(3) or v1)
                if pending_title:
                    title = " ".join(pending_title)
                    cm = CHAPTER_RE.match(title)
                    chapter_title = cm.group(2).strip() if cm else title
                    pending_title = []
                chapter_num = ch
                current = {
                    "chapter": ch, "verse_start": v1, "verse_end": v2,
                    "ref": f"{ch}.{v1}" if v1 == v2 else f"{ch}.{v1}-{v2}",
                    "speaker": speaker, "iast": [], "devanagari": [],
                    "page": pno + 1, "chapter_title": chapter_title,
                }
                continue
            if CHAPTER_RE.match(line) or (pending_title and current is None):
                flush()
                pending_title.append(line)
                continue
            if SPEAKER_RE.search(line):
                key = line.lower()
                speaker = SPEAKER_NAMES.get(key, line)
                if current is not None:
                    current["speaker"] = speaker
                    current["speaker_line"] = line
                continue
            if current is not None:
                if line.startswith("Bhagavad-g") or line == "Slokas Only":
                    continue
                current["iast"].append(line)
    flush()

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(verses, ensure_ascii=False, indent=1), encoding="utf-8")
    chapters = sorted({v["chapter"] for v in verses})
    print(f"verses: {len(verses)}  chapters: {chapters}")
    per = {}
    for v in verses:
        per[v["chapter"]] = per.get(v["chapter"], 0) + (v["verse_end"] - v["verse_start"] + 1)
    print("verse counts per chapter:", per)
    bad = [v["ref"] for v in verses if not (2 <= len(v["iast"]) <= 8)]
    print("suspicious pada counts:", bad[:30], "..." if len(bad) > 30 else "")
    for ref in ("2.47", "1.21-22", "18.66", "2.29"):
        for v in verses:
            if v["ref"] == ref:
                print(ref, v["speaker"], "|", " / ".join(v["iast"]))
                print("   ", " / ".join(v["devanagari"]))


if __name__ == "__main__":
    main()
