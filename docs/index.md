---
layout: default
title: OpenTRAP
---

# OpenTRAP

**Open Traffic Replay and Analysis Proxy** - an open-source, community-driven web
security testing suite built around an intercepting HTTP proxy.

Capture, inspect, replay and tamper with the traffic between a browser and a web
application, map an application's attack surface, crawl and audit it for real
vulnerabilities, and drive repeatable manual and automated testing from one
workspace, all with a familiar, recognisable workflow.

## Why OpenTRAP

- **A proxy-first testing suite.** Intercept, HTTP history, repeater, intruder,
  sequencer, decoder, comparer, an out-of-band collaborator, and a complete
  traffic logger, all in one window.
- **Scanning that holds up.** Crawl and audit for real vulnerabilities, with
  findings in a clear issue view and the evidence highlighted, built on a proven,
  industry-grade engine.
- **One self-contained file.** The release is a single Linux jar with the scan
  engine built in. Download it, run it, scan, there is nothing else to install.
- **Looks right on any screen.** The interface detects your display and scales
  itself, crisp on a 4K panel, normal on 1080p.
- **Genuinely open.** Licensed under the GNU AGPL v3.0, so it stays open and
  hackable, with no licence gate on its core capabilities.

## Get started

Download the latest release and run it. Nothing to install.

**Recommended: the AppImage** - one self-contained file, no Java needed:

```
chmod +x opentrap-x86_64.appimage
./opentrap-x86_64.appimage
```

Or double-click it in your file manager.

**Or the jar**, if you already have Java 17 or newer:

```
java -jar opentrap_<timestamp>.jar
```

The scan engine is built in and sets itself up on first run; the optional testing
browser is an in-app download when you want it.

## Learn more

- [Source, README and documentation](https://github.com/opentrap/opentrap)
- [How to contribute](https://github.com/opentrap/opentrap/blob/main/CONTRIBUTING.md)
- [Licence: GNU AGPL v3.0](https://www.gnu.org/licenses/agpl-3.0.html)

*OpenTRAP is independent open-source software, released under the GNU AGPL v3.0.
See the repository for full credits and component licences.*
