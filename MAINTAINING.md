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

## Where things are

- `src/opentrap/` - application source (Java)
- `icon/opentrap.svg`, `src/opentrap/ui/icons/` - the logo (README, launcher, app window)
- `docs/` - the GitHub Pages site (`index.md`) and README screenshots (`images/`)
- `tools/versions` - pinned versions of the JDK, ZAP engine, testing browser and vendored libraries
- `lib/` - vendored compile-time libraries (BouncyCastle, Brotli decoder)
