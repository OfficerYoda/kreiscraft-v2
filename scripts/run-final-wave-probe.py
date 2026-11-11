"""Compatibility entrypoint: run-final-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR."""
from pathlib import Path
import os
import runpy
import sys

repo = Path(__file__).resolve().parents[1]
runner = repo / "scripts/run-dragon-wave-probe.py"
production = os.environ.get("PRODUCTION_JAR", str(repo / "build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar"))
sys.argv = [str(runner), *sys.argv[1:], production, "final"]
runpy.run_path(str(runner), run_name="__main__")
