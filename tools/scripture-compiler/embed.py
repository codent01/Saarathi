"""Compute sentence embeddings for every Gita verse with the same quantized MiniLM model the app ships.

Model: Xenova/all-MiniLM-L6-v2 (Apache 2.0), onnx/model_quantized.onnx + vocab.txt in tools/embeddings.
Text per verse = translation + themes + curated note (same material the FTS index sees).
Pooling: mean over attention mask, then L2 normalisation (identical to the Kotlin Embedder).
Writes table gita_embeddings(verse_id INTEGER PRIMARY KEY, dim INTEGER, vec BLOB float32 little-endian).
"""
from __future__ import annotations

import sqlite3
import struct
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import BertWordPieceTokenizer

HERE = Path(__file__).resolve().parent
EMB = HERE.parent / "embeddings"
MODEL = EMB / "model_quantized.onnx"
VOCAB = EMB / "vocab.txt"
MAX_LEN = 256


class Embedder:
    def __init__(self) -> None:
        self.tok = BertWordPieceTokenizer(str(VOCAB), lowercase=True, strip_accents=True)
        self.tok.enable_truncation(MAX_LEN)
        self.sess = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
        self.inputs = [i.name for i in self.sess.get_inputs()]
        self.output = self.sess.get_outputs()[0].name

    def embed(self, text: str) -> np.ndarray:
        enc = self.tok.encode(text)
        ids = np.array([enc.ids], dtype=np.int64)
        mask = np.array([enc.attention_mask], dtype=np.int64)
        feed = {"input_ids": ids, "attention_mask": mask}
        if "token_type_ids" in self.inputs:
            feed["token_type_ids"] = np.zeros_like(ids)
        out = self.sess.run([self.output], feed)[0]          # (1, seq, 384)
        m = mask[..., None].astype(np.float32)
        vec = (out * m).sum(axis=1) / np.maximum(m.sum(axis=1), 1e-9)
        vec = vec[0]
        return vec / max(np.linalg.norm(vec), 1e-9)


def verse_text(translation: str, themes: str, note: str) -> str:
    parts = [translation.strip(), themes.replace("_", " ").strip(), note.strip()]
    return ". ".join(p for p in parts if p)


def write_embeddings(db_path: Path) -> int:
    db = sqlite3.connect(db_path)
    db.execute("CREATE TABLE IF NOT EXISTS gita_embeddings(verse_id INTEGER PRIMARY KEY, dim INTEGER NOT NULL, vec BLOB NOT NULL)")
    db.execute("DELETE FROM gita_embeddings")
    emb = Embedder()
    rows = db.execute(
        "SELECT v.id, COALESCE(t.text,''), v.themes, v.keywords FROM gita_verses v "
        "LEFT JOIN gita_translations t ON t.verse_id = v.id AND t.source_key = 'besant1922' ORDER BY v.id"
    ).fetchall()
    n = 0
    for vid, tr, themes, note in rows:
        text = verse_text(tr, themes or "", note or "")
        if not text:
            continue
        vec = emb.embed(text).astype(np.float32)
        db.execute("INSERT INTO gita_embeddings VALUES(?,?,?)", (vid, len(vec), struct.pack(f"<{len(vec)}f", *vec)))
        n += 1
    db.execute("INSERT OR REPLACE INTO meta VALUES('embedding_model', 'Xenova/all-MiniLM-L6-v2 quantized, mean pooling, L2')")
    db.commit(); db.close()
    return n


if __name__ == "__main__":
    e = Embedder()
    print("inputs:", e.inputs, "output:", e.output)
    a = e.embed("I am afraid of failing at my job")
    b = e.embed("fear of failure; you have a right to your work but not to its fruits")
    c = e.embed("the banyan tree with roots above")
    print("dim", a.shape, "sim(fear,fear-verse)=%.3f sim(fear,banyan)=%.3f" % (float(a @ b), float(a @ c)))
    db_path = HERE.parents[1] / "android" / "app" / "src" / "main" / "assets" / "scripture" / "scripture.db"
    print("embedded verses:", write_embeddings(db_path))
