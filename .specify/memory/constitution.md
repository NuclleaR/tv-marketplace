# TV Marketplace Constitution

## Core Principles

### I. TV-First, Remote-Only UX

Every screen is designed for the 10-foot experience and a D-pad remote before anything else.

- Every user flow MUST be completable with D-pad (up/down/left/right), Select, and Back only.
  Touch, mouse, or keyboard input MUST NOT be required.
- Every screen MUST define an initial focus target, and focus MUST never be lost or land on a
  non-interactive element. Focus order MUST be predictable and spatially consistent.
- Back MUST always do something sensible: close the overlay, return to the previous screen, or
  exit from the root. It MUST NOT be swallowed silently.
- Focused elements MUST have a clearly visible focus indicator (scale, border, or elevation).
- Text and touch-target sizing MUST follow Android TV design guidelines (overscan-safe margins,
  minimum readable text sizes at 10 feet). TalkBack MUST announce every focusable element.

Rationale: A marketplace on TV fails if a user with only a remote cannot browse, select, and
purchase. Focus bugs are the most common and most damaging TV defects.

### II. Test-First (NON-NEGOTIABLE)

TDD is mandatory for all production code.

- Tests MUST be written first, reviewed, and observed failing before implementation begins
  (Red → Green → Refactor).
- Domain and data layers MUST have unit tests (JUnit + coroutine test utilities); ViewModels
  MUST have state/event tests.
- Each user story MUST have at least one Compose UI test that drives it via D-pad key events,
  including focus assertions.
- Contracts with remote APIs MUST be covered by tests against fakes or recorded fixtures; tests
  MUST NOT depend on live network.
- A failing or flaky test MUST block merge; flaky tests are fixed or quarantined with a tracked
  issue, never ignored.

Rationale: TV apps are slow to verify manually on device; an automated, test-first safety net
keeps feature velocity without regressions in navigation and purchase flows.

### III. Modular, Layered Architecture

The codebase is organized into Gradle modules with strict layering and unidirectional data flow.

- Code MUST be split into `:app`, `:feature:*`, `:core:*` (e.g. `:core:ui`, `:core:data`,
  `:core:domain`, `:core:testing`) modules. Feature modules MUST NOT depend on each other.
- Layers are UI → domain → data. Dependencies MUST point inward only; domain MUST be pure
  Kotlin with no Android framework dependencies.
- UI state MUST flow one way: ViewModel exposes immutable state (`StateFlow`), UI sends events.
  Composables MUST be stateless where practical and MUST NOT perform I/O.
- Dependencies MUST be injected (Hilt or an equivalent DI framework chosen in the plan); no
  service locators or global mutable singletons.
- Each module MUST have a single clear purpose; organizational-only modules are not allowed.

Rationale: Isolation keeps build times low, enables independent testing per feature, and stops
marketplace concerns (catalog, details, checkout, account) from tangling.

### IV. Performance Budgets on Low-End TV Hardware

Performance is a feature with explicit, measured budgets on a low-end reference device
(≈2 GB RAM Google TV-class streamer; exact device fixed in the first plan).

- Cold start to first interactive, focused screen MUST be ≤ 2.0 s (p90) on the reference device.
- D-pad navigation and row scrolling MUST sustain 60 fps with no janky frames > 5% in
  Macrobenchmark runs; focus response to a key press MUST be ≤ 100 ms.
- Steady-state app heap MUST stay ≤ 150 MB; images MUST be loaded at display size with a
  bounded memory cache.
- Baseline Profiles MUST be generated and shipped for startup and primary browse flows.
- Any change that regresses a budget MUST be fixed or explicitly justified in the plan's
  Complexity Tracking before merge.

Rationale: TV hardware is far weaker than phones, and users abandon laggy remote navigation.
Budgets make "fast enough" objective and testable.

### V. Simplicity & Observability

Build the simplest thing that satisfies the spec, and make it diagnosable in the field.

- YAGNI: features, abstractions, and dependencies MUST be justified by a current requirement.
  New third-party libraries MUST be justified in the plan.
- Logging MUST be structured and leveled; release builds MUST NOT log PII, tokens, or payment
  data.
- Crashes and ANRs MUST be reported via a crash-reporting tool; key flows (browse, details,
  purchase) MUST emit analytics/trace events sufficient to debug failures.
- Errors MUST surface to the user as a focusable, actionable state (retry/back), never a dead
  screen.

Rationale: Simple code is easier to keep fast and correct on TV; observability compensates for
the difficulty of reproducing issues on living-room devices.

## Technology Stack & Platform Constraints

- Language: Kotlin only for production code; Java MUST NOT be introduced.
- UI: Jetpack Compose with Compose for TV (`androidx.tv`) components. Legacy Leanback MUST NOT
  be used for new screens.
- Async: Kotlin Coroutines and Flow. RxJava MUST NOT be introduced.
- Build: Gradle Kotlin DSL with a version catalog (`libs.versions.toml`); convention plugins for
  shared module configuration.
- Platform: Android TV / Google TV. The manifest MUST declare `android.software.leanback` and
  MUST NOT require touchscreen. `minSdk` and `targetSdk` are set in the plan; `targetSdk` MUST
  track the current Google Play requirement.
- Static analysis: ktlint/Detekt and Android Lint MUST pass with zero new warnings.

## Development Workflow & Quality Gates

- Work follows the Spec Kit flow: specify → clarify (as needed) → plan → tasks → implement.
  Every plan MUST include a Constitution Check against Principles I–V.
- All changes land via pull request on a feature branch; direct commits to `main` are forbidden.
- Merge gates (all MUST pass in CI): build, unit tests, Compose UI tests, Lint/Detekt/ktlint,
  and — for changes touching startup or browse flows — Macrobenchmark budget checks.
- Code review MUST verify D-pad operability, test-first evidence (tests in the diff precede or
  accompany implementation), layering rules, and performance impact.
- Any deviation from a principle MUST be recorded in the plan's Complexity Tracking with the
  reason and the simpler alternative that was rejected.

## Governance

- This constitution supersedes all other development practices for this project. Where other
  docs conflict, this document wins.
- Amendments are made via `/speckit-constitution`, submitted as a pull request with a Sync
  Impact Report, and require approval by the project maintainer.
- Versioning follows semantic versioning:
  - MAJOR: removal or backward-incompatible redefinition of a principle or governance rule.
  - MINOR: a new principle or section, or materially expanded guidance.
  - PATCH: clarifications, wording, and non-semantic fixes.
- Compliance is reviewed at every `/speckit-plan` (Constitution Check) and every PR review.
  Unjustified violations block merge.
- Runtime development guidance lives in `CLAUDE.md` at the repository root (to be created) and
  MUST stay consistent with this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-11-18 | **Last Amended**: 2026-11-18
