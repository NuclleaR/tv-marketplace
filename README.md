# TV Marketplace

## Build

```sh
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug
./gradlew :app:installDebug         # on the connected device or emulator
./gradlew appUnitTest               # every unit test
./gradlew formatKotlin lintKotlin   # format, then check
```

Needs JDK 21 and the Android SDK (`ANDROID_HOME` or `sdk.dir` in `local.properties`).

## Modules

`:app → :features:* → :navigation → :core:*`. Features never depend on each other, core never depends on features,
and only `:app` depends on implementation modules such as `:core:data:impl`. The build fails when a module breaks
these rules (`build-logic/src/main/kotlin/checkModuleDependencies.kt`).

A new screen:

1. Add a destination to `navigation/.../Destination.kt`.
2. Create `features/<name>` with a `build.gradle.kts` like `features/home`, and `include` it in
   `settings.gradle.kts`.
3. Contribute a `NavEntryProvider` `@IntoSet` from the feature, as in `HomeScreen.kt`.
4. Add the module to `app/build.gradle.kts`.
