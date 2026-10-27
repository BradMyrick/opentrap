# Contributors

OpenTRAP is built by its community. Everyone whose change has landed is listed
here. If your change was merged and you are not yet on this list, say so and you
will be added.

## Maintainers

- scottsturrock - project maintainer; reviews and merges contributions, maintains releases.

## Contributors

<!-- Add new contributors below, one per line: Name (or handle) - what they worked on. -->

- conformance - proxy listens on 8080 by default and shows its port on the intercept and options tabs; options pages open at the top; tables fill their panes; build scripts no longer pack stale output; documented the fork and pull request workflow
- exploit-development - the modern themed interface and the overall UI refresh
- codesec-ops - the project startup and welcome screen; the guided new scan wizard; live passive scanning of proxy traffic; the request and response inspector in the message editor; WebSocket capture; the organizer, sequencer and decoder work; the refreshed screenshots and project site; the tiered scan configurations with a Custom settings screen wired to real engine options; task controls on the dashboard; responsive scan handling that stops cleanly and keeps memory bounded; the response render view; the interface parity pass with a single-row tab strip, top-bar tools and a Settings dialog; the Discover tab with the project roadmap; the not-yet-implemented contribution dialogs; the Extensions tab; wider scan coverage with the beta rule packs; and uniform form alignment across the tools
- BradMyrick - fixed blank windows on non-reparenting compositors (sway, i3, Hyprland, river, dwl, bspwm, labwc) by setting the Java window-manager hint at launch; fixed the crash when a response held a long JSON string, by colouring JSON with a linear scan and containing any colouring failure, with A/B tests that prove it; removed the duplicated issue evidence and unmodified-request copies that inflated project memory and files, and fixed a latent stack overflow scanning HTML titles, with measured before and after numbers and tests
- lunar-me - corrected the audit-tool sentence in the maintainer guide

## Issue reporters

A clear bug report is a contribution: these people found and reported problems that were then fixed.

- PotatoGamo - reported the poor contrast of the dark theme (#26), which led to the dark theme being rebuilt
