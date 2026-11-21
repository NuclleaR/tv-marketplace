# Contract: TV screens and D-pad behavior

This is the UI contract for Constitution Principle I and FR-020/FR-021. Each screen lists its
initial focus, focus order and Back behaviour, and Compose UI tests assert these. The rules shared
with feature 001 apply throughout:
- `focusRestorer()` comes before `focusGroup()` on every row and list;
- returning to a screen focuses the element that opened the next one;
- only standard tv-material controls are used (`Button`, `ListItem`, `Card`, `Surface`, `Switch`).

## Home (changes)

```
┌───────────────────────────────────────────────────────────────────────┐
│ TV Marketplace       [Add app] [Inbox 2] [Settings]                   │
│                                                                       │
│ 3 updates   [Update all] [Check for updates]   checked 2 h ago        │
│                                                                       │
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                           │
│ │ icon   │ │ icon   │ │ icon   │ │ icon   │                           │
│ │SmartTube│ │NewPipe │ │Kodi    │ │Stremio │                           │
│ │1.2 → 1.3│ │Installed│ │Install │ │▓▓▓░ 60%│                           │
│ └────────┘ └────────┘ └────────┘ └────────┘                           │
└───────────────────────────────────────────────────────────────────────┘
```

- **Tiles** show the status line from `AppStatus`, in [data-model.md](../data-model.md):
  `installed → latest` with an update mark, "Installed", "Not installed", a progress bar,
  "No package for this device" or "Source unavailable". Selecting a tile opens **App details**.
  Back from App details focuses that tile again.
- **Updates row**:
  - It appears only when there is at least one update or a check is running. It sits above the
    grid and is reachable by Down from the top buttons.
  - **Update all** starts the install queue. While it runs, the row reads
    "Updating 2 of 3", and **Update all** becomes **Stop after this one**.
  - When the queue ends with failures, the row reads "2 updated, 1 failed" with **Show**, which
    opens an **Update failures** list: one row per app with its reason, Select opens its details.
- **Check for updates** is in the top bar when there are no updates, and in the updates row when
  there are. While a check runs, it's disabled with "Checking…". When requests are limited, the
  row shows "GitHub is limiting requests, try after 14:20" and focus stays on the button.
- Initial focus: unchanged from 001, **Add app**.

## App details (new)

```
┌───────────────────────────────────────────────────────────────────────┐
│ [icon] SmartTube                         yuliskov/SmartTube           │
│        Installed 1.2.0 · Latest 1.3.0 (3 days ago)                    │
│        Description text…                                              │
│                                                                       │
│ [ Update ]  [ Release notes ]  [ Settings ]  [ Remove ]               │
│                                                                       │
│ Packages in 1.3.0                                                     │
│ ┌───────────────────────────────────────────────────────────────────┐ │
│ │ ● SmartTube_1.3.0_armeabi-v7a.apk   18 MB   Recommended           │ │
│ │   SmartTube_1.3.0_arm64-v8a.apk     19 MB                         │ │
│ │   SmartTube_1.3.0_x86.apk           20 MB   Not for this device   │ │
│ └───────────────────────────────────────────────────────────────────┘ │
│ What's new (first lines of the release notes)…                        │
└───────────────────────────────────────────────────────────────────────┘
```

- **Primary action** (FR-002), the first button, with initial focus:

  | State | Primary action |
  |-------|----------------|
  | NotInstalled | Install |
  | UpdateAvailable | Update |
  | UpToDate | Open, which launches the app |
  | Downloading | Cancel, with a progress line beside it |
  | ReadyToInstall | Install now, which shows the system prompt |
  | Failed | Retry |
  | NoPackage / Unavailable | Release notes / Remove |

- **Buttons row**: primary, **Release notes**, **Settings**, **Remove**; Left/Right move between
  them.
- **Packages list** (FR-005):
  - It sits below the buttons; Down from the buttons enters it, on the row the list last focused,
    else the recommended or remembered package's row.
  - Each row shows the file name, size, and a tag: "Recommended", "Your choice" or "Not for this
    device". Incompatible rows are focusable for reading, but disabled.
  - Selecting a compatible row installs or updates with that package: the same flow as the
    primary action, which then shows Cancel.
  - When the user's choice is in effect, a row **Use automatic choice** follows the list.
  - When the stored choice matched nothing in the new release, a note above the list says so.
  - With one compatible package and no incompatible ones, the list is still shown, so the file
    name and size are visible.
- **Notes preview**: the first ~6 lines, not focusable; **Release notes** opens the full text.
- **Install permission** (FR-008):
  - When install needs the permission, a panel replaces the buttons row: "To install apps, allow
    TV Marketplace to install unknown apps", with **Open settings** (focused) and **Not now**.
  - Coming back with the permission granted continues the install without another press.
- **Failure states** (FR-009, FR-021): an inline message under the buttons, with **Retry** as the
  primary action. Back still leaves. A signature conflict explains that uninstalling first would
  remove the app's data, and offers **Retry** and **Back** only: no uninstall shortcut.
- **Back**: Home. A running download continues (FR-006).

## Release notes (new, full screen)

- A single focusable text container, which has initial focus; it renders `notesHtml` with no
  links (R9).
- Up/Down scroll by about 3 lines, long press by a page. The end of the text stops without
  wrapping.
- Back: App details, with focus on **Release notes**.

## App settings (new; dialog over App details)

- **Include pre-releases**: a `ListItem` with a trailing `Switch`, with initial focus (FR-014).
  Toggling it starts a check of this app only.
- Back: closes it, and focus returns to **Settings**.

## Remove (new; dialog over App details)

- Text: "Remove <name> from the list?"
- Buttons: **Keep on TV** (initial focus, the safe choice), **Uninstall too**, **Cancel**. When
  the app isn't installed, the buttons are **Remove** (initial focus) and **Cancel**.
- **Uninstall too** shows the system uninstall confirmation:
  - when the user accepts, the app is uninstalled and removed;
  - when the user declines, the app stays installed, and is still removed from the list (US4 #2).
- After removal: Home, with focus on the tile that took the removed one's place, else **Add app**.
- Back: same as Cancel.

## Update failures (new; from Home)

- A vertical list of failed apps with their reasons; the first row has initial focus.
- Select opens App details; Back returns to Home's **Show**.
