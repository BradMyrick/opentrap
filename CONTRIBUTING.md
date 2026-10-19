# Contributing to OpenTRAP

Thanks for considering a contribution. OpenTRAP is a community project and changes
of any size are welcome, from a one-line fix to a whole tool.

## How to contribute a change

1. Make your change against a clean checkout and keep it focused on one thing.
2. Build and run it (`./build.sh` then `./run.sh`) and confirm the app still
   launches and your change behaves as intended.
3. Send the patch, or a description of the change with the diff. It will be
   reviewed, merged, and you will be credited in `CONTRIBUTORS.md`.

Keep each change self-contained. Match the style of the surrounding code: the same
naming, comment density and idioms. Do not reformat unrelated code.

## Building

    ./build.sh      # compiles src/ and repacks dist/opentrap.jar
    ./run.sh        # launches the app
    ./test.sh       # compiles and runs the test suite

Tests live under `tests/` rather than `src/`, so they are never packed into the
shipped jar. Any change with logic to it should come with test cases.

Requirements: Java 17 or newer (built and tested on Java 25). BouncyCastle and a
Brotli decoder are vendored under `lib/`; the ZAP scan engine and the Chromium
browser are fetched on demand by `./fetch-zap.sh` and `./fetch-browser.sh`. See
`DEPENDENCIES.md` for how
bundled tools are pinned and updated across releases, and run
`./tools/check-updates.sh` to see what has a newer version.

## How the code is organised

The source lives under `src/opentrap/`:

- `core` - shared services: the suite context, message store, scope, scan tasks,
  issues, HTTP primitives.
- `engine` - drives the bundled OWASP ZAP scan engine, run headless: starting it,
  running crawl-and-audit, mapping ZAP's findings into OpenTRAP issues and its
  traffic into the logger, and configuring ZAP's rules. All detection is ZAP's;
  OpenTRAP keeps none of its own.
- `ui` - the main window, menus, shared Swing helpers, HiDPI scaling, bootstrap.
- `proxy` - the intercepting proxy, the per-host certificate authority, and the
  embedded browser launcher.
- one package per tool: `target`, `proxy`, `scanner`, `intruder`, `repeater`,
  `collaborator`, `sequencer`, `decoder`, `comparer`, `logger`, `dashboard`.

`CONTRACT.md` is the full API reference for the tool contract, `SuiteContext`,
`HttpMessage` and the shared UI helpers.

## Architecture at a glance

OpenTRAP is a shell that hosts independent tools around one shared context.

- **`SuiteContext` is the hub.** It holds the state every tool reads and writes:
  the message store (all captured traffic), the target scope, settings, the issue
  list, scan tasks, and the collaborator. Tools talk through it, not to each other.
- **Each tool is a `ToolModule` that contributes a tab.** The shell calls
  `init(ctx)` once, then `tabs()` to build its UI; tools are listed in
  `Bootstrap.registerTools` in tab order.
- **Traffic flows one way.** The proxy captures an exchange into `ctx.store()`;
  the store notifies listeners; the site map, logger and dashboard update from it.
- **Scanning is delegated to the engine** (`opentrap.engine`), which crawls and
  audits and returns findings mapped into `ctx`'s issue list.
- **`Ui` holds the shared Swing helpers and look and feel**, so every tool matches.

To understand any tool, read its `*Tool.java`: that one file is the whole of it.

## Add a tool in a few minutes

A tool is one class implementing `opentrap.core.ToolModule`, plus one line in
`Bootstrap`. This is a complete, working tool:

```java
package opentrap.hello;

import opentrap.core.SuiteContext;
import opentrap.core.ToolModule;
import opentrap.ui.Ui;
import javax.swing.*;

public class HelloTool implements ToolModule {
    @Override public String name() { return "hello"; }    // the tab caption
    @Override public void init(SuiteContext ctx) { }       // grab shared state here
    @Override public Tab[] tabs() {
        JPanel p = new JPanel();
        p.add(Ui.label("Hello from a new tool."));
        return new Tab[]{ new Tab("hello", p) };
    }
}
```

Register it in `src/opentrap/ui/Bootstrap.java`:

```java
frame.register(new opentrap.hello.HelloTool());
```

Then `./build.sh && ./run.sh`, and a "hello" tab appears. That is the whole
extension model. To receive requests from other tools' "send to" menus, call
`ctx.registerSink("hello", ...)` in `init`; everything else shared (traffic,
scope, issues) is on `SuiteContext`.

## Good first areas

- **Deeper ZAP engine integration.** Surface more of the engine through the UI:
  the AJAX spider for single-page apps, active-rule and scan-policy configuration,
  and persisting rule settings across runs.
- Protocol support (WebSocket, HTTP/2).
- Payload libraries and processors for intruder.
- UI polish and accessibility.

## House style

- Clear, self-documenting code over cleverness.
- Describe behaviour on its own terms in comments and docs.
- Do not use em dashes or en dashes anywhere in the project; use a plain hyphen.
- Keep the interface aligned and uniform. Labelled inputs line up through `Ui.form(...)`, filter strips through `Ui.filterBar(...)`, and matching controls share one height. A screen where fields or rows do not line up is a bug to fix, not to ship.
