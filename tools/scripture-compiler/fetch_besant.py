"""Download Annie Besant's public-domain Bhagavad-Gita translation (4th ed., 1922) from
Wikisource and save work/besant.json as {"2.47": "text", ...}.

Besant died in 1933; the text is public domain in India and the US. It is bundled in the
app as the default English rendering, clearly labelled, so the APK contains no copyrighted
translation. Requires network once, at compile time only.
"""
from __future__ import annotations

import html
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

from bs4 import BeautifulSoup

OUT = Path(__file__).resolve().parent / "work" / "besant.json"
URL = "https://en.wikisource.org/api/rest_v1/page/html/Bhagavad-Gita_(Besant_4th)%2FDiscourse_{n}"
UA = "SaarathiScriptureCompiler/0.1 (offline Android app build tool)"
VERSES_PER_CHAPTER = {1: 47, 2: 72, 3: 43, 4: 42, 5: 29, 6: 47, 7: 30, 8: 28, 9: 34, 10: 42,
                      11: 55, 12: 20, 13: 35, 14: 27, 15: 20, 16: 24, 17: 28, 18: 78}


CACHE = Path(__file__).resolve().parent / "work" / "besant_html"


def fetch(n: int) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / f"discourse_{n}.html"
    if cached.exists() and cached.stat().st_size > 10_000:
        return cached.read_text(encoding="utf-8")
    req = urllib.request.Request(URL.format(n=n), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        doc = r.read().decode("utf-8")
    cached.write_text(doc, encoding="utf-8")
    return doc


def parse_chapter(doc: str) -> dict[int, str]:
    soup = BeautifulSoup(doc, "html.parser")
    verses: dict[int, str] = {}
    for p in soup.find_all("p"):
        # skip Sanskrit blocks
        if p.find_parent("div", attrs={"lang": "sa"}) is not None:
            continue
        for sup in p.find_all("sup"):
            sup.decompose()
        text = p.get_text(" ", strip=True)
        text = html.unescape(text).replace("​", "")
        text = re.sub(r"\s+", " ", text).strip()
        m = re.match(r"^(.*?)\s*\((\d+)(?:\s*[-–,]\s*(\d+))?\)\s*$", text)
        if not m:
            continue
        body = re.sub(r"\s+([,.;:!?])", r"\1", m.group(1).strip())
        first, last = int(m.group(2)), int(m.group(3) or m.group(2))
        for num in range(first, last + 1):
            if body and num not in verses:
                verses[num] = body
    return verses


def main() -> None:
    result: dict[str, str] = {}
    for ch in range(1, 19):
        for attempt in range(5):
            try:
                doc = fetch(ch)
                break
            except Exception as e:  # noqa: BLE001
                wait = 15 * (attempt + 1)
                print(f"chapter {ch}: fetch failed ({e}), retrying in {wait}s", file=sys.stderr)
                time.sleep(wait)
        else:
            raise SystemExit(f"could not fetch chapter {ch}")
        verses = parse_chapter(doc)
        expected = VERSES_PER_CHAPTER[ch]
        missing = [v for v in range(1, expected + 1) if v not in verses]
        print(f"chapter {ch:2d}: {len(verses)} verses (expected {expected}) missing {missing}")
        for v, t in verses.items():
            result[f"{ch}.{v}"] = t
        time.sleep(4)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    print("total verses:", len(result))
    print("sample 2.47:", result.get("2.47"))


if __name__ == "__main__":
    main()
