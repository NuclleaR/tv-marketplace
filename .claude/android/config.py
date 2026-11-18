"""Reads the project's emulator settings from .claude/android.json at the repository root."""

import json
import os
import shutil
import re
import subprocess
import sys
from pathlib import Path

CONFIG_PATH = Path(".claude") / "android.json"
STATE_ROOT = Path.home() / ".android" / "agent-emulators"

DEFAULTS = {
  # the applicationId of the build the agent installs, e.g. "com.example.app.debug"
  "package": None,
  # optional: "com.example.app/.MainActivity"; resolved from the launcher intent when left out
  "activity": None,
  # Gradle task that builds and installs the debug app on ANDROID_SERIAL
  "installTask": ":app:installDebug",
  # optional shell command run once in `prepare`, with ANDROID_SERIAL set, after the app is installed and started:
  # seed test data, sign in to a test account, tap through onboarding with ui.py
  "setup": None,
  # optional: the base AVD's name, shared by every worktree of the repository
  "avd": None,
  "api": 36,
  "device": "pixel_9",
  "imageTag": "google_apis_playstore",
  # "host" is fast; use "swiftshader_indirect" on a machine without a usable GPU
  "gpu": "host",
  "maxInstances": 4,
  "idleHours": 3,
}


def git(*args, cwd=None):
  result = subprocess.run(["git", *args], capture_output=True, text=True, cwd=cwd)
  if result.returncode != 0:
    sys.exit(f"git {' '.join(args)} failed: {result.stderr.strip()}. Run the scripts from inside the repository.")
  return result.stdout.strip()


def repo_root():
  return Path(git("rev-parse", "--show-toplevel"))


def main_checkout():
  """The main checkout, the same for every worktree, so they share one base AVD."""
  common = Path(git("rev-parse", "--path-format=absolute", "--git-common-dir"))
  return common.parent if common.name == ".git" else common


def load():
  path = repo_root() / CONFIG_PATH
  config = dict(DEFAULTS)
  if path.is_file():
    config.update(json.loads(path.read_text()))
  if not config["avd"]:
    config["avd"] = "agent-" + re.sub(r"[^a-z0-9]+", "-", main_checkout().name.lower()).strip("-")
  config["path"] = path
  return config


def require_package(config):
  if not config["package"]:
    sys.exit(
      f'No "package" in {config["path"]}. Create it with the applicationId of the debug build, e.g.\n'
      '{"package": "com.example.app.debug", "installTask": ":app:installDebug"}',
    )
  return config["package"]


def state_dir(config):
  return STATE_ROOT / config["avd"]


def sdk_dir():
  for tool in ["adb", "emulator"]:
    found = shutil.which(tool)
    if found:
      return Path(found).resolve().parent.parent
  for variable in ["ANDROID_HOME", "ANDROID_SDK_ROOT"]:
    if os.environ.get(variable):
      return Path(os.environ[variable])
  properties = repo_root() / "local.properties"
  if properties.is_file():
    for line in properties.read_text().splitlines():
      if line.startswith("sdk.dir="):
        return Path(line.removeprefix("sdk.dir="))
  sys.exit("No Android SDK found. Put adb on the PATH, set ANDROID_HOME, or add sdk.dir to local.properties.")


def adb_path():
  return shutil.which("adb") or str(sdk_dir() / "platform-tools" / "adb")
