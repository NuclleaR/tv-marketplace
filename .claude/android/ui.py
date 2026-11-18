#!/usr/bin/env python3
"""Reads and drives the screen of the device in ANDROID_SERIAL."""

import argparse
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ElementTree
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent))
import config as project_config  # noqa: E402

CONFIG = project_config.load()


def serial():
  value = os.environ.get("ANDROID_SERIAL")
  if not value:
    sys.exit("Set ANDROID_SERIAL to your emulator, e.g. ANDROID_SERIAL=emulator-5560. emulator.py start prints it.")
  return value


def adb(*args, check=True):
  result = subprocess.run([project_config.adb_path(), "-s", serial(), *args], capture_output=True)
  if check and result.returncode != 0:
    sys.exit(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
  return result


def package():
  return project_config.require_package(CONFIG)


def activity():
  if CONFIG["activity"]:
    return CONFIG["activity"]
  resolved = adb("shell", "cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", package())
  lines = [line.strip() for line in resolved.stdout.decode(errors="replace").splitlines() if "/" in line]
  if not lines:
    sys.exit(f"No launcher activity for {package()}. Is the app installed? Or set \"activity\" in {CONFIG['path']}.")
  return lines[-1]


class Node:
  def __init__(self, element, parent):
    self.parent = parent
    self.text = element.get("text", "")
    self.desc = element.get("content-desc", "")
    self.resource_id = element.get("resource-id", "").split("/")[-1]
    self.cls = element.get("class", "").rsplit(".", 1)[-1]
    self.clickable = element.get("clickable") == "true" or element.get("long-clickable") == "true"
    self.scrollable = element.get("scrollable") == "true"
    self.checkable = element.get("checkable") == "true"
    self.checked = element.get("checked") == "true"
    self.selected = element.get("selected") == "true"
    self.focused = element.get("focused") == "true"
    self.enabled = element.get("enabled") == "true"
    self.package = element.get("package", "")
    left, top, right, bottom = map(int, re.findall(r"-?\d+", element.get("bounds", "[0,0][0,0]")))
    self.bounds = (left, top, right, bottom)
    self.children = []

  @property
  def center(self):
    left, top, right, bottom = self.bounds
    return (left + right) // 2, (top + bottom) // 2

  @property
  def own_label(self):
    return self.text or self.desc

  def inner_labels(self):
    """Labels of the descendants that aren't controls of their own."""
    labels = []
    for child in self.children:
      if child.is_control():
        continue
      if child.own_label:
        labels.append(child.own_label)
      labels.extend(child.inner_labels())
    return labels

  @property
  def label(self):
    return self.own_label or " · ".join(self.inner_labels())

  def is_control(self):
    return self.clickable or self.checkable or self.cls == "EditText"

  def inside_control(self):
    current = self.parent
    while current:
      if current.is_control():
        return True
      current = current.parent
    return False

  def has_control_descendant(self):
    return any(child.is_control() or child.has_control_descendant() for child in self.children)

  def is_listed(self):
    if self.is_control():
      return bool(self.label) or not self.has_control_descendant()
    if self.scrollable:
      return True
    return bool(self.own_label) and not self.inside_control()


def dump():
  last_error = ""
  for _ in range(5):
    result = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
    output = result.stdout.decode(errors="replace")
    start = output.find("<?xml")
    end = output.rfind("</hierarchy>")
    if start != -1 and end != -1:
      root = ElementTree.fromstring(output[start:end + len("</hierarchy>")])
      nodes = []

      def walk(element, parent):
        node = Node(element, parent)
        nodes.append(node)
        if parent:
          parent.children.append(node)
        for child in element.findall("node"):
          walk(child, node)

      for element in root.findall("node"):
        walk(element, None)
      return nodes
    last_error = output.strip() or result.stderr.decode(errors="replace").strip()
    # uiautomator fails while the screen is still animating
    time.sleep(0.5)
  sys.exit(f"uiautomator dump failed: {last_error}")


def describe(node):
  parts = [f"{node.center[0]:>4},{node.center[1]:<4}"]
  label = node.label if node.is_control() else node.own_label
  if label:
    parts.append(f'"{label}"')
  if node.resource_id:
    parts.append(f"id={node.resource_id}")
  flags = [
    flag
    for flag, on in [
      ("clickable", node.clickable),
      ("scrollable", node.scrollable),
      ("checked" if node.checked else "unchecked", node.checkable),
      ("selected", node.selected),
      ("focused", node.focused),
      ("disabled", not node.enabled),
      ("edit", node.cls == "EditText"),
    ]
    if on
  ]
  if node.is_control() and not node.label:
    flags.append("NO LABEL")
  parts.extend(flags)
  if CONFIG["package"] and node.package != CONFIG["package"]:
    parts.append(f"[{node.package}]")
  return "  ".join(parts)


def command_dump(args):
  for node in dump():
    if args.all or node.is_listed():
      print(describe(node))


def find(nodes, query, exact):
  query = query.lower()

  def keys(node):
    return [key.lower() for key in [node.own_label, node.label, node.resource_id] if key]

  matches = [node for node in nodes if query in keys(node)]
  if not matches and not exact:
    matches = [node for node in nodes if any(query in key for key in keys(node))]
  targets = []
  for node in matches:
    target = clickable_target(node)
    if target not in targets:
      targets.append(target)
  return targets


def clickable_target(node):
  current = node
  while current:
    if current.clickable:
      return current
    current = current.parent
  return node


def command_tap(args):
  matches = find(dump(), args.query, args.exact)
  if not matches:
    sys.exit(f'Nothing on screen matches "{args.query}". Run ui.py dump to see what is there.')
  if len(matches) <= args.nth:
    sys.exit(f'Only {len(matches)} match(es) for "{args.query}".')
  target = matches[args.nth]
  x, y = map(str, target.center)
  if args.long:
    adb("shell", "input", "swipe", x, y, x, y, "800")
  else:
    adb("shell", "input", "tap", x, y)
  others = f"  ({len(matches)} matches, --nth picks another)" if len(matches) > 1 else ""
  print(f"tapped {describe(target)}{others}")


def command_wait(args):
  deadline = time.monotonic() + args.timeout
  while True:
    matches = find(dump(), args.query, args.exact)
    if bool(matches) != args.gone:
      print(describe(matches[0]) if matches else f'"{args.query}" is gone')
      return
    if time.monotonic() > deadline:
      state = "is still there" if args.gone else "didn't show up"
      sys.exit(f'"{args.query}" {state} within {args.timeout:g}s.')
    time.sleep(0.5)


def command_type(args):
  escaped = args.text.replace("%", "\\%").replace(" ", "%s")
  adb("shell", "input", "text", shlex.quote(escaped))


def command_scroll(args):
  # unlike `wm size`, this follows the rotation
  displays = adb("shell", "dumpsys", "window", "displays").stdout.decode()
  width, height = map(int, re.search(r"cur=(\d+)x(\d+)", displays).groups())
  x = width // 2
  top, bottom = height * 3 // 10, height * 7 // 10
  start, end = (bottom, top) if args.direction == "down" else (top, bottom)
  adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "300")


def scale_down(path, max_size):
  if shutil.which("sips"):
    command = ["sips", "-Z", str(max_size), path]
  elif shutil.which("magick"):
    command = ["magick", path, "-resize", f"{max_size}x{max_size}>", path]
  elif shutil.which("convert"):
    command = ["convert", path, "-resize", f"{max_size}x{max_size}>", path]
  else:
    return False
  return subprocess.run(command, capture_output=True).returncode == 0


def command_screenshot(args):
  path = args.path or tempfile.mkstemp(prefix="screen-", suffix=".png")[1]
  with open(path, "wb") as file:
    file.write(adb("exec-out", "screencap", "-p").stdout)
  if not args.full and scale_down(path, args.max_size):
    print(f"{path} (scaled down: tap by text, or scale coordinates from the dump)")
  else:
    print(f"{path} (full size)")


def check_crash():
  crash = adb("logcat", "-b", "crash", "-d").stdout.decode(errors="replace")
  if crash.strip():
    reason = next((line for line in crash.splitlines() if "Exception" in line and "FATAL" not in line), crash)
    sys.exit(f"The app crashed on start: {reason.split('AndroidRuntime: ', 1)[-1]}")


def command_launch(args):
  adb("logcat", "-b", "crash", "-c")
  command = ["am", "start", "-W", "-S", "-n", activity()]
  for key, value in args.es or []:
    command += ["--es", key, value]
  for key, value in args.ez or []:
    command += ["--ez", key, value]
  for key, value in args.ei or []:
    command += ["--ei", key, value]
  adb("shell", " ".join(shlex.quote(part) for part in command))
  time.sleep(1)
  check_crash()
  print(f"Launched {activity()}")


def command_deeplink(args):
  adb("logcat", "-b", "crash", "-c")
  adb("shell", "am", "force-stop", package())
  command = ["am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", args.uri, package()]
  adb("shell", " ".join(shlex.quote(part) for part in command))
  time.sleep(1)
  check_crash()
  print(f"Opened {args.uri}")


def command_logs(args):
  if args.crash:
    print(adb("logcat", "-b", "crash", "-d").stdout.decode(errors="replace"))
    return
  pid = adb("shell", "pidof", package(), check=False).stdout.decode().strip()
  if not pid:
    sys.exit(f"{package()} isn't running. For a crash, use --crash.")
  output = adb("logcat", "-d", f"--pid={pid}").stdout.decode(errors="replace").splitlines()
  print("\n".join(output[-args.lines:]))


def command_adb(args):
  sys.exit(subprocess.run([project_config.adb_path(), "-s", serial(), *args.args]).returncode)


def mark_used():
  state = project_config.state_dir(CONFIG) / "instances" / f"{serial().removeprefix('emulator-')}.json"
  if state.exists():
    state.touch()


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  subparsers = parser.add_subparsers(required=True)

  dump_parser = subparsers.add_parser("dump", help="list what's on screen: center x,y, label, state")
  dump_parser.add_argument("--all", action="store_true", help="every node, also layout nodes and texts inside controls")
  dump_parser.set_defaults(func=command_dump)

  tap_parser = subparsers.add_parser("tap", help="tap what matches a text, content description, or id")
  tap_parser.add_argument("query")
  tap_parser.add_argument("--exact", action="store_true", help="no substring matches")
  tap_parser.add_argument("--nth", type=int, default=0, help="pick another match, 0-based")
  tap_parser.add_argument("--long", action="store_true", help="long-press")
  tap_parser.set_defaults(func=command_tap)

  wait_parser = subparsers.add_parser("wait", help="wait until something shows up")
  wait_parser.add_argument("query")
  wait_parser.add_argument("--exact", action="store_true", help="no substring matches")
  wait_parser.add_argument("--timeout", type=float, default=15)
  wait_parser.add_argument("--gone", action="store_true", help="wait until it disappears")
  wait_parser.set_defaults(func=command_wait)

  type_parser = subparsers.add_parser("type", help="type into the focused field")
  type_parser.add_argument("text")
  type_parser.set_defaults(func=command_type)

  scroll_parser = subparsers.add_parser("scroll", help="swipe the screen to scroll")
  scroll_parser.add_argument("direction", choices=["down", "up"])
  scroll_parser.set_defaults(func=command_scroll)

  screenshot_parser = subparsers.add_parser("screenshot", help="save a screenshot and print its path")
  screenshot_parser.add_argument("path", nargs="?")
  screenshot_parser.add_argument("--full", action="store_true", help="keep the full resolution")
  screenshot_parser.add_argument("--max-size", type=int, default=1200)
  screenshot_parser.set_defaults(func=command_screenshot)

  launch_parser = subparsers.add_parser("launch", help="force-stop and start the app, with optional intent extras")
  launch_parser.add_argument("--es", nargs=2, action="append", metavar=("KEY", "VALUE"), help="string extra")
  launch_parser.add_argument("--ez", nargs=2, action="append", metavar=("KEY", "VALUE"), help="boolean extra")
  launch_parser.add_argument("--ei", nargs=2, action="append", metavar=("KEY", "VALUE"), help="int extra")
  launch_parser.set_defaults(func=command_launch)

  deeplink_parser = subparsers.add_parser("deeplink", help="restart the app on a deep link")
  deeplink_parser.add_argument("uri")
  deeplink_parser.set_defaults(func=command_deeplink)

  logs_parser = subparsers.add_parser("logs", help="the app's recent log, or the crash buffer")
  logs_parser.add_argument("--crash", action="store_true")
  logs_parser.add_argument("--lines", type=int, default=200)
  logs_parser.set_defaults(func=command_logs)

  adb_parser = subparsers.add_parser("adb", help="run adb against ANDROID_SERIAL: adb shell cmd uimode night yes")
  adb_parser.add_argument("args", nargs=argparse.REMAINDER)
  adb_parser.set_defaults(func=command_adb)

  args = parser.parse_args()
  mark_used()
  args.func(args)


if __name__ == "__main__":
  main()
