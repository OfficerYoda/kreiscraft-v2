"""Run built production+probe JARs on fresh Paper132 worlds. Never use the shared run/ directory.

Usage: JAVA_HOME=... python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR [PRODUCTION_JAR] [wave|final|smash|opening|release|rotation|second|dive|damage|flight|defender]
The cache directory supplies libraries/, cache/, versions/, eula.txt and paper-26.3-249.jar.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import threading
import zipfile

repo = Path(__file__).resolve().parents[1]
mode = sys.argv[4] if len(sys.argv) > 4 else "wave"
root = Path(sys.argv[1]).resolve()
cache = Path(sys.argv[2]).resolve()
root.mkdir(exist_ok=False)
server = root / "server"
server.mkdir()
for name in ("libraries", "cache", "versions"):
    shutil.copytree(cache / name, server / name)
for name in ("eula.txt", "paper-26.3-249.jar"):
    shutil.copy2(cache / name, server / name)
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
(server / "server.properties").write_text(
    f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=true\nview-distance=3\n"
    f"simulation-distance=3\nlevel-name={'final_world' if mode == 'final' else 'wave_world'}\nspawn-protection=0\nmax-players=80\n"
    "enable-rcon=false\nenable-query=false\npause-when-empty-seconds=-1\n"
)
plugins = server / "plugins"
plugins.mkdir()
production = Path(sys.argv[3]).resolve() if len(sys.argv) > 3 else repo / "build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar"
guard = {"final": "final-wave", "second": "second-wave", "flight": "dragon"}.get(mode, mode)
stems = {"wave": "dragon-wave", "final": "final-wave", "smash": "smash-encounter", "opening": "opening-roster",
         "release": "release-candidate", "rotation": "rotation", "second": "second-wave", "dive": "dive-encounter",
         "damage": "dragon-damage", "flight": "dragon-flight", "defender": "defender-roaming"}
if mode not in stems:
    raise ValueError(f"probe must be one of {', '.join(stems)}")
probe = Path(os.environ["PROBE_JAR"]).resolve() if "PROBE_JAR" in os.environ else repo / "build/paper-probe" / (stems[mode] + "-probe-0.1.0-SNAPSHOT.jar")
shutil.copy2(production, plugins / "Kreiscraft.jar")
shutil.copy2(probe, plugins / "DragonWaveProbe.jar")
with zipfile.ZipFile(production) as jar:
    lines = jar.read("config.yml").decode().splitlines()
module = None
for index, line in enumerate(lines):
    if line.startswith("  ") and not line.startswith("   ") and line.endswith(":"):
        module = line.strip()[:-1]
    if line.startswith("    enabled:"):
        lines[index] = "    enabled: " + ("true" if module in ("shattered-crown", "wither") else "false")
    if mode == "smash" and module == "shattered-crown":
        key = line.strip().split(":", 1)[0]
        overrides = {"opening-grace-seconds": 0, "arrival-x": 45.5, "arrival-z": 40.5,
                     "arrival-y": 64, "arrival-radius": 3, "arrival-height": 64,
                     "lane-cadence-seconds": 120, "prey-cadence-seconds": 120,
                      "landing-cadence-seconds": 120, "bombardment-cadence-seconds": 120,
                      "sweep-cadence-seconds": 10, "attack-gap-min-seconds": 5, "attack-gap-max-seconds": 5}
        if key in overrides:
            lines[index] = f"    {key}: {overrides[key]}"
    if mode in ("release", "rotation") and module == "shattered-crown":
        key = line.strip().split(":", 1)[0]
        overrides = {"attack-gap-min-seconds": 5, "attack-gap-max-seconds": 5,
        "landing-cadence-seconds": 120, "landing-approach-seconds": 45}
        if key in overrides:
            lines[index] = f"    {key}: {overrides[key]}"
    if mode in ("rotation", "dive", "second") and module == "shattered-crown":
        key = line.strip().split(":", 1)[0]
        overrides = {"rotation": {"pool-damage": 1, "pool-lifetime-seconds": 15, "lane-damage": 0,
                                  "sweep-damage": 0, "bombardment-damage": 1, "landing-cadence-seconds": 10},
                     "dive": {"opening-grace-seconds": 0, "attack-gap-min-seconds": 5,
                              "attack-gap-max-seconds": 5, "landing-cadence-seconds": 120},
                     "second": {"opening-grace-seconds": 120}}[mode]
        if key in overrides:
            lines[index] = f"    {key}: {overrides[key]}"
(plugins / "Kreiscraft").mkdir()
(plugins / "Kreiscraft/config.yml").write_text("\n".join(lines) + "\n")
snapshot = Path(os.environ["RELEASE_SNAPSHOT"]).resolve() if "RELEASE_SNAPSHOT" in os.environ else None
if snapshot is not None:
    if mode != "release":
        raise ValueError("RELEASE_SNAPSHOT requires release mode")
    source = json.loads((snapshot / "artifacts.json").read_text())
    if (source["production_sha256"] != hashlib.sha256(production.read_bytes()).hexdigest()
            or "RELEASE_INTERRUPT_READY" not in (snapshot / "console.log").read_text()
            or (snapshot / "exit.txt").read_text().strip() != "-9"):
        raise ValueError("Snapshot must be a genuine owned interruption of the same production artifact")
    shutil.copytree(snapshot / "interrupted-world-backup", root / "interrupted-world-backup")
    shutil.copytree(snapshot / "interrupted-world-backup", server / "wave_world")
    shutil.copy2(snapshot / "interrupted.properties", root / "interrupted.properties")
    (plugins / "ReleaseCandidateProbe").mkdir()
    shutil.copy2(snapshot / "interrupted.properties", plugins / "ReleaseCandidateProbe/interrupted.properties")
    shutil.copy2(snapshot / "server/plugins/Kreiscraft/config.yml", plugins / "Kreiscraft/config.yml")
    (root / "snapshot-source.txt").write_text(str(snapshot))
(root / "artifacts.json").write_text(json.dumps({
    "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
    "port": port,
    "production_sha256": hashlib.sha256(production.read_bytes()).hexdigest(),
    "probe_sha256": hashlib.sha256(probe.read_bytes()).hexdigest(),
    "paper_sha256": hashlib.sha256((server / "paper-26.3-249.jar").read_bytes()).hexdigest(),
}, indent=2))
with (root / "console.log").open("w") as log:
    timed_out = False
    process = subprocess.Popen([
        str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xms512M", "-Xmx2G",
        "-Dterminal.jline=false", "-Dterminal.ansi=false", f"-Dkreiscraft.{guard}-probe=true",
        f"-Dkreiscraft.release-restart={'true' if snapshot is not None else 'false'}",
        "-jar", "paper-26.3-249.jar", "--nogui",
    ], cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE if mode == "release" else log,
       stderr=subprocess.STDOUT, text=True)
    (root / "owned-pid.txt").write_text(str(process.pid))
    try:
        if mode == "release":
            # Consume the owned process protocol, not an unbounded background-job polling loop.
            deadline = threading.Timer(120 if snapshot is not None else 1200, process.kill)
            deadline.start()
            try:
                for line in process.stdout:
                    log.write(line)
                    log.flush()
                    if "RELEASE_INTERRUPT_READY" in line:
                        process.send_signal(signal.SIGSTOP)  # Freeze ONLY our JVM's live mixed-impact checkpoint.
                        try:
                            shutil.copytree(server / "wave_world", root / "interrupted-world-backup")
                            shutil.copy2(plugins / "ReleaseCandidateProbe/interrupted.properties", root / "interrupted.properties")
                            (root / "interruption-method.txt").write_text("save-all flush; SIGSTOP owned PID; byte-copy world backup; SIGKILL owned PID\n")
                        finally:
                            process.kill()  # Deliberately no plugin disable, even if backup copying fails.
                        break
            finally:
                deadline.cancel()
        # Rain's native tick bound is unchanged; allow one host/provider pause inside its wall-clock guard.
        code = process.wait(timeout=1200 if mode == "release" else 300)
    except subprocess.TimeoutExpired:
        timed_out = True
        process.stdin.write("stop\n")
        process.stdin.flush()
        try:
            code = process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            code = process.wait()
    (root / "exit.txt").write_text(str(code))
    (root / "timeout.txt").write_text(str(timed_out))
text = (root / "console.log").read_text()
print(text[-14000:])
marker = {"smash": "SMASH_ENCOUNTER_PROBE", "wave": "DRAGON_WAVE_PROBE", "final": "FINAL_WAVE_PROBE", "opening": "OPENING_ROSTER_PROBE",
          "release": "RELEASE_CANDIDATE_PROBE", "rotation": "ROTATION_PROBE", "second": "SECOND_WAVE_PROBE", "dive": "DIVE_ENCOUNTER_PROBE",
          "damage": "DRAGON_DAMAGE_PROBE", "flight": "DRAGON_FLIGHT_PROBE", "defender": "DEFENDER_ROAMING_PROBE"}[mode]
passed = code == 0 and marker + " PASS" in text and marker + " FAIL" not in text and not re.search(r"\bERROR\]", text)
if mode == "release":
    passed = code == -9 and "RELEASE_INTERRUPT_READY" in text and "PROBE FAIL" not in text and not re.search(r"\bERROR\]", text)
    if snapshot is not None:
        passed = code == 0 and "RELEASE_RESTART_PROBE PASS" in text and "PROBE FAIL" not in text and not re.search(r"\bERROR\]", text)
if passed and mode in ("smash", "release") and snapshot is None:
    with (root / "console-restart.log").open("w") as log:
        process = subprocess.Popen([
            str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xms512M", "-Xmx2G",
            "-Dterminal.jline=false", "-Dterminal.ansi=false", f"-Dkreiscraft.{mode}-probe=true",
            f"-Dkreiscraft.{mode}-restart=true", "-jar", "paper-26.3-249.jar", "--nogui",
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
    restart_marker = {"smash": "SMASH_RESTART_PROBE", "release": "RELEASE_RESTART_PROBE"}[mode]
    passed = code == 0 and restart_marker + " PASS" in text and " FAIL" not in text and not re.search(r"\bERROR\]", text)
if passed and mode == "release":
    # Independent disabled-startup fork of the SAME interrupted active world, not already-cleaned residue.
    disabled = root / "disabled-server"
    disabled.mkdir(exist_ok=False)
    for name in ("libraries", "cache", "versions"):
        shutil.copytree(cache / name, disabled / name)
    for name in ("eula.txt", "paper-26.3-249.jar"):
        shutil.copy2(cache / name, disabled / name)
    shutil.copytree(root / "interrupted-world-backup", disabled / "wave_world")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        disabled_port = sock.getsockname()[1]
    (disabled / "server.properties").write_text((server / "server.properties").read_text().replace(
        f"server-port={port}", f"server-port={disabled_port}"))
    disabled_plugins = disabled / "plugins"
    disabled_plugins.mkdir()
    shutil.copy2(production, disabled_plugins / "Kreiscraft.jar")
    shutil.copy2(probe, disabled_plugins / "ReleaseCandidateProbe.jar")
    (disabled_plugins / "Kreiscraft").mkdir()
    (disabled_plugins / "Kreiscraft/config.yml").write_text((plugins / "Kreiscraft/config.yml").read_text().replace(
        "    enabled: true", "    enabled: false"))
    (disabled_plugins / "ReleaseCandidateProbe").mkdir()
    shutil.copy2(root / "interrupted.properties", disabled_plugins / "ReleaseCandidateProbe/interrupted.properties")
    (root / "disabled-port.txt").write_text(str(disabled_port))
    with (root / "console-disabled-restart.log").open("w") as log:
        process = subprocess.Popen([
            str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xms512M", "-Xmx2G",
            "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Dkreiscraft.release-probe=true",
            "-Dkreiscraft.release-restart=true", "-Dkreiscraft.release-disabled-restart=true",
            "-jar", "paper-26.3-249.jar", "--nogui",
        ], cwd=disabled, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        (root / "owned-disabled-restart-pid.txt").write_text(str(process.pid))
        try:
            code = process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            process.kill()
            code = process.wait()
        (root / "exit-disabled-restart.txt").write_text(str(code))
    text = (root / "console-disabled-restart.log").read_text()
    print(text[-14000:])
    passed = code == 0 and "RELEASE_DISABLED_RESTART_PROBE PASS" in text and " FAIL" not in text and not re.search(r"\bERROR\]", text)
if passed and mode == "release":
    print("RELEASE_CANDIDATE_VERIFICATION PASS nativeAttempts+enabledInterruptedRestart+disabledInterruptedRestart")
ports = [port] + ([disabled_port] if (root / "disabled-port.txt").exists() else [])
released = {}
for owned_port in ports:
    with socket.socket() as sock:
        released[owned_port] = sock.connect_ex(("127.0.0.1", owned_port)) != 0
(root / "ports-released.json").write_text(json.dumps(released, indent=2))
passed = passed and not timed_out and all(released.values())
sys.exit(0 if passed else 1)
