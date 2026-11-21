# Feature Specification: Install, Update and Manage Tracked Apps

**Feature Branch**: `002-app-install-updates`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "TV-native app manager like Obtainium (https://github.com/ImranR98/Obtainium), built for the TV remote, because Obtainium is a Flutter app that works badly with a D-pad. Feature 001 already lets the user add apps (send a GitHub link from the phone, or search GitHub on the TV) and shows the tracked apps on Home. This feature is the rest of the app manager: everything Obtainium does with a tracked app. Opening a tracked app shows its details screen (name, icon, description, source, installed version vs latest release, release notes). Install the app from its latest release: pick the right APK asset for the device (ABI, TV/universal variants, ignore unrelated assets), download it with progress, verify, and install through the system installer, asking for the install-unknown-apps permission when needed. Detect which tracked apps are installed and their installed versions, including apps installed before they were tracked. Check sources for updates in the background on a schedule and on demand, show which apps have updates on Home, and update one or all of them. Handle pre-releases (opt-in per app), apps with no APK in the latest release, GitHub API rate limits, and failed or cancelled downloads. Remove an app from the tracked list, and optionally uninstall it from the TV. Everything is driven by the remote, with focus restoring rules already used in the app. Scope decision to clarify: which sources besides GitHub (Obtainium also supports GitLab, Codeberg/Forgejo, F-Droid and others), and whether app settings like a per-app APK filter are needed."

## Context

The product is a TV-native app manager in the spirit of Obtainium: the user tracks apps by their
source (a GitHub repository today), and the TV installs and keeps them up to date straight from
the releases their authors publish, without an app store. Obtainium itself is unusable with a TV
remote, which is why this app exists.

Feature 001 covers getting an app into the tracked list: a link sent from the phone, or a search
on the TV, then a preview and "Add". Home shows the tracked apps, but selecting one does nothing
yet. This feature makes the tracked list do what an app manager is for: see an app's details,
install it, know when an update is out, update it, and remove it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Install a tracked app from its latest release (Priority: P1)

The user selects a tracked app on Home. A details screen opens with the app's icon, name,
description, source, the latest release's version and its release notes. The user selects
"Install". The TV downloads the right package for this device, shows progress, and hands it to
the system installer; after confirming the system prompt the app is installed and the details
screen shows it as installed, with "Open" as the main action.

**Why this priority**: Installing is the reason the app exists. Without it, tracking a repository
has no value; with only this story, the user can already get any GitHub-released app onto the TV
with the remote.

**Independent Test**: Track a repository whose latest release has an APK, open it from Home,
select Install, accept the system prompt, and launch the installed app from its details screen.

**Acceptance Scenarios**:

1. **Given** a tracked app that is not installed, **When** the user selects it on Home, **Then**
   its details screen opens with Install focused, showing the latest version and its release notes.
2. **Given** the details screen of an app that is not installed, **When** the user selects Install,
   **Then** the download starts, its progress is shown, and Install turns into Cancel.
3. **Given** the download finished, **When** the system installer asks for confirmation and the
   user accepts, **Then** the app is installed and the details screen shows the installed version
   and an Open action that launches it.
4. **Given** this app has never been allowed to install other apps, **When** the user selects
   Install, **Then** the app explains why the permission is needed and opens the system screen to
   grant it; on returning, the installation continues without starting over.
5. **Given** a release with several packages (per-ABI, phone/TV, debug/release), **When** the
   user opens the app, **Then** the details screen lists every APK of the release, the package
   matching this device is marked as recommended and focused, and the user can install any other
   one from the list instead.
6. **Given** a download in progress, **When** the user selects Cancel or the download fails,
   **Then** the partial download is discarded and a focusable Retry is offered.

---

### User Story 2 - Know about updates and install them (Priority: P1)

The TV checks the tracked apps' sources for new releases on its own and when the user asks. Home
marks the apps that have an update and shows how many there are; the user updates one app from
its details screen or all of them at once.

**Why this priority**: Keeping apps current is the other half of the product promise; apps
installed outside a store otherwise never update.

**Independent Test**: Install an older release of a tracked app, trigger "Check for updates",
see the app marked on Home, update it, and confirm the installed version is the latest.

**Acceptance Scenarios**:

1. **Given** an installed tracked app whose source has a newer release, **When** an update check
   runs (scheduled or on demand), **Then** Home marks the app as having an update and shows the
   installed and the new version.
2. **Given** several apps have updates, **When** the user selects "Update all" on Home, **Then**
   each one is downloaded and installed in turn, with overall progress, and the ones that fail are
   listed with a reason and can be retried.
3. **Given** the user is on Home, **When** they select "Check for updates", **Then** the check runs
   with a visible indicator and Home reflects the result when it ends.
4. **Given** an app opted into pre-releases, **When** a pre-release newer than the installed
   version is published, **Then** it counts as an update for that app only.
5. **Given** a newer release that has no installable package, **When** the check runs, **Then**
   the app is not offered as updatable and its details screen says the latest release has no
   package for this device.

---

### User Story 3 - See what is installed, including apps installed before tracking (Priority: P2)

Home and every details screen show whether each tracked app is installed on this TV and which
version, also for apps the user had installed by other means before adding them.

**Why this priority**: Correct installed state is what update detection and the Install/Open
choice depend on; tracking an app that is already installed is common when moving from another
tool.

**Independent Test**: Install an app by other means, then add its repository; its details screen
shows it as installed with the right version and offers Update when a newer release exists.

**Acceptance Scenarios**:

1. **Given** an app installed before it was tracked, **When** the user adds its repository,
   **Then** the app is recognized as installed with its installed version.
2. **Given** a tracked app is uninstalled outside this app, **When** the user returns to Home,
   **Then** it shows as not installed.
3. **Given** a tracked repository whose package cannot be matched to any installed app, **When**
   the user opens it, **Then** it shows as not installed and Install is offered.

---

### User Story 4 - Remove a tracked app (Priority: P2)

From a details screen the user removes an app from the tracked list, and chooses whether to also
uninstall it from the TV.

**Why this priority**: The list must stay manageable, and removing something by mistake from the
list should not silently remove the app from the TV.

**Independent Test**: Remove an installed tracked app, choose "Keep on TV"; it leaves the list and
stays installed. Repeat with "Uninstall too"; the system uninstall prompt appears and the app is
gone after confirming.

**Acceptance Scenarios**:

1. **Given** the details screen of a tracked app, **When** the user selects Remove, **Then** a
   confirmation asks whether to only stop tracking or also uninstall, with the safe choice focused.
2. **Given** the user chooses to also uninstall, **When** they confirm the system prompt, **Then**
   the app is uninstalled and removed from the list; if they decline, it stays installed and is
   still removed from the list.

---

### User Story 5 - Per-app settings (Priority: P3)

From a details screen the user adjusts how an app is tracked: whether pre-releases count as
updates. The package choice itself is made on the details screen (FR-005), not in settings.

**Why this priority**: Most apps work with the defaults; these settings fix the minority where
automatic package choice or release channels do not fit.

**Independent Test**: For an app with two TV-compatible packages, install the non-recommended one
from the details screen's list; the next update uses the matching package of the new release.

**Acceptance Scenarios**:

1. **Given** an app's settings, **When** the user turns on pre-releases, **Then** update checks
   for that app include pre-releases.
2. **Given** the user installed a package other than the recommended one, **When** a newer release
   is found, **Then** the update uses that release's package with the matching name (the same
   name with the version changed), and the details screen shows the choice is the user's, with a
   way to return to the automatic choice. When no package of the new release matches, the
   automatic choice is offered and the user is told the earlier choice no longer applies.

---

### Edge Cases

- The GitHub request limit is reached during a check or an install: the user sees that GitHub is
  limiting requests and when it is worth trying again; nothing is marked wrongly as up to date.
- No network, or the network drops mid-download: the download stops with Retry; already
  installed apps are unaffected.
- The downloaded package is signed by a different key than the installed app: the system refuses
  the update; the user is told the update cannot replace the installed app and that uninstalling
  first would remove its data.
- The downloaded package is a different app (another package name) than the one installed:
  the install is stopped before the system prompt and the user is told why.
- The release is older than the installed version (author deleted a release): no downgrade is
  offered as an update.
- Not enough free storage for the download: the download does not start, the user sees how much
  space is needed.
- The latest release has no package that runs on this device (only other ABIs, or only
  non-Android files): shown as "no package for this device", with the release notes still visible.
- A repository was renamed or deleted: the app shows that its source is no longer available and
  offers Remove; the installed app is kept.
- The user leaves the details screen or the app during a download: the download continues in the
  background and its state is shown again on return; installing still needs the user's confirmation.
- Two update checks overlap (scheduled and manual): only one runs.
- Version names that are not comparable as numbers (dates, commit hashes, prefixes like "v"):
  a release counts as newer when its publication is newer and its version differs from the
  installed one.

## Requirements *(mandatory)*

### Functional Requirements

**Details screen**

- **FR-001**: Selecting a tracked app on Home MUST open its details screen showing icon, name,
  description, source, installed version (or "not installed"), latest version, publication date
  and release notes of the latest release.
- **FR-002**: The details screen MUST offer one primary action that depends on state: Install
  (not installed), Update (installed and a newer release exists), Open (installed and up to date),
  Cancel (download in progress); secondary actions: Remove, app settings, and opening the release
  notes in full.
- **FR-003**: Release notes MUST be readable with the remote: scrollable with the D-pad, rendered
  as formatted text without links that require a pointer.

**Install**

- **FR-004**: The system MUST pick the package from a release automatically: an Android package
  that runs on this device's architecture, preferring a TV or universal variant and a release over
  a debug build; non-package files MUST be ignored.
- **FR-005**: The details screen MUST list every APK of the release shown, with its file name and
  size. The automatically chosen one MUST be marked as recommended and get the initial focus in
  the list; packages for another architecture MUST be marked as not for this device and MUST NOT
  be installable. Selecting any other package installs that one instead.
- **FR-006**: Downloads MUST show progress, MUST be cancellable, and MUST survive leaving the
  screen; a cancelled or failed download MUST leave no partial file behind.
- **FR-007**: Before handing a package to the system installer, the system MUST verify that the
  file is complete and, for an installed app, that it is the same app (package name).
- **FR-008**: Installing MUST go through the system installer and its confirmation; when the app is
  not yet allowed to install other apps, the user MUST be shown why and taken to the system screen
  that grants it, and the install MUST resume after the permission is granted.
- **FR-009**: Install results (success, cancelled by the user, refused by the system with a reason)
  MUST be shown on the details screen as a focusable state.

**Installed state**

- **FR-010**: The system MUST determine for every tracked app whether it is installed and which
  version, including apps installed before they were tracked, by matching the app's package
  identity from its releases.
- **FR-011**: Installed state MUST stay current when apps are installed, updated or removed
  outside this app.

**Updates**

- **FR-012**: The system MUST check tracked apps for new releases on a schedule while the TV is on,
  and on demand from Home. Updates found in the background MUST only be shown; nothing is installed
  until the user chooses to.
- **FR-013**: Home MUST show which apps have updates and how many, and MUST offer "Update all".
- **FR-014**: Pre-releases MUST count as updates only for apps that opted in.
- **FR-015**: A release MUST NOT be offered as an update when it is not newer than the installed
  version or has no package for this device.
- **FR-016**: Update checks MUST stay within the source's request limits; when a limit is reached,
  the check MUST stop, keep the previous results, and say when it can resume.

**Removal**

- **FR-017**: Removing an app MUST ask whether to only stop tracking or also uninstall; only
  tracking stops by default. Uninstalling MUST go through the system's uninstall confirmation.

**Per-app settings**

- **FR-018**: Each app MUST have a setting for including pre-releases. A package the user chose
  from the list (FR-005) MUST be remembered for that app and used for its updates, matched by
  file name with the version ignored; "Update all" MUST use it too. A text filter such as
  Obtainium's APK filter is out of scope.

**Sources**

- **FR-019**: Apps MUST be installable and updatable from GitHub repositories' releases, the only
  source in this feature. F-Droid repositories are planned as a later feature, so the notion of an
  app's source MUST NOT assume GitHub is the only one.

**Remote and focus**

- **FR-020**: Every flow in this feature MUST be completable with the D-pad, Select and Back; every
  screen MUST define its initial focus; returning to Home or a list MUST focus the item that opened
  the screen, and every row or list MUST restore its last focused item.
- **FR-021**: Errors MUST be shown as focusable, actionable states (Retry, Back), never as a
  dead end.

### Key Entities

- **Tracked app**: an app the user follows; its source, display name, icon, per-app settings, and
  the package identity learned from its releases.
- **Release**: a published version at the source; version name, publication date, pre-release
  flag, release notes, and its downloadable files.
- **Package**: an installable file of a release; file name, size, architecture and variant hints.
- **Installed app**: what the TV has installed for a tracked app; package identity and version.
- **Update**: a tracked, installed app whose newest eligible release is newer than the installed
  version, with the package that would be installed.
- **Download**: a package being fetched; progress, state (running, paused by failure, done,
  cancelled), and the file once complete.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a tracked app focused on Home, the user installs it with two Select presses
  (open the app, Install) plus the system confirmation, excluding download time.
- **SC-002**: For 95% of tested popular TV-compatible GitHub apps, the automatically chosen package
  installs and runs without the user changing the choice.
- **SC-003**: An update published at the source is shown on Home within one scheduled check cycle,
  and immediately after a manual check.
- **SC-004**: Updating all apps with pending updates takes one action on Home plus one system
  confirmation per app.
- **SC-005**: No download failure, cancellation or network loss leaves the user without a focused,
  actionable element on screen.
- **SC-006**: The installed state shown matches the TV's actual installed apps in 100% of the
  cases tested, including apps installed or removed outside this app.

## Assumptions

- The TV runs a supported Android TV / Google TV version where sideloading through the system
  installer is possible after the user grants the install-unknown-apps permission; the system
  confirmation prompt for each install is acceptable and expected.
- GitHub is used without the user's account; its anonymous request limits apply, and update checks
  are spaced to stay within them. Signing in to GitHub is out of scope.
- Package identity (package name) is learned from the first package installed or matched; apps
  whose releases never contain an Android package cannot be installed and are shown as such.
- Downloads are stored in the app's private storage and deleted after a successful install.
- Home's tracked-apps grid from feature 001 is reused and extended with installed/update state;
  adding apps (phone link and search) stays as defined in feature 001.
- Sources other than GitHub (F-Droid next, then possibly GitLab, Codeberg/Forgejo, direct links)
  are out of scope for this feature.
- Notifications outside the app are out of scope; update state is shown inside the app on Home.
