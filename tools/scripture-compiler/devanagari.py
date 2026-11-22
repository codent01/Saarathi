"""IAST (ISKCON style, as in 'Bhagavad-gita As It Is') -> Devanagari.

The 'Slokas Only' PDF gives every verse in roman transliteration with word breaks and
compound hyphens, e.g.  "karmaṇy evādhikāras te mā phaleṣu kadācana".
Devanagari never writes a bare consonant mid-pada, so words ending in a consonant are
joined with the following word before transliteration:  कर्मण्येवाधिकारस्ते मा फलेषु कदाचन.
"""
from __future__ import annotations

import re
import unicodedata

from indic_transliteration import sanscript

VOWEL_ENDINGS = set("aāiīuūṛṝḷḹeoṃḥṁ")
AVAGRAHA_MARKS = ("’", "'", "‘")


def normalize_iast(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = text.replace("ﬁ", "fi").replace("ﬂ", "fl")
    text = text.replace("ṁ", "ṃ")          # ISKCON anusvara -> standard IAST
    text = text.replace("ḻ", "ḷ")
    return text


def _ends_with_consonant(word: str) -> bool:
    w = word.rstrip(".,;:!?")
    if not w:
        return False
    last = w[-1]
    if last in AVAGRAHA_MARKS:
        return False
    return last.isalpha() and last.lower() not in VOWEL_ENDINGS


def join_sandhi_words(line: str) -> str:
    """Join words that are one phonetic unit in Devanagari, keep the rest separated."""
    words = line.split()
    out: list[str] = []
    for w in words:
        w = w.replace("-", "")
        if out and _ends_with_consonant(out[-1]):
            out[-1] = out[-1] + w
        else:
            out.append(w)
    return " ".join(out)


def iast_line_to_devanagari(line: str) -> str:
    line = normalize_iast(line)
    for mark in AVAGRAHA_MARKS:
        line = line.replace(mark, "ऽ")  # avagraha survives transliteration untouched
    joined = join_sandhi_words(line)
    # sanscript leaves unknown characters (like ऽ) as they are.
    deva = sanscript.transliterate(joined, sanscript.IAST, sanscript.DEVANAGARI)
    # Printed shlokas attach the avagraha to the preceding word: सङ्गोऽस्त्वकर्मणि
    deva = deva.replace(" ऽ", "ऽ")
    return deva


def iast_lines_to_devanagari(lines: list[str]) -> list[str]:
    return [iast_line_to_devanagari(l) for l in lines]


def danda_format(lines: list[str]) -> str:
    """Lay out padas with single/double danda like a printed shloka."""
    deva = iast_lines_to_devanagari(lines)
    n = len(deva)
    out = []
    for i, l in enumerate(deva):
        if n >= 4 and i == n // 2 - 1:
            out.append(l + " ।")
        elif i == n - 1:
            out.append(l + " ॥")
        else:
            out.append(l)
    return "\n".join(out)


if __name__ == "__main__":
    sample = ["karmaṇy evādhikāras te", "mā phaleṣu kadācana",
              "mā karma-phala-hetur bhūr", "mā te saṅgo ’stv akarmaṇi"]
    print(danda_format(sample))
