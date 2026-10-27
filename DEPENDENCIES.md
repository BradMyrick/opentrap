# Bundled tools and dependencies

OpenTRAP drives a few external tools and vendors a couple of libraries. This is
how they are tracked and kept current across release cycles. It is for
developers; a user never has to think about it.

## What is bundled

| Tool | Version pin | Where | How it is installed | Licence |
|---|---|---|---|---|
| OWASP ZAP (scan engine) | `tools/versions` | `engine/` | `./fetch-zap.sh` | Apache 2.0 |
| Chromium (browser) | `tools/versions` | `browser/` | `./fetch-browser.sh` | BSD |
| BouncyCastle (CA) | `tools/versions` | `lib/` | jar in `lib/` | MIT |
| Brotli decoder | `tools/versions` | `lib/` | jar in `lib/` | MIT |

ZAP is the crawl and active-scan engine: OpenTRAP runs it **headless** (no UI)
as a child process on the loopback and drives it over its JSON API, mapping its
findings into OpenTRAP's own issue model. The user never sees ZAP. It is fetched
on demand (the Core package, no bundled JRE, since OpenTRAP runs on a JVM) rather
than packed into the jar. Chromium is the embedded testing browser. BouncyCastle
and the Brotli decoder are small jars vendored under `lib/` and unpacked into
`dist/opentrap.jar` by the build.

## Single source of truth

`tools/versions` holds the pinned version of every bundled tool. The fetch
scripts install exactly those versions, so a build is reproducible rather than
pulling whatever is latest that day.

## Release-cycle workflow

1. Run the update check:

       ./tools/check-updates.sh

   It prints, for the ZAP engine, Chromium, BouncyCastle and the Brotli
   decoder, the pinned version, the latest upstream version, and whether an
   update is available. The JDK pin is not checked. It changes nothing.

2. For each tool you want to move, edit its line in `tools/versions`.

3. Re-install the bundled binaries:

       ./fetch-zap.sh         # after changing ZAP_VERSION
       ./fetch-browser.sh     # after changing CHROMIUM_REVISION

   For a jar under `lib/`, replace it with the new version and update the
   filename reference in `build.sh` if the name changed.

4. Build and run the tests, then smoke-test a crawl and a scan:

       ./build.sh && ./test.sh

5. Record the bump in the release notes, and keep the licence attribution in
   `CREDITS.md` current if a tool is added or removed.

## Why pin rather than always-latest

A pinned version means two developers, and CI, build the same product, and a
crawler or browser update that changes behaviour lands deliberately in a release
rather than silently between runs. The update check keeps the pins from going
stale without taking the choice of when to move away from you.
