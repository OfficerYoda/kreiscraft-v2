"""Run the final-wave fixture on fresh Paper132 with BOTH production modules enabled.

Usage: JAVA_HOME=... python3 scripts/run-final-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import zipfile

repo = Path(__file__).resolve().parents[1]
root = Path(sys.argv[1]).resolve()
cache = Path(sys.argv[2]).resolve()
root.mkdir(exist_ok=False)
server = root / "server"
server.mkdir()
for name in ("libraries", "cache", "versions"):
    shutil.copytree(cache / name, server / name)
for name in ("eula.txt", "paper-26.2-132.jar"):
    shutil.copy2(cache / name, server / name)
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
(server / "server.properties").write_text(
    f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=true\nview-distance=3\n"
    "simulation-distance=3\nlevel-name=final_world\nspawn-protection=0\nmax-players=80\n"
    "enable-rcon=false\nenable-query=false\npause-when-empty-seconds=-1\n"
)
plugins = server / "plugins"
plugins.mkdir()
production = repo / "build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar"
probe = next((repo / "build/paper-probe").glob("final-wave-probe-*.jar"))
shutil.copy2(production, plugins / "Kreiscraft.jar")
shutil.copy2(probe, plugins / "FinalWaveProbe.jar")
with zipfile.ZipFile(production) as jar:
    lines = jar.read("config.yml").decode().splitlines()
module = None
for index, line in enumerate(lines):
    if line.startswith("  ") and not line.startswith("   ") and line.endswith(":"):
        module = line.strip()[:-1]
    if line.startswith("    enabled:"):
        lines[index] = "    enabled: " + ("true" if module in ("shattered-crown", "wither") else "false")
(plugins / "Kreiscraft").mkdir()
(plugins / "Kreiscraft/config.yml").write_text("\n".join(lines) + "\n")
(root / "artifacts.json").write_text(json.dumps({
    "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
    "port": port,
    "production_sha256": hashlib.sha256(production.read_bytes()).hexdigest(),
    "probe_sha256": hashlib.sha256(probe.read_bytes()).hexdigest(),
    "paper_sha256": hashlib.sha256((server / "paper-26.2-132.jar").read_bytes()).hexdigest(),
}, indent=2))
with (root / "console.log").open("w") as log:
    process = subprocess.Popen([
        str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xms512M", "-Xmx2G",
        "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Dkreiscraft.final-wave-probe=true",
        "-jar", "paper-26.2-132.jar", "--nogui",
    ], cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
    (root / "owned-pid.txt").write_text(str(process.pid))
    try:
        code = process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        process.stdin.write("stop\n")
        process.stdin.flush()
        try:
            code = process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            code = process.wait()
    (root / "exit.txt").write_text(str(code))
text = (root / "console.log").read_text()
print(text[-18000:])
sys.exit(0 if code == 0 and "FINAL_WAVE_PROBE PASS" in text and "FINAL_WAVE_PROBE FAIL" not in text
         and not re.search(r"\bERROR\]", text) else 1)
