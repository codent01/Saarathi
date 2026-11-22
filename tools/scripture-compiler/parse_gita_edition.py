"""Parse the text layer of the user's 'Bhagavad-gita As It Is' (1972 Macmillan) scan.

Produces work/gita_edition.json with, per verse: chapter, verse_start, verse_end, page
(1-based PDF page where the TEXT heading sits), translation, purport (commentary).

This output is the user's *personal edition pack*. It is derived from their own PDF and is
NOT bundled into the APK; the app imports it at setup time so verse cards can show this
edition's wording with exact page references.

Pass 1 maps every page to a chapter (chapter title pages + running headers).
Pass 2 walks TEXT headings page by page. Pages without a text layer are OCR'd with
Tesseract (there are only ~20 of them).
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import pymupdf

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "scripture-sources" / "Bhagavad_gita-As_It_Is-Original_authorized_Macmillan_edition.pdf"
WORK = Path(__file__).resolve().parent / "work"
OUT = WORK / "gita_edition.json"
TESSDATA = Path(__file__).resolve().parent / "tessdata"
TESSERACT = Path(r"C:\Program Files\Tesseract-OCR\tesseract.exe")

TEXT_RE = re.compile(r"^TEXTS?\s*(\d+)\s*(?:[-–]\s*(\d+))?\s*$")
CHAPTER_RE = re.compile(r"^CHAPTER\s+([A-Z]+)\s*$")
HEADER_CH_RE = re.compile(r"\[\s*Ch\.?\s*(\d+)")
WORDS = {"ONE": 1, "TWO": 2, "THREE": 3, "FOUR": 4, "FIVE": 5, "SIX": 6, "SEVEN": 7, "EIGHT": 8,
         "NINE": 9, "TEN": 10, "ELEVEN": 11, "TWELVE": 12, "THIRTEEN": 13, "FOURTEEN": 14,
         "FIFTEEN": 15, "SIXTEEN": 16, "SEVENTEEN": 17, "EIGHTEEN": 18}
VERSES_PER_CHAPTER = {1: 46, 2: 72, 3: 43, 4: 42, 5: 29, 6: 47, 7: 30, 8: 28, 9: 34, 10: 42,
                      11: 55, 12: 20, 13: 35, 14: 27, 15: 20, 16: 24, 17: 28, 18: 78}

_ocr_cache = WORK / "ocr_cache"


def page_lines(doc: pymupdf.Document, pno: int) -> list[str]:
    text = doc[pno].get_text()
    if len(text.strip()) < 50:
        _ocr_cache.mkdir(parents=True, exist_ok=True)
        cached = _ocr_cache / f"p{pno + 1}.txt"
        if cached.exists():
            text = cached.read_text(encoding="utf-8")
        else:
            import pytesseract
            from PIL import Image
            pytesseract.pytesseract.tesseract_cmd = str(TESSERACT)
            pix = doc[pno].get_pixmap(dpi=300)
            img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
            # English only; the installed tessdata ships eng. Devanagari is never OCR'd here.
            text = pytesseract.image_to_string(img, lang="eng")
            cached.write_text(text, encoding="utf-8")
            print(f"  OCR'd page {pno + 1} ({len(text)} chars)")
    return [l.strip() for l in text.splitlines()]


def join_lines(lines: list[str]) -> str:
    out = ""
    for l in lines:
        l = l.strip()
        if not l:
            continue
        if out.endswith("\u00ad") or out.endswith("-"):
            out = out.rstrip("\u00ad-") + l
        else:
            out = (out + " " + l) if out else l
    return re.sub(r"\s+", " ", out).strip()


def chapter_map(pages: list[list[str]]) -> list[int]:
    """Chapter number for each page (0 = front matter)."""
    starts: dict[int, int] = {}
    # chapter title pages: a CHAPTER <WORD> line near the top, followed by TEXT 1 soon after
    for pno, lines in enumerate(pages):
        head = [l for l in lines[:8] if l]
        for l in head:
            m = CHAPTER_RE.match(l)
            if m and m.group(1) in WORDS:
                ch = WORDS[m.group(1)]
                has_text1 = any(TEXT_RE.match(x) and int(TEXT_RE.match(x).group(1)) == 1 for x in lines[:40])
                if has_text1 and ch not in starts:
                    starts[ch] = pno
    # fallback: first page carrying "[Ch. N" header, backed up one page if that page has TEXT 1
    for pno, lines in enumerate(pages):
        for l in lines[:6]:
            m = HEADER_CH_RE.search(l)
            if m:
                ch = int(m.group(1))
                if 1 <= ch <= 18 and ch not in starts:
                    prev = pages[pno - 1] if pno > 0 else []
                    prev_has_text1 = any(TEXT_RE.match(x) and int(TEXT_RE.match(x).group(1)) == 1 for x in prev[:40])
                    starts[ch] = pno - 1 if prev_has_text1 else pno
    ordered = sorted(starts.items())
    print("chapter start pages:", {k: v + 1 for k, v in ordered})
    result = [0] * len(pages)
    for ch, start in ordered:
        for p in range(start, len(pages)):
            result[p] = ch
    return result


def main() -> None:
    doc = pymupdf.open(SRC)
    pages = [page_lines(doc, i) for i in range(doc.page_count)]
    chapters = chapter_map(pages)

    records: list[dict] = []
    cur: dict | None = None
    mode = ""
    trans_lines: list[str] = []
    purp_lines: list[str] = []
    expected_next = 1
    chapter = 0

    def flush():
        nonlocal cur, trans_lines, purp_lines, mode
        if cur is not None:
            cur["translation"] = join_lines(trans_lines)
            cur["purport"] = join_lines(purp_lines)
            records.append(cur)
        cur = None; trans_lines = []; purp_lines = []; mode = ""

    for pno, lines in enumerate(pages):
        if chapters[pno] != chapter:
            flush()
            chapter = chapters[pno]
            expected_next = 1
        if chapter < 1:
            continue
        for s in lines:
            if not s:
                continue
            # OCR often renders "TEXT 11" as "TEXT I1", "TEXT ll" or "TEXT 1 1"; normalise before matching
            if re.match(r"^TEXTS?\s", s):
                tail = s[4:] if s.startswith("TEXT ") else s[5:] if s.startswith("TEXTS") else s[4:]
                if re.fullmatch(r"[\sIl|1-9O0\-–]+", tail or " "):
                    tail = tail.replace("I", "1").replace("l", "1").replace("|", "1").replace("O", "0")
                    if "-" not in tail and "–" not in tail:
                        tail = tail.replace(" ", "")
                    s = ("TEXTS " if s.startswith("TEXTS") else "TEXT ") + tail.strip()
            m = TEXT_RE.match(s)
            if m:
                v1 = int(m.group(1)); v2 = int(m.group(2) or v1)
                if v1 == expected_next or (v1 > expected_next and v1 - expected_next <= 3):
                    flush()
                    cur = {"chapter": chapter, "verse_start": v1, "verse_end": v2, "page": pno + 1}
                    expected_next = v2 + 1
                continue
            if cur is None:
                continue
            up = s.upper().replace(" ", "")
            if up == "TRANSLATION":
                mode = "translation"; continue
            if up == "PURPORT":
                mode = "purport"; continue
            if re.match(r"^(Text\s+\d+\]|\d+\s*$)", s) or "Bhagavad-gita As It Is" in s or HEADER_CH_RE.search(s):
                continue
            if mode == "translation":
                trans_lines.append(s)
            elif mode == "purport":
                purp_lines.append(s)
    flush()

    WORK.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(records, ensure_ascii=False, indent=1), encoding="utf-8")

    covered: dict[int, set[int]] = {}
    for r in records:
        for v in range(r["verse_start"], r["verse_end"] + 1):
            covered.setdefault(r["chapter"], set()).add(v)
    print(f"verse records: {len(records)}")
    missing_total = 0
    for ch in range(1, 19):
        have = covered.get(ch, set())
        missing = [v for v in range(1, VERSES_PER_CHAPTER[ch] + 1) if v not in have]
        missing_total += len(missing)
        pg = [r["page"] for r in records if r["chapter"] == ch]
        print(f"  ch {ch:2d}: {len(have):3d}/{VERSES_PER_CHAPTER[ch]} pages {min(pg, default=0)}-{max(pg, default=0)} missing {missing[:15]}")
    empty = [f"{r['chapter']}.{r['verse_start']}" for r in records if len(r["translation"]) < 20]
    print("empty translations:", len(empty), empty[:20])
    print("total missing verses:", missing_total)


if __name__ == "__main__":
    main()
