package opentrap.intruder;

import opentrap.core.HttpService;
import opentrap.ui.OptionsPage;
import opentrap.ui.Ui;

import javax.swing.*;

/** The target tab: host, port and whether to use TLS. */
public class TargetPanel extends OptionsPage {

    private final JTextField host = Ui.field("", 24);
    private final JTextField port = Ui.field("80", 6);
    private final JCheckBox ssl = Ui.check("use SSL", false);

    public TargetPanel() {
        ssl.addActionListener(e -> {
            String p = port.getText().trim();
            if (ssl.isSelected() && p.equals("80")) port.setText("443");
            else if (!ssl.isSelected() && p.equals("443")) port.setText("80");
        });

        section("attack target",
                Ui.note("Configure the host and port of the server to attack. These values "
                        + "are filled in automatically when a request is sent to Intruder."),
                OptionsPage.fieldRow("host:", host, ""),
                OptionsPage.fieldRow("port:", port, ""),
                ssl);
    }

    public void set(HttpService svc) {
        if (svc == null) return;
        host.setText(svc.host());
        port.setText(String.valueOf(svc.port()));
        ssl.setSelected(svc.secure());
    }

    public HttpService service() {
        int p;
        try { p = Integer.parseInt(port.getText().trim()); }
        catch (Exception e) { p = ssl.isSelected() ? 443 : 80; }
        return new HttpService(host.getText().trim(), p, ssl.isSelected());
    }
}
