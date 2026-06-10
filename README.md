# Nested List A11y — TalkBack demo

A standalone, single-screen Jetpack Compose app demonstrating **two container
variants of a three-level nested interactive list**, implemented with
first-party Compose semantics only (no third-party libraries, no custom
`AccessibilityDelegate`). The two variants are swapped in place on the one
screen — there is no navigation.

Use it to (a) feel how a correctly-built nested list behaves under TalkBack and
(b) see the exact semantics APIs Google recommends — then apply the same
pattern to your own list.

The app ships **two variants of the same accessible tree**, selectable with a
segmented-button switcher at the top of the screen. They render identically to
TalkBack; only the Compose container differs:

- **Column** — [`NestedAccessibleList.kt`](app/src/main/java/com/example/nestedlist/NestedAccessibleList.kt):
  the baseline. Plain nested `Column`s, a small fixed tree composed eagerly.
  This is where the accessibility logic — and a long comment block explaining
  what TalkBack *actually* announces vs. what it does not — lives.
- **LazyColumn** — [`LazyNestedAccessibleList.kt`](app/src/main/java/com/example/nestedlist/LazyNestedAccessibleList.kt):
  the same tree built for large / on-demand data with a `LazyColumn` outer
  list plus nested collections. Read the baseline first; this file documents
  only what changes (and why) when the outer container becomes lazy — explicit
  `CollectionInfo` to supply the count a lazy list omits, hoisted state keyed
  by node id, and a stable per-item `key` so state and focus survive recycling.

## What the demo contains

```
Column variant            LazyColumn variant
Heading: "Product         Heading: "Departments"
 Categories"
└─ Category    (expand)   └─ Department  (expand)
   └─ Subcat.  (expand)      └─ Team      (expand)
      └─ Product (checkbox)     └─ Function (checkbox)
```

Level 1 & 2 are expand/collapse branches (`Role.Button`); Level 3 leaves are
selectable (`Role.Checkbox`).

## Project layout

```
settings.gradle.kts
build.gradle.kts                      root plugins
gradle.properties
gradle/wrapper/gradle-wrapper.properties
app/
  build.gradle.kts                    Compose + Material3 deps
  proguard-rules.pro
  src/main/
    AndroidManifest.xml
    java/com/example/nestedlist/
      MainActivity.kt                 thin host: theme + scaffold + demo switcher
      NestedAccessibleList.kt         the accessible nested list (Column baseline)
      LazyNestedAccessibleList.kt     the same tree, LazyColumn variant
    res/values/
      strings.xml
      themes.xml
```

Toolchain: AGP 8.7.3 · Kotlin 2.1.0 · Gradle 8.9 · compileSdk 35 · minSdk 24 ·
Compose BOM 2024.12.01 · JDK 17.

---

## Build & install — Android Studio (recommended)

1. **File ▸ Open** and select this folder.
2. On first sync, Studio generates the missing `gradle/wrapper/gradle-wrapper.jar`
   and downloads the SDK/build tools as needed. Let the Gradle sync finish.
3. Connect a device (or start an emulator) and press **Run ▸ Run 'app'**, or
   build the APK directly with **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
4. The debug APK lands at:
   `app/build/outputs/apk/debug/app-debug.apk`

## Build & install — command line

The command line needs the Gradle wrapper jar. Generate it once (either let
Android Studio sync the project, or, if you install standalone Gradle, run
`gradle wrapper --gradle-version 8.9`). After that:

```powershell
# from the project root
.\gradlew.bat assembleDebug          # build the debug APK
.\gradlew.bat installDebug           # build + install to the connected device
```

Point Gradle at your SDK if it can't find it by creating `local.properties`
(use the path to your own Android SDK):

```
# Windows
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
# macOS
sdk.dir=/Users/<you>/Library/Android/sdk
# Linux
sdk.dir=/home/<you>/Android/Sdk
```

Manual install of a built APK:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Testing with TalkBack

1. On the device: **Settings ▸ Accessibility ▸ TalkBack ▸ On**
   (or hold both volume keys if the shortcut is enabled).
2. Launch **Nested List A11y**.
3. Use the **Column / LazyColumn** switcher at the top to pick a variant — both
   announce identically; only the underlying container differs.
4. Swipe right/left to move focus; double-tap to activate the focused row.

### What you should hear (realistic expectations)

These examples use the **Column** variant (the "Product Categories" tree); the
**LazyColumn** variant behaves the same way over its "Departments" tree, with
its own announcements documented in
[`LazyNestedAccessibleList.kt`](app/src/main/java/com/example/nestedlist/LazyNestedAccessibleList.kt).

- Title: **"Product Categories, heading"** — reachable via heading navigation.
- Entering a list container: the list **size is spoken once**, e.g.
  *"in list, 2 items"*. Entering an expanded subgroup reannounces the new
  size — that entry cue is the **depth signal**.
- A branch row: **"Electronics, collapsed, 1 of 2, in list, 2 items, Double tap
  to activate actions available. Use Tap with three fingers to view."**
- A leaf row: **"Laptops checkbox, not checked, 1 of 2. In list, 2 items."**

### Per-item index ("n of m") announcements

Because we manually set `CollectionInfo` on the parent containers and `CollectionItemInfo` on each row, modern TalkBack **will announce the index in the list** in many cases as you navigate (e.g., "1 of 2", "2 of 2"). 

However, this cannot be 100% relied upon across all environments:
- TalkBack's exact speech behavior depends heavily on the user's specific TalkBack settings (such as **Verbosity ▸ List and grid info**) and Android OS/TalkBack versions.
- On some devices or older configurations, TalkBack might only speak the list size upon entering the container rather than on every item.
- We still manually add `CollectionItemInfo` because it generates a correct accessibility node tree that other assistive technologies, such as **Switch Access, Braille displays, and automated scanners**, rely upon.

### What is NOT spoken
- **No spoken hierarchy "level"** — TalkBack has no tree/node depth role on Android. The primary depth signal is the size announcement when entering a nested subgroup.

---

## Keyboard accessibility

An interactive list **should be fully operable with a hardware keyboard or D-pad**,
not just touch and TalkBack — it's part of the same accessibility baseline.

This demo gets that for free by sticking to first-party interactive modifiers and
Material components. Because each row uses `Modifier.clickable` / `Modifier.toggleable`
(and the variant switcher is a Material `SegmentedButton`), every control is
focusable and operable with no extra code:

- **Tab / arrow keys (or D-pad)** move focus between the switcher and the rows.
- **Enter** (also **NumPad Enter** or **D-pad center**) activates a branch
  (expand/collapse) or toggles a leaf checkbox.

> **Gotcha:** Compose's `clickable`/`toggleable` activate on Enter / NumPad Enter /
> D-pad center — **not the Space bar**, unlike native Android Views and HTML
> buttons. If you need Space to activate too, add an `onKeyEvent` handler for
> `Key.Spacebar` yourself.

Test it by pairing a Bluetooth/USB keyboard, or use the emulator's D-pad
(**arrow keys + Enter**). The takeaway for your own lists: build interactive rows
from `clickable`/`toggleable` rather than raw pointer-input handling, and keyboard
focus and Enter/D-pad activation come along with the click semantics.
