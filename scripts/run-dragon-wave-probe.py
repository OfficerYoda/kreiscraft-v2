"""Run built production+probe JARs on fresh Paper132 worlds. Never use the shared run/ directory.

Usage: JAVA_HOME=... python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR [PRODUCTION_JAR] [wave|smash]
The cache directory supplies libraries/, cache/, versions/, eula.txt and paper-26.2-132.jar.
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
    "simulation-distance=3\nlevel-name=wave_world\nspawn-protection=0\nmax-players=80\n"
    "enable-rcon=false\nenable-query=false\npause-when-empty-seconds=-1\n"
)
plugins = server / "plugins"
plugins.mkdir()
production = Path(sys.argv[3]).resolve() if len(sys.argv) > 3 else repo / "build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar"
mode = sys.argv[4] if len(sys.argv) > 4 else "wave"
if mode not in ("wave", "smash"):
    raise ValueError("probe must be wave or smash")
probe = next((repo / "build/paper-probe").glob(("smash-encounter" if mode == "smash" else "dragon-wave") + "-probe-*.jar"))
shutil.copy2(production, plugins / "Kreiscraft.jar")
shutil.copy2(probe, plugins / "DragonWaveProbe.jar")
with zipfile.ZipFile(production) as jar:
    lines = jar.read("config.yml").decode().splitlines()
module = None
for index, line in enumerate(lines):
    if line.startswith("  ") and not line.startswith("   ") and line.endswith(":"):
        module = line.strip()[:-1]
    if line.startswith("    enabled:"):
        lines[index] = "    enabled: " + ("true" if module == "shattered-crown" else "false")
    if mode == "smash" and module == "shattered-crown":
        key = line.strip().split(":", 1)[0]
        overrides = {"opening-grace-seconds": 0, "arrival-x": 45.5, "arrival-z": 0.5,
                     "arrival-y": 64, "arrival-radius": 3, "arrival-height": 64,
                     "lane-cadence-seconds": 120, "prey-cadence-seconds": 120,
                     "landing-cadence-seconds": 120, "bombardment-cadence-seconds": 120,
                     "sweep-cadence-seconds": 10}
        if key in overrides:
            lines[index] = f"    {key}: {overrides[key]}"
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
        "-Dterminal.jline=false", "-Dterminal.ansi=false", f"-Dkreiscraft.{mode}-probe=true",
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
print(text[-14000:])
marker = "SMASH_ENCOUNTER_PROBE" if mode == "smash" else "DRAGON_WAVE_PROBE"
passed = code == 0 and marker + " PASS" in text and marker + " FAIL" not in text and not re.search(r"\bERROR\]", text)
if passed and mode == "smash":
    with (root / "console-restart.log").open("w") as log:
        process = subprocess.Popen([
            str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xms512M", "-Xmx2G",
            "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Dkreiscraft.smash-probe=true",
            "-Dkreiscraft.smash-restart=true", "-jar", "paper-26.2-132.jar", "--nogui",
        ], cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        (root / "owned-restart-pid.txt").write_text(str(process.pid))
        try:
            code = process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            process.kill()
            code = process.wait()
        (root / "exit-restart.txt").write_text(str(code))
    text = (root / "console-restart.log").read_text()
    print(text[-14000:])
    passed = code == 0 and "SMASH_RESTART_PROBE PASS" in text and " FAIL" not in text and not re.search(r"\bERROR\]", text)
sys.exit(0 if passed else 1)
