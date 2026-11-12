"""Stdlib routing/staging check; real Paper assertions remain in FinalWaveProbe."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import zipfile

repo = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    cache = root / "cache"
    cache.mkdir()
    for name in ("libraries", "cache", "versions"):
        (cache / name).mkdir()
    for name in ("eula.txt", "paper-26.3-249.jar"):
        (cache / name).write_text("fixture")
    production = root / "production.jar"
    with zipfile.ZipFile(production, "w") as jar:
        jar.writestr("config.yml", "modules:\n  shattered-crown:\n    enabled: false\n  wither:\n    enabled: false\n  other:\n    enabled: true\n")
    probe = root / "probe.jar"
    probe.write_bytes(b"fixture")
    java = root / "bin/java"
    java.parent.mkdir()
    java.write_text(f"#!{sys.executable}\nimport sys\nassert '-Dkreiscraft.final-wave-probe=true' in sys.argv\nprint('FINAL_WAVE_PROBE PASS fixture')\n")
    java.chmod(0o755)
    env = dict(os.environ, JAVA_HOME=str(root), PRODUCTION_JAR=str(production), PROBE_JAR=str(probe))
    for command in ("run-dragon-wave-probe.py", "run-final-wave-probe.py"):
        evidence = root / command
        args = [sys.executable, str(repo / "scripts" / command), str(evidence), str(cache)]
        if command == "run-dragon-wave-probe.py":
            args += [str(production), "final"]
        result = subprocess.run(args, env=env, capture_output=True, text=True, timeout=10)
        assert result.returncode == 0, result.stdout + result.stderr
        assert (evidence / "exit.txt").read_text() == "0"
        assert (evidence / "timeout.txt").read_text() == "False"
        artifacts = json.loads((evidence / "artifacts.json").read_text())
        assert artifacts["production_sha256"] == hashlib.sha256(production.read_bytes()).hexdigest()
        assert artifacts["probe_sha256"] == hashlib.sha256(probe.read_bytes()).hexdigest()
        assert all(json.loads((evidence / "ports-released.json").read_text()).values())
        assert "level-name=final_world" in (evidence / "server/server.properties").read_text()
        config = (evidence / "server/plugins/Kreiscraft/config.yml").read_text()
        assert "  wither:\n    enabled: true" in config
        assert "  shattered-crown:\n    enabled: true" in config
        assert "  other:\n    enabled: false" in config
print("FINAL_WAVE_RUNNER_CHECK PASS general+compatibility+staging+process+port")
