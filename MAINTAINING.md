# Maintaining OpenTRAP

Notes for building, releasing, and picking the project back up on a fresh clone.
For contributing changes, see [CONTRIBUTING.md](CONTRIBUTING.md); for the bundled
components and their licences, see [DEPENDENCIES.md](DEPENDENCIES.md) and
[CREDITS.md](CREDITS.md).

## Fresh clone: get a working tree

A clone has the source but not the large, fetched tools (they are gitignored).
Fetch them once:

```
./fetch-jdk.sh       # portable Temurin JDK into jdk/  (required for a portable AppImage)
./fetch-zap.sh       # OWASP ZAP scan engine into engine/
./fetch-browser.sh   # optional: the testing browser into browser/
```

Pinned versions live in `tools/versions`, the single source of truth; the fetch
scripts install those versions. Bump a line there as part of a release, then
re-run the matching fetch script.

## Build and run

```
./build.sh      # lean dev jar  -> dist/opentrap.jar
./run.sh        # launch the dev jar (auto HiDPI scale; ./run.sh 2 to force)
./test.sh       # run the test suite
```

## Release (the AppImage is the only published artefact)

```
./package-appimage.sh    # builds the release jar, then the AppImage -> dist/opentrap-x86_64.appimage
```

Test the AppImage by hand, then publish:

```
./publish.sh <timestamp> <notes.md>
```

- `<timestamp>` is the build **embedded in the AppImage**. `package-appimage.sh`
  calls `release.sh`, which stamps a fresh `opentrap_<timestamp>.jar`, so the
  timestamp to publish is the jar inside the AppImage, not an earlier one left in
  `dist/`. Find it with:
  `./dist/opentrap-x86_64.appimage --appimage-extract 'usr/lib/app/*'` then
  `ls squashfs-root/usr/lib/app/`.
- `publish.sh` creates the GitHub release, uploads the AppImage under the fixed
  name `opentrap-x86_64.appimage`, marks it latest, and verifies that the
  permanent link
  `https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage`
  resolves to the new tag.

## Critical: AppImage portability (glibc)

The AppImage must be built from the **portable Temurin JDK** in `jdk/`
(`./fetch-jdk.sh`), not the system JDK. Temurin is built on glibc 2.17, so the
packaged app runs across mainstream Linux. Building the runtime from a
bleeding-edge system JDK (for example on Kali, glibc 2.38) produces an AppImage
that quits before showing its window on older distributions such as Ubuntu LTS and
Debian stable, and fails the AppImageHub launch-and-screenshot test.

`build.sh`, `release.sh` and `package-appimage.sh` automatically prefer `jdk/` when
it is present, and `package-appimage.sh` fetches it if missing. Temurin ships
without `jmods`, so `package-appimage.sh` uses the JDK as the runtime image
directly rather than running `jlink`.

Verify portability after building: every binary in the AppImage should require no
newer than `GLIBC_2.17`:

```
dist/opentrap-x86_64.appimage --appimage-extract >/dev/null
find squashfs-root -type f \( -name '*.so*' -o -perm -u+x \) \
  -exec sh -c 'readelf -V "$1" 2>/dev/null | grep -oE "GLIBC_[0-9]+\.[0-9]+"' _ {} \; \
  | sort -V | tail -1
```

## How it fits together

A few parts are non-obvious and worth knowing before changing them.

**The scan engine runs as a separate process.** The release jar embeds the trimmed
OWASP ZAP engine as a resource (`/opentrap/engine/engine.zip`, put there by
`release.sh`). On first use `opentrap.engine.ZapEngine` extracts it once to
`~/.opentrap/engine`, then launches it **headless as its own Java process** using
the bundled runtime (`java.home/bin/java`). OpenTRAP never links ZAP in-process; it
drives it over ZAP's local HTTP API (`opentrap.engine.ZapApi`, e.g.
`http://127.0.0.1:8090`). This is why the bundled runtime must keep `bin/java` and
a full module set: the engine is a second JVM, not a library call.

**The testing browser is not bundled in the release.** It is large (~680 MB), so
the release stays small. `opentrap.proxy.EmbeddedBrowser` uses a Chromium found in
the product `browser/` folder (populated by `fetch-browser.sh`, or by the optional
in-app download) or one already on the system `PATH`. It launches with an isolated
profile under `~/.opentrap/browser`, pointed at the proxy
(`--proxy-server=http://127.0.0.1:<port>`) and trusting the proxy CA, so TLS
interception works with nothing to configure.

**HiDPI scaling works by relaunching the JVM.** Java fixes `sun.java2d.uiScale` at
startup, so the UI scales uniformly only if the factor is set before any window is
built. `opentrap.ui.UiScale` picks the factor (the `-Dopentrap.scale` flag, else the
saved preference, else a value detected from the screen) and, when it differs from
the running JVM, relaunches a fresh JVM with the factor applied via
`JAVA_TOOL_OPTIONS`. Inside the AppImage that relaunch is avoided: `AppRun` calls
`OpenTRAP --print-scale` (a headless probe that prints the factor and exits) and
sets the scale up front, because relaunching would tear down the AppImage mount and
kill the app.

**The build identifier is the jar manifest version.** `release.sh` stamps
`Implementation-Version` with the UTC build timestamp; `opentrap.ui.Main.VERSION`
reads it back (a dev build reports `dev`). It appears in the title bar and About
box, and it is the release tag.

## Known limitations and scope

- **Linux x86_64 only.** The release is a single x86_64 Linux AppImage; there are no
  Windows, macOS or ARM builds. The code is portable Java, but the packaging, the
  bundled runtime and the AppImage target are x86_64 Linux.
- **The AppImage requires glibc 2.17 or newer** (see above), which covers
  mainstream distributions from roughly 2014 onward.
- **The testing browser is a separate, optional download**, not part of the
  release; without it OpenTRAP falls back to a system Chromium if one is installed.
- **Local state lives in `~/.opentrap/`** (the extracted engine, the browser
  profile, saved settings). Deleting it returns the app to first-run behaviour.

## Where things are

- `src/opentrap/` - application source (Java)
- `icon/opentrap.svg`, `src/opentrap/ui/icons/` - the logo (README, launcher, app window)
- `docs/` - the GitHub Pages site (`index.md`) and README screenshots (`images/`)
- `tools/versions` - pinned versions of the JDK, ZAP engine, testing browser and vendored libraries
- `lib/` - vendored compile-time libraries (BouncyCastle, Brotli decoder)
