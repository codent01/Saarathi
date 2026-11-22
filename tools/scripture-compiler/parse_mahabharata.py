"""Parse the Ganguli Mahabharata PDF (sacred-texts edition) into work/mb_passages.json.

Record: idx, book, parva, section (int), section_roman, sub_parva, speaker, text, page.
Speaker attribution follows Ganguli's "<Name> said/continued," openers and is carried
forward inside a section until the next opener. Nested narration (Sauti -> Vaisampayana ->
Sanjaya) means the outermost narrator is always a reciter; the app treats only the
innermost explicit speaker as "who said this".
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import pymupdf

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "scripture-sources" / "Mahabharata (Unabridged in English).pdf"
OUT = Path(__file__).resolve().parent / "work" / "mb_passages.json"

BOOK_RE = re.compile(r"^BOOK\s+(\d+)\s*$")
PARVA_RE = re.compile(r"^([A-Z][A-Z\-' ]+?)\s+PARVA\s*$")
SECTION_RE = re.compile(r"^SECTION\s+([IVXLCDM]+)\s*[A-Z]?\s*$")
SUBPARVA_RE = re.compile(r"^\(([A-Za-z\-' ]+ Parva)\)\s*$")
SPEAKER_RE = re.compile(
    r"""^[\s"'‘“]*([A-Z][A-Za-z'\-]+(?:\s+[A-Z][A-Za-z'\-]+){0,3})\s+(?:then\s+)?(said|continued|replied|answered|addressed|spoke)\b""")
NOISE_RE = re.compile(r"^(The Complete Mahabharata in English|Page \d+|\d+)\s*$")

ROMAN = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}

# The sacred-texts edition does not print every book's parva heading in caps, so the
# book number is the reliable key.
PARVA_BY_BOOK = {
    1: "Adi Parva", 2: "Sabha Parva", 3: "Vana Parva", 4: "Virata Parva", 5: "Udyoga Parva",
    6: "Bhishma Parva", 7: "Drona Parva", 8: "Karna Parva", 9: "Shalya Parva", 10: "Sauptika Parva",
    11: "Stri Parva", 12: "Santi Parva", 13: "Anusasana Parva", 14: "Aswamedha Parva",
    15: "Asramavasika Parva", 16: "Mausala Parva", 17: "Mahaprasthanika Parva", 18: "Svargarohanika Parva",
}


def roman_to_int(s: str) -> int:
    total = 0
    prev = 0
    for ch in reversed(s):
        v = ROMAN[ch]
        total += -v if v < prev else v
        prev = max(prev, v)
    return total


def main() -> None:
    doc = pymupdf.open(SRC)
    passages: list[dict] = []
    book = 0
    parva = ""
    sub_parva = ""
    section = 0
    section_roman = ""
    speaker = ""
    buf: list[str] = []
    buf_page = 0
    pending_short = ""

    def flush():
        nonlocal buf, pending_short, speaker, buf_page
        text = " ".join(buf).strip()
        buf = []
        if not text:
            return
        m = SPEAKER_RE.match(text)
        if m:
            speaker = m.group(1).strip()
        # merge tiny paragraphs with the next one so passages are meaningful units
        if pending_short:
            text = pending_short + " " + text
            pending_short = ""
        if len(text) < 160:
            pending_short = text
            return
        passages.append({
            "idx": len(passages), "book": book, "parva": PARVA_BY_BOOK.get(book, parva), "sub_parva": sub_parva,
            "section": section, "section_roman": section_roman, "speaker": speaker,
            "text": text, "page": buf_page,
        })

    for pno in range(doc.page_count):
        lines = doc[pno].get_text().splitlines()
        for raw in lines:
            line = raw.replace(" ", " ").rstrip()
            s = line.strip()
            if not s:
                flush()
                continue
            if NOISE_RE.match(s):
                continue
            m = BOOK_RE.match(s)
            if m:
                flush(); book = int(m.group(1)); continue
            m = PARVA_RE.match(s)
            if m and len(s) < 40:
                flush(); parva = s.title(); sub_parva = ""; continue
            m = SECTION_RE.match(s)
            if m:
                flush(); section_roman = m.group(1); section = roman_to_int(section_roman)
                speaker = ""; pending_short = ""; continue
            m = SUBPARVA_RE.match(s)
            if m:
                sub_parva = m.group(1); continue
            if not buf:
                buf_page = pno + 1
            buf.append(s)
    flush()

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(passages, ensure_ascii=False), encoding="utf-8")
    books = {}
    for p in passages:
        books.setdefault((p["book"], p["parva"]), 0)
        books[(p["book"], p["parva"])] += 1
    print(f"passages: {len(passages)}")
    for k in sorted(books):
        print(" ", k, books[k])
    speakers = {}
    for p in passages:
        speakers[p["speaker"]] = speakers.get(p["speaker"], 0) + 1
    top = sorted(speakers.items(), key=lambda kv: -kv[1])[:25]
    print("top speakers:", top)
    gita = [p for p in passages if p["book"] == 6 and 25 <= p["section"] <= 42]
    print("Bhishma Parva sections 25-42 passages:", len(gita))
    if gita:
        print("sample:", gita[0]["speaker"], "|", gita[0]["text"][:300])


if __name__ == "__main__":
    main()
