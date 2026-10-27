# Security policy

OpenTRAP is a security testing tool, and it holds a position of trust on the
machines it runs on: it installs a per-user certificate authority for TLS
interception, records the full traffic that passes through it, and drives an
embedded scan engine. We take bugs in that surface seriously, and we would
much rather hear about them from you than learn of them later.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting on this repository:
**Security > Report a vulnerability**. That reaches the maintainers directly
and keeps the report private until a fix is out.

If you cannot use that channel, open a regular issue that contains no details
and say how we can reach you; we will follow up privately.

Please include, as far as you can:

- The commit or release you tested.
- Steps to reproduce, or a proof of concept.
- What an attacker could gain, and any conditions required.
- Whether the issue is in OpenTRAP itself, in the pinned OWASP ZAP engine, or
  in the bundled Chromium build.

## Scope

In scope: OpenTRAP's own code and the build and packaging scripts in this
repository. That includes the certificate authority and keystore handling, the
proxy and its parsers, project file handling, and the scripts that fetch the
engine, browser and JDK.

Out of scope: vulnerabilities in the applications you test through OpenTRAP,
in the ZAP engine itself (report those to the ZAP team), or in Chromium builds
(report those upstream), and issues that require an attacker to already have
control of the machine OpenTRAP runs on.

## Handling

- We will acknowledge reports, normally within a few days.
- Fixes for confirmed issues ship in the next release when that is soon, or in
  a dedicated release when it is not.
- Credit in the release notes is given by default; tell us if you prefer to
  stay anonymous.

## Safe research

Testing OpenTRAP against software you own or are authorised to test is fine.
Do not test it against third-party systems, and do not run a live intercepting
proxy against traffic you are not authorised to intercept.

## A note on the certificate authority

OpenTRAP's CA lives in `~/.opentrap/ca/` and is generated locally on first
use; it is never transmitted anywhere. Anyone who can read that file can
intercept the machine's proxied TLS traffic, so treat a copy of it as a
credential. If you find a way the CA is generated weakly, stored too openly,
or trusted beyond the proxy, that is exactly the kind of report we want.
