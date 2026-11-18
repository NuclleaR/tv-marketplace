#!/usr/bin/env python3
"""Gives every agent its own emulator, cloned read-only from one prepared base AVD per repository."""

import argparse
import fcntl
import json
import os
import platform
import shutil
import socket
import subprocess
import sys
import time
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent))
import config as project_config  # noqa: E402
import ui  # noqa: E402

PORTS = range(5560, 5586, 2)
CONFIG = project_config.load()
AVD_NAME = CONFIG["avd"]
STATE_DIR = project_config.state_dir(CONFIG)
INSTANCES_DIR = STATE_DIR / "instances"
PREPARED_FILE = STATE_DIR / "prepared.json"
MAX_INSTANCES = int(os.environ.get("AGENT_EMULATOR_MAX", CONFIG["maxInstances"]))
IDLE_HOURS = float(os.environ.get("AGENT_EMULATOR_IDLE_HOURS", CONFIG["idleHours"]))
# The software renderer that -no-window picks on its own can reboot the guest after a snapshot restore.
ADB = project_config.adb_path()
EMULATOR_FLAGS = ["-gpu", CONFIG["gpu"], "-no-audio", "-no-boot-anim"]


def sdk_root():
  return project_config.sdk_dir()


def sdk_tool(*relative_paths):
  for relative_path in relative_paths:
    tool = sdk_root() / relative_path
    if tool.exists():
      return str(tool)
  sys.exit(f"{relative_paths[0]} is missing in the SDK at {sdk_root()}.")


def sdk_env():
  root = str(sdk_root())
  path = os.pathsep.join([f"{root}/platform-tools", f"{root}/emulator", os.environ.get("PATH", "")])
  return {**os.environ, "ANDROID_HOME": root, "ANDROID_SDK_ROOT": root, "PATH": path}


def system_image():
  abi = "arm64-v8a" if platform.machine() in ("arm64", "aarch64") else "x86_64"
  return f"system-images;android-{CONFIG['api']};{CONFIG['imageTag']};{abi}"


@contextmanager
def lock():
  STATE_DIR.mkdir(parents=True, exist_ok=True)
  with open(STATE_DIR / "lock", "w") as file:
    try:
      fcntl.flock(file, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
      print("Waiting for another emulator.py run, e.g. a prepare.", flush=True)
      fcntl.flock(file, fcntl.LOCK_EX)
    yield


def alive(instance):
  # After a reboot, the pid of a stale instance can belong to an unrelated process.
  command = subprocess.run(["ps", "-o", "command=", "-p", str(instance["pid"])], capture_output=True, text=True).stdout
  return f"-avd {AVD_NAME}" in command and f"-port {instance['port']}" in command


def instances():
  INSTANCES_DIR.mkdir(parents=True, exist_ok=True)
  result = []
  for file in sorted(INSTANCES_DIR.glob("*.json")):
    instance = json.loads(file.read_text())
    instance["file"] = file
    instance["idle_hours"] = (time.time() - file.stat().st_mtime) / 3600
    if alive(instance):
      result.append(instance)
    else:
      file.unlink()
  return result


def owner_key(args):
  return args.name or str(project_config.repo_root())


def port_free(port):
  for candidate in [port, port + 1]:
    with socket.socket() as probe:
      if probe.connect_ex(("127.0.0.1", candidate)) == 0:
        return False
  return True


def kill(instance, reason=None):
  serial = instance["serial"]
  if reason:
    print(f"Stopping {serial} of {instance['owner']}: {reason}")
  subprocess.run([ADB, "-s", serial, "emu", "kill"], capture_output=True, env=sdk_env())
  deadline = time.monotonic() + 20
  while alive(instance) and time.monotonic() < deadline:
    time.sleep(0.5)
  if alive(instance):
    os.kill(instance["pid"], 9)
  instance["file"].unlink(missing_ok=True)


def reap():
  for instance in instances():
    if instance["owner"].startswith("/") and not Path(instance["owner"]).exists():
      kill(instance, "its worktree is gone")
    elif instance["idle_hours"] > IDLE_HOURS:
      kill(instance, f"unused for {instance['idle_hours']:.1f} hours")


def adb_ok(serial, *args):
  result = subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, env=sdk_env())
  return result.returncode == 0, result.stdout.strip()


def wait_for_boot(serial, process, timeout=300):
  deadline = time.monotonic() + timeout
  stable_since = None
  while time.monotonic() < deadline:
    if process.poll() is not None:
      return False
    ok, value = adb_ok(serial, "shell", "getprop", "sys.boot_completed")
    if ok and value == "1":
      stable_since = stable_since or time.monotonic()
      # adb reconnects once right after a snapshot restore
      if time.monotonic() - stable_since > 2:
        return True
    else:
      stable_since = None
    time.sleep(0.5)
  return False


def launch(port, extra_flags, log_file):
  emulator = sdk_tool("emulator/emulator")
  command = [emulator, "-avd", AVD_NAME, "-port", str(port), *EMULATOR_FLAGS, *extra_flags]
  with open(log_file, "w") as log:
    return subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, env=sdk_env(), start_new_session=True)


def command_start(args):
  owner = owner_key(args)
  if not PREPARED_FILE.is_file():
    print(f"The base AVD {AVD_NAME} isn't prepared yet, preparing it first.")
    prepare(args, only_if_missing=True)
  with lock():
    reap()
    running = instances()
    own = next((instance for instance in running if instance["owner"] == owner), None)
    if own:
      own["file"].touch()
      print(f"Reusing your running emulator.\nANDROID_SERIAL={own['serial']}")
      return
    if len(running) >= MAX_INSTANCES:
      listing = "\n".join(f"  {instance['serial']}  {instance['owner']}" for instance in running)
      sys.exit(
        f"All {MAX_INSTANCES} agent emulators are taken:\n{listing}\n"
        "Wait for one to stop, or raise AGENT_EMULATOR_MAX if the machine has memory to spare.",
      )
    taken = {instance["port"] for instance in running}
    port = next((port for port in PORTS if port not in taken and port_free(port)), None)
    if port is None:
      sys.exit("No free emulator port left.")
    flags = ["-read-only"]
    if not args.window:
      flags.append("-no-window")
    log_file = INSTANCES_DIR / f"{port}.log"
    process = launch(port, flags, log_file)
    serial = f"emulator-{port}"
    state_file = INSTANCES_DIR / f"{port}.json"
    started = datetime.now().isoformat(timespec="seconds")
    state_file.write_text(json.dumps({"serial": serial, "port": port, "pid": process.pid, "owner": owner, "started": started}))
  if not wait_for_boot(serial, process):
    subprocess.run([ADB, "-s", serial, "emu", "kill"], capture_output=True, env=sdk_env())
    process.kill()
    state_file.unlink(missing_ok=True)
    sys.exit(f"{serial} didn't boot. Log: {log_file}")
  prepared = json.loads(PREPARED_FILE.read_text())
  print(f"Booted a clone of {AVD_NAME} with the app from {prepared['commit']}.\nANDROID_SERIAL={serial}")


def command_stop(args):
  with lock():
    running = instances()
    if args.all:
      targets = running
    else:
      owner = owner_key(args)
      targets = [instance for instance in running if instance["owner"] == owner]
    if not targets:
      print("No agent emulator of yours is running.")
    for instance in targets:
      kill(instance)
      print(f"Stopped {instance['serial']}.")


def command_list(args):
  running = instances()
  if not running:
    print("No agent emulators are running.")
  for instance in running:
    print(f"{instance['serial']}  started {instance['started']}  idle {instance['idle_hours']:.1f}h  {instance['owner']}")
  if PREPARED_FILE.is_file():
    prepared = json.loads(PREPARED_FILE.read_text())
    print(f"Base AVD {AVD_NAME} prepared {prepared['date']} from {prepared['commit']}.")
  else:
    print(f"Base AVD {AVD_NAME} isn't prepared yet.")


def run(command, shell=False, **kwargs):
  if not shell and not shutil.which(command[0]) and not Path(command[0]).exists():
    sys.exit(f"{command[0]} is missing. Install it and run prepare again.")
  print("$ " + (command if shell else " ".join(command)), flush=True)
  result = subprocess.run(command, shell=shell, capture_output=True, text=True, **kwargs)
  if result.returncode != 0:
    sys.exit(f"{result.stdout}{result.stderr}\n{'setup' if shell else command[0]} failed.")
  return result


def create_avd():
  image = system_image()
  sdkmanager = sdk_tool("cmdline-tools/latest/bin/sdkmanager", "tools/bin/sdkmanager")
  avdmanager = sdk_tool("cmdline-tools/latest/bin/avdmanager", "tools/bin/avdmanager")
  if not (sdk_root() / image.replace(";", "/")).is_dir():
    run([sdkmanager, image], input="y\n", env=sdk_env())
  run([avdmanager, "create", "avd", "--force", "-n", AVD_NAME, "-k", image, "-d", CONFIG["device"]], input="no\n", env=sdk_env())
  avd_home = Path(os.environ.get("ANDROID_AVD_HOME", Path.home() / ".android" / "avd"))
  config_ini = avd_home / f"{AVD_NAME}.avd" / "config.ini"
  overrides = {"hw.keyboard": "yes", "hw.gpu.enabled": "yes", "hw.gpu.mode": CONFIG["gpu"]}
  lines = [line for line in config_ini.read_text().splitlines() if line.split("=", 1)[0] not in overrides]
  lines += [f"{key}={value}" for key, value in overrides.items()]
  config_ini.write_text("\n".join(lines) + "\n")


def prepare(args, only_if_missing=False):
  root = project_config.repo_root()
  package = project_config.require_package(CONFIG)
  with lock():
    if only_if_missing and PREPARED_FILE.is_file():
      return
    running = instances()
    if running:
      sys.exit(f"{len(running)} agent emulator(s) still run from the old base. Stop them first (emulator.py stop --all).")
    PREPARED_FILE.unlink(missing_ok=True)
    create_avd()
    port = next(port for port in PORTS if port_free(port))
    serial = f"emulator-{port}"
    os.environ["ANDROID_SERIAL"] = serial
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    process = launch(port, ["-no-window", "-no-snapshot-load", "-wipe-data"], STATE_DIR / "prepare.log")
    env = {**sdk_env(), "ANDROID_SERIAL": serial}
    try:
      if not wait_for_boot(serial, process):
        sys.exit(f"The base AVD didn't boot. Log: {STATE_DIR / 'prepare.log'}")
      if CONFIG["installTask"]:
        run([str(root / "gradlew"), CONFIG["installTask"], "-q"], cwd=root, env=env)
        ui.adb("shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(2)
      if CONFIG["setup"]:
        run(CONFIG["setup"], shell=True, cwd=root, env=env)
    except BaseException:
      subprocess.run([ADB, "-s", serial, "emu", "kill"], capture_output=True, env=sdk_env())
      raise
    commit = project_config.git("log", "-1", "--format=%h %s", cwd=root)
    if project_config.git("status", "--porcelain", cwd=root):
      commit += " with local changes"
    # killing it saves the quick boot snapshot that every agent emulator starts from
    subprocess.run([ADB, "-s", serial, "emu", "kill"], capture_output=True, env=sdk_env())
    process.wait()
    PREPARED_FILE.write_text(json.dumps({"commit": commit, "date": datetime.now().isoformat(timespec="seconds")}))
  print(f"Prepared {AVD_NAME} with the app from {commit}.")


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  owner = argparse.ArgumentParser(add_help=False)
  owner.add_argument("--name", help="who owns the emulator, defaults to the git worktree")
  subparsers = parser.add_subparsers(required=True)

  start_parser = subparsers.add_parser("start", parents=[owner], help="start or reuse your emulator and print its ANDROID_SERIAL")
  start_parser.add_argument("--window", action="store_true", help="show the emulator window, e.g. so the user can watch")
  start_parser.set_defaults(func=command_start)

  stop_parser = subparsers.add_parser("stop", parents=[owner], help="stop your emulator")
  stop_parser.add_argument("--all", action="store_true", help="stop every agent emulator of this repository")
  stop_parser.set_defaults(func=command_stop)

  subparsers.add_parser("list", help="show the running agent emulators").set_defaults(func=command_list)
  subparsers.add_parser("prepare", help="(re)build the base AVD every agent emulator is cloned from").set_defaults(func=prepare)

  args = parser.parse_args()
  args.func(args)


if __name__ == "__main__":
  main()
