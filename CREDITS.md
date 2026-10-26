# Icon credits

The OpenTRAP application icon (`icon/opentrap.svg` and the rasterised copies
under `src/opentrap/ui/icons/`) is based on the **knemo-monitor** icon from the
**Flat Remix** icon theme.

- Author: Daniel Ruiz de Alegria
- Source: https://github.com/daniruiz/flat-remix
- Licence: GNU General Public License v3

The only modification is pinning the fill colour to the theme's orange so the icon
stays visible on any desktop colour scheme instead of being recoloured by it.

Flat Remix is GPL-3.0, which is compatible with OpenTRAP's AGPL-3.0 licence. In
line with the GPL, this credit and the licence are retained. The full GPL-3.0
text is at https://www.gnu.org/licenses/gpl-3.0.html and is also shipped with
most systems under `/usr/share/common-licenses/GPL-3`.

# Bundled tools

OpenTRAP drives these external tools, fetched on demand into the product rather
than packed into the jar:

- **OWASP ZAP** (Zed Attack Proxy), by the ZAP dev team / Checkmarx, licensed
  Apache 2.0. Source: https://github.com/zaproxy/zaproxy. OpenTRAP runs ZAP with
  no user interface, as a headless scan engine on the loopback: its crawler
  (spider) and active-scan rules power OpenTRAP's crawl and audit, and its
  findings are mapped into OpenTRAP's own issue model and shown in OpenTRAP's UI.
  Nothing of ZAP's interface is shown to the user. The engine is fetched by
  `fetch-zap.sh`. Apache 2.0 is compatible with OpenTRAP's AGPL-3.0 licence.
- **Chromium**, the embedded testing browser, BSD-licensed open-source build,
  fetched by `fetch-browser.sh`.

# Organisation logos

The logos in the "Supporting software freedom" section are each organisation's
own mark, shown to credit and link to them. Trademarks remain the property of
their respective owners.

- **Electronic Frontier Foundation**, **Software Freedom Conservancy** and the
  **Free Software Foundation** logos are each organisation's own, from their
  websites.
- The **Open Source Initiative** keyhole is from Wikimedia Commons, licensed
  CC BY 2.5.
- The **Free Software Foundation Europe** logo is from Wikimedia Commons, by
  FSFE, licensed CC BY-SA 3.0.

# Contributors

OpenTRAP is built by its community, and every contribution is credited. The
people who have shaped it so far:

- **scottsturrock** - project maintainer
- **conformance** - proxy defaults and the fork and pull request workflow
- **exploit-development** - the modern themed interface and UI refresh
- **codesec-ops** - the project startup screen, new scan wizard, live passive
  scanning, message-editor inspector, WebSocket capture, and the project site
- **BradMyrick** - the launch fix for non-reparenting compositors (sway, i3,
  Hyprland and others)
- **PotatoGamo** - reported the dark theme contrast problem that led to its
  rebuild

The full list, with what each person worked on and everyone who has reported
a problem, is in
[CONTRIBUTORS.md](CONTRIBUTORS.md).
