package opentrap.proxy;

import opentrap.core.SuiteContext;
import opentrap.ui.OptionsPage;
import opentrap.ui.Ui;

import javax.swing.*;

/** Settings for the bundled testing browser, shown under Settings > Browser. */
public class BrowserSettingsPanel extends OptionsPage {

    public BrowserSettingsPanel(SuiteContext ctx) {
        boolean relaxNow = ctx.settings().getBool(BrowserFlags.RELAX_KEY, true);
        JCheckBox relax = Ui.check("relax browser security while testing", relaxNow);
        relax.addActionListener(e ->
                ctx.settings().setBool(BrowserFlags.RELAX_KEY, relax.isSelected()));
        JTextArea relaxNote = Ui.note(
            "Applies to the bundled browser and the crawler, both of which run in their own "
            + "isolated profile and never touch your ordinary browsing. Allows cross-origin "
            + "reads, mixed content, private network requests and pop-ups, so that requests "
            + "the browser would otherwise refuse to send still reach the proxy and get "
            + "scanned. This widens how much of the application is reached. It does not change "
            + "what the scanner reports about a page it has already seen, because every "
            + "passive check reads the HTTP exchange at the proxy rather than relying on the "
            + "browser to enforce anything. Takes effect the next time the browser is "
            + "launched.");
        section("Testing browser", relax, relaxNote);

        JTextArea where = Ui.note(
            "The browser is launched from the globe in the top bar or from Open browser on "
            + "the Proxy tab. It uses the bundled Chromium when present, otherwise a Chromium "
            + "found on this machine, with its own profile under ~/.opentrap/browser and the "
            + "proxy's certificate already trusted.");
        section("Launching", where);
    }
}
