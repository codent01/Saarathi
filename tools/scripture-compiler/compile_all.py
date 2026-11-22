"""Run the whole scripture pipeline in order."""
import runpy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

for step in ("parse_shlokas", "fetch_besant", "parse_gita_edition", "parse_mahabharata", "build_db"):
    print(f"\n=== {step} ===")
    runpy.run_path(str(HERE / f"{step}.py"), run_name="__main__")
