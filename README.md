<div align="center">

<img src="icon/opentrap.svg" width="84" alt="OpenTRAP logo">

<h1>OpenTRAP</h1>

<p><strong>Open Traffic Replay and Analysis Proxy</strong></p>

<p>A free, open-source alternative to Burp Suite: the intercepting-proxy workflow you
already know, on top of the trusted OWASP ZAP detection engine, with nothing locked
behind a licence. If you have used Burp Suite, you will feel at home in minutes.</p>

<p>
<img src="https://img.shields.io/badge/licence-AGPL--3.0-e5484d" alt="Licence">
<img src="https://img.shields.io/badge/Java-17%2B-5b8def" alt="Java">
<img src="https://img.shields.io/badge/platform-Linux-9aa7b4" alt="Platform">
<img src="https://img.shields.io/badge/status-active%20development-3fb950" alt="Status">
<img src="https://img.shields.io/badge/contributions-welcome-ff6a5f" alt="Contributions welcome">
</p>

<p>
<a href="https://github.com/opentrap/opentrap"><img src="https://img.shields.io/github/stars/opentrap/opentrap?style=for-the-badge&logo=github&logoColor=white&label=Star%20on%20GitHub&color=e5484d" alt="Star on GitHub"></a>
<a href="https://twitter.com/intent/tweet?text=OpenTRAP%2C%20an%20open-source%20web%20security%20testing%20suite&url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap"><img src="https://img.shields.io/badge/Share-X-000000?style=for-the-badge&logo=x&logoColor=white" alt="Share on X"></a>
<a href="https://www.linkedin.com/sharing/share-offsite/?url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap"><img src="https://img.shields.io/badge/Share-LinkedIn-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white" alt="Share on LinkedIn"></a>
<a href="https://www.reddit.com/submit?url=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap&title=OpenTRAP"><img src="https://img.shields.io/badge/Share-Reddit-FF4500?style=for-the-badge&logo=reddit&logoColor=white" alt="Share on Reddit"></a>
<a href="https://news.ycombinator.com/submitlink?u=https%3A%2F%2Fgithub.com%2Fopentrap%2Fopentrap&t=OpenTRAP"><img src="https://img.shields.io/badge/Share-Hacker%20News-FF6600?style=for-the-badge&logo=ycombinator&logoColor=white" alt="Share on Hacker News"></a>
</p>

<p>
<a href="https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage"><img src="https://img.shields.io/badge/Download-AppImage%20for%20Linux-3fb950?style=for-the-badge&logo=linux&logoColor=white" alt="Download the latest AppImage for Linux"></a>
<a href="https://github.com/opentrap/opentrap/releases/latest"><img src="https://img.shields.io/badge/All%20releases-9aa7b4?style=for-the-badge&logo=github&logoColor=white" alt="All releases"></a>
</p>

<br>

<a href="docs/images/dashboard.png"><img src="docs/images/dashboard.png" width="100%" alt="OpenTRAP dashboard with a crawl finished, an active audit running, and the live table of the most serious vulnerabilities found"></a>

<p><sub>A live audit on the Dashboard: crawl complete, active scan running, high-severity findings arriving as they are confirmed.</sub></p>

</div>

---

## Why we built it

The most capable intercepting proxies, such as Burp Suite, are commercial and
closed, with their most useful features behind a paid licence. OWASP ZAP is a
powerful open engine, but its interface is unfamiliar to the many testers who work
proxy-first. OpenTRAP brings the two together: the familiar Burp-style workflow on
top of the trusted OWASP ZAP detection engine, genuinely **open**, genuinely useful
day to day, and immediately **familiar** to anyone who has worked with an
intercepting proxy before.

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

It is built for penetration testers, bug bounty hunters, AppSec engineers and
students who want a professional intercepting-proxy workflow without a commercial
licence.

## See it in action

<table>
<tr>
<td width="50%" valign="top">
<a href="docs/images/target.png"><img src="docs/images/target.png" alt="Target tab: site map tree, request list, and the issues found with a full advisory for a reflected cross-site scripting finding"></a>
<br><sub><b>Target</b><br>Site map, scope and every issue found, with the advisory, request and response beside it.</sub>
</td>
<td width="50%" valign="top">
<a href="docs/images/proxy.png"><img src="docs/images/proxy.png" alt="Proxy tab with interception, and the built-in Chromium browser opened from it, routed through the proxy and showing the certificate authority page"></a>
<br><sub><b>Proxy and built-in browser</b><br>One click opens Chromium already routed through the proxy and trusting its certificate authority, so TLS interception works with nothing to configure.</sub>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<a href="docs/images/collaborator.png"><img src="docs/images/collaborator.png" alt="Collaborator tab listing captured out-of-band DNS and HTTP interactions with the request to the collaborator shown"></a>
<br><sub><b>Collaborator</b><br>Out-of-band DNS and HTTP interactions captured for blind vulnerabilities, with every callback request on record.</sub>
</td>
<td width="50%" valign="top">
<a href="docs/images/intruder.png"><img src="docs/images/intruder.png" alt="Intruder tab running a sniper attack, with the live attack table of payloads, status, length and timing"></a>
<br><sub><b>Intruder</b><br>Four attack types with payload sets and processing; the live attack table shows status, length and timing per request.</sub>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<a href="docs/images/repeater.png"><img src="docs/images/repeater.png" alt="Repeater tab with a request and its response in numbered tabs for editing and reissuing"></a>
<br><sub><b>Repeater</b><br>Edit and reissue a request, one numbered tab at a time, with the full response beside it.</sub>
</td>
<td width="50%" valign="top">
<a href="docs/images/logger.png"><img src="docs/images/logger.png" alt="Logger tab with a filterable table of every request made by the audit, with request and response viewers below"></a>
<br><sub><b>Logger</b><br>A complete, filterable record of every request OpenTRAP and the scan engine make.</sub>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<a href="docs/images/decoder.png"><img src="docs/images/decoder.png" alt="Decoder tab with a URL-encoded path-traversal string decoded to plain text through a transform pipeline"></a>
<br><sub><b>Decoder</b><br>Chain URL, Base64, hex and hash transforms on any value, or let smart decode work it out for you.</sub>
</td>
<td width="50%" valign="top">
<a href="docs/images/about.png"><img src="docs/images/about.png" alt="About OpenTRAP dialog showing the build, the scan engine in use and where it comes from, the runtime, the licence and project links"></a>
<br><sub><b>About</b><br>The build, the scan engine in use and where it comes from, the runtime and the licence, stated plainly.</sub>
</td>
</tr>
</table>

Full-size screenshots of every tool are on the
[project site](https://opentrap.github.io/opentrap/#see-it-in-action).

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
ready to triage or hand straight to a developer. The detection is built on the
proven OWASP ZAP engine, so what OpenTRAP reports holds up.

## Highlights

- **Proxy-first by design** - intercept, replay and edit live traffic, with the whole toolset one click away.
- **Detection that stands up** - crawl and audit with the proven OWASP ZAP engine, so the findings you report carry weight.
- **Everything in one window** - repeater, intruder, sequencer, decoder, comparer, collaborator and a full logger.
- **One self-contained file** - every release is a single Linux AppImage with its own runtime and the scan engine built in; download it and run it.
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

Download the latest release from the [releases page](https://github.com/opentrap/opentrap/releases/latest) and run it. Nothing to install.

**One file: the AppImage.** It bundles its own Java runtime and the scan engine,
so there is no Java to install. This link always points at the latest release: [opentrap-x86_64.appimage](https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage)

Or from a terminal, fetch it:

```
wget https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage
```

make it executable:

```
chmod +x opentrap-x86_64.appimage
```

and run it:

```
./opentrap-x86_64.appimage
```

You can also double-click the downloaded file in your file manager.

The download is complete: the Java runtime and the scan engine are inside the
file and set themselves up on first use. Every release is this one AppImage, so
nothing ships with parts missing.
The optional testing browser is an in-app download, offered only when you want it,
so the release stays small.

The fastest way to test a target is the built-in browser: on the **Proxy** tab
click **open browser**. It launches a bundled Chromium in an isolated profile,
already routed through the proxy and already trusting OpenTRAP's certificate
authority, so there is nothing to configure and no certificate to install.

## Build from source

```
./build.sh      # compile src/ into a lean dev jar (uses an engine already on disk)
./test.sh       # run the test suite
./release.sh    # build the self-contained release jar the AppImage is made from
./package-appimage.sh   # build the AppImage, the only artefact that is released
```

The only compiled-in dependencies are BouncyCastle (the proxy CA) and a Brotli
decoder. The scan engine and testing browser are fetched on demand by
`./fetch-zap.sh` and `./fetch-browser.sh`; bundled-tool versions are pinned in
`tools/versions`. See `DEPENDENCIES.md`.

## FAQ

**Is there a free, open-source alternative to Burp Suite?**
Yes. OpenTRAP gives you the intercepting-proxy workflow Burp users know, with Proxy,
Repeater, Intruder, Collaborator, Sequencer, Decoder and Comparer, under the AGPL-3.0
with no paid tier and no feature gate.

**What is the difference between OpenTRAP and OWASP ZAP?**
OpenTRAP uses the OWASP ZAP engine for crawling and scanning, and puts a familiar,
Burp-style interface and workflow on top of it. If you trust ZAP's detection but want
a proxy-first workspace, OpenTRAP is that front end.

**Does it have Repeater, Intruder and a Collaborator?**
Yes, along with HTTP history, a target site map and scope, a sequencer, a decoder, a
comparer and a full traffic logger, all in one window.

**Which platforms does it run on?**
Today OpenTRAP ships as a single self-contained Linux AppImage. It needs nothing
installed: the Java runtime and the scan engine are inside the file.

**Is it really free?**
Yes. OpenTRAP is released under the GNU AGPL v3.0. Every capability is open, with
nothing reserved for a paid edition.

## Licence

OpenTRAP is released under the **GNU Affero General Public License v3.0**. You are
free to use, study, modify and share it; if you distribute a modified version or
run one as a network service, you release your changes under the same licence, so
improvements stay open for everyone. The full text is in [LICENSE](LICENSE).

---

<sub>OpenTRAP is independent open-source software. It bundles third-party
open-source components under their respective licences; see
[CREDITS.md](CREDITS.md).</sub>
