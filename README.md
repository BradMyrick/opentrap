<div align="center">

<img src="icon/opentrap.svg" width="84" alt="OpenTRAP logo">

<h1>OpenTRAP</h1>

<p><strong>Open Traffic Replay and Analysis Proxy</strong></p>

<p>An open-source web security testing suite built around an intercepting proxy. A
familiar workflow for finding and proving web vulnerabilities, with a trustworthy
detection engine underneath, and nothing locked behind a licence.</p>

<p>
<img src="https://img.shields.io/badge/licence-AGPL--3.0-e5484d" alt="Licence">
<img src="https://img.shields.io/badge/Java-17%2B-5b8def" alt="Java">
<img src="https://img.shields.io/badge/platform-Linux-9aa7b4" alt="Platform">
<img src="https://img.shields.io/badge/status-active%20development-3fb950" alt="Status">
<img src="https://img.shields.io/badge/contributions-welcome-ff6a5f" alt="Contributions welcome">
</p>

<p>
<a href="https://github.com/opentrap/opentrap"><img src="https://img.shields.io/badge/Star%20on%20GitHub-e5484d?style=for-the-badge&logo=github&logoColor=white" alt="Star on GitHub"></a>
<a href="https://twitter.com/intent/tweet?text=OpenTRAP%2C%20an%20open-source%20web%20security%20testing%20suite&url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap"><img src="https://img.shields.io/badge/Share-X-000000?style=for-the-badge&logo=x&logoColor=white" alt="Share on X"></a>
<a href="https://www.linkedin.com/sharing/share-offsite/?url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap"><img src="https://img.shields.io/badge/Share-LinkedIn-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white" alt="Share on LinkedIn"></a>
<a href="https://www.reddit.com/submit?url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap&title=OpenTRAP"><img src="https://img.shields.io/badge/Share-Reddit-FF4500?style=for-the-badge&logo=reddit&logoColor=white" alt="Share on Reddit"></a>
<a href="https://news.ycombinator.com/submitlink?u=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap&t=OpenTRAP"><img src="https://img.shields.io/badge/Share-Hacker%20News-FF6600?style=for-the-badge&logo=ycombinator&logoColor=white" alt="Share on Hacker News"></a>
</p>

</div>

---

## Why we built it

The most capable intercepting proxies are commercial and closed. The leading open
alternative is powerful, but its interface is unfamiliar to many of the people who
would benefit from it most. We wanted something different: a web security testing
suite that is genuinely **open**, genuinely useful day to day, and immediately
**familiar** to anyone who has worked with an intercepting proxy before.

OpenTRAP is that tool. A complete testing workspace with a clean, recognisable
workflow, that any practitioner can run, inspect, and extend, with no licence gate
on its core capabilities. It is built to be improved by the people who use it
rather than steered by a single vendor's roadmap. If a security tester can describe
a better way to work, OpenTRAP should be the tool that lets them build it.

## What it is

A proxy-first workspace for web application testing. Capture, inspect, replay and
tamper with the traffic between a browser and a web application. Map an
application's attack surface, and drive repeatable manual and automated testing
from one window: interception, HTTP history, a target site map and scope, a request
toolset, an out-of-band collaborator, and a complete log of everything that runs.

## Help build OpenTRAP

**OpenTRAP is a community project, and this is an open call.** We want this to grow
into something the whole web security community owns and improves. If you can fix a
bug, patch a rough edge, sharpen the UI, add a tool, or bring anything useful at
all, we want your contribution. You do not need permission to improve it: if you
think you can make it better, you probably can.

- Every change is reviewed, merged, and **fully credited** to its author.
- One-line fixes are as welcome as whole new tools.
- Bug reports and concrete ideas move the project forward too.

The fastest way to help it grow is to **star the repository and share it** (buttons
at the top), and to send that first change. See [CONTRIBUTING.md](CONTRIBUTING.md)
to get started, then open an issue or a pull request.

## Scanning and detection

Point OpenTRAP at a target and it crawls the application, maps what it finds, and
audits it for real vulnerabilities: injection, cross-site scripting, broken access
control, information disclosure, misconfiguration and more. Findings land in a
clear issue view with the request, response and the matched evidence highlighted,
ready to triage or hand straight to a developer. The detection is built on a
proven, industry-grade engine, so what OpenTRAP reports holds up.

## Highlights

- **Proxy-first by design** - intercept, replay and edit live traffic, with the whole toolset one click away.
- **Detection that stands up** - crawl and audit with a proven, industry-grade engine, so the findings you report carry weight.
- **Everything in one window** - repeater, intruder, sequencer, decoder, comparer, collaborator and a full logger.
- **One self-contained file** - a single Linux jar with the scan engine built in; download it and run it.
- **Open and hackable** - AGPL v3.0: read it, change it, extend it, with no gate on its core capabilities.
- **Built to be improved** - shaped by the people who use it, with every contribution credited.

## The toolset

| Tool | What it does |
|------|--------------|
| **Dashboard** | Live tasks, issues and event log; start a scan |
| **Target** | Site map tree and the suite-wide scope that governs every tool |
| **Proxy** | Intercept, HTTP history, per-host certificate authority |
| **Passive** | Configure the scan engine's rules (enable, disable, threshold) |
| **Logger** | A complete record of every request OpenTRAP and the engine make |
| **Intruder** | Four attack types with payload sets and processing |
| **Repeater** | Edit and reissue a request, one numbered tab at a time |
| **Collaborator** | Out-of-band interaction capture for blind vulnerabilities |
| **Sequencer** | Token randomness analysis with entropy tests |
| **Decoder / Comparer** | Chained encode/decode/hash transforms, and item diffs |

Right-click any request or response to send it between tools.

## Get started

Download the latest release and run it. Nothing to install.

**Recommended: the AppImage.** One self-contained file that bundles its own Java
runtime and the scan engine, no Java needed, just make it executable and run it
(or double-click it in your file manager):

```
chmod +x OpenTRAP-x86_64.AppImage
./OpenTRAP-x86_64.AppImage
```

**Or the jar**, if you already have **Java 17 or newer**:

```
java -jar opentrap_<timestamp>.jar
```

Either way the download is self-contained: the scan engine is built in and sets
itself up on first use, and the interface detects your display and scales itself.
The optional testing browser is an in-app download, offered only when you want it,
so the release stays small.

The fastest way to test a target is the built-in browser: on the **Proxy** tab
click **open browser**. It launches a bundled Chromium in an isolated profile,
already routed through the proxy and already trusting OpenTRAP's certificate
authority, so there is nothing to configure and no certificate to install.

## Build from source

```
./build.sh      # compile src/ into a lean dev jar
./test.sh       # run the test suite
./release.sh    # build the self-contained, timestamped release jar
```

The only compiled-in dependencies are BouncyCastle (the proxy CA) and a Brotli
decoder. The scan engine and testing browser are fetched on demand by
`./fetch-zap.sh` and `./fetch-browser.sh`; bundled-tool versions are pinned in
`tools/versions`. See `DEPENDENCIES.md`.

## Licence

OpenTRAP is released under the **GNU Affero General Public License v3.0**. You are
free to use, study, modify and share it; if you distribute a modified version or
run one as a network service, you release your changes under the same licence, so
improvements stay open for everyone. The full text is in [LICENSE](LICENSE).

---

<sub>OpenTRAP is independent open-source software. It bundles third-party
open-source components under their respective licences; see
[CREDITS.md](CREDITS.md).</sub>
