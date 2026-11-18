# CLAUDE.md

TV Marketplace is a multi-module Android app in Kotlin with Jetpack Compose, Metro DI and Navigation3.

## Commands

- Build: `./gradlew :app:assembleDebug`. Install on your emulator: `ANDROID_SERIAL=… ./gradlew :app:installDebug`.
- Format, then lint: `./gradlew formatKotlin`, then `./gradlew lintKotlin`. Android lint: `./gradlew :app:lintDebug`.
- Tests, narrow first: `./gradlew :features:home:testDebugUnitTest --tests "*.HomeViewModelTest"`, then
  `./gradlew appUnitTest`.
- CI runs `./gradlew appUnitTest lintKotlin :app:lintDebug :app:assembleDebug :companion:lintDebug :companion:assembleDebug`.
  Run it before saying a change is done.
- When a task is done, stop what you started: `./gradlew --stop`, and the emulator.

## Architecture

- `:app → :features:* → :navigation → :core:*`. Features never depend on other features; core never on features.
  A contract with several implementations is split into `:api` plus implementations (`:core:data:impl`); only `:app`
  depends on implementations. `build-logic/src/main/kotlin/checkModuleDependencies.kt` fails the build on violations.
- New functionality starts in a feature module; extract to `:core` once a second feature needs it.
- Convention plugins `convention.app`, `convention.library`, `convention.compose`, `convention.ktlint` live in
  `build-logic/`. Namespaces come from the module path. Versions only in `gradle/libs.versions.toml`.
- DI (Metro): constructor `@Inject`, `@ContributesBinding(AppScope::class)` for implementations,
  `@BindingContainer @ContributesTo(AppScope::class) object` for providers, `@AssistedInject` for ViewModels with
  arguments. The graph is `app/.../di/AppGraph.kt`.
- Screens: a stateful `XScreen` gets its ViewModel with `retain { rootGraphAs<XGraph>().… }` and passes the view
  state to a stateless `XView`, which has the previews. A ViewModel is a plain class with
  `@Composable fun viewState()`, tested with Molecule + Turbine.
- Navigation: `@Serializable` destinations in `:navigation`, a `NavEntryProvider` `@IntoSet` per feature, and the
  injected `Navigator` to move.
- Every user-visible string goes in `core/strings`.
- Debug builds open any back stack from the `debugBackStack` extra (`app/.../navigation/StartDestinations.kt`).

## Link sharing (phone → TV)

- `:core:linkserver` runs a Ktor CIO server on the TV's LAN address (port 8765, fallbacks up to 8775) and serves the
  phone page from `src/main/assets/web/`. The API is specified in `specs/001-phone-link-sharing/contracts/`.
- `:companion` is a separate phone app (Share menu target). Its strings live in its own `res/`, not `core/strings`,
  because `:app` lint would report them as unused.
- Logs go through `AppLog`, which only takes names, numbers and enums: never log links, addresses, tokens or device
  names.

## Skills

With the `android` plugin (`/plugin install android@nucllear`), load the matching skill before working:
`architecture`, `gradle`, `testing`, `compose-ui`, `compose-previews`, `localization`, and `emulator` to run the
app (configured in `.claude/android.json`).
