package opentrap.intruder;

import opentrap.core.HttpService;
import opentrap.ui.Ui;

import javax.swing.*;
import java.awt.*;

/**
 * The attack target as a single row under the attack type, as in the reference:
 * host, port and whether to use TLS. The values are filled in automatically when
 * a request is sent to Intruder.
 */
public class TargetPanel extends JPanel {

    private final JTextField host = Ui.field("", 28);
    private final JTextField port = Ui.field("80", 5);
    private final JCheckBox ssl = Ui.check("use SSL", false);

    public TargetPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 2));
        setOpaque(false);
        ssl.addActionListener(e -> {
            String p = port.getText().trim();
            if (ssl.isSelected() && p.equals("80")) port.setText("443");
            else if (!ssl.isSelected() && p.equals("443")) port.setText("80");
        });
        host.setToolTipText("host of the server to attack");
        port.setToolTipText("port of the server to attack");
        add(Ui.bold("Target"));
        add(host);
        add(Ui.label("port"));
        add(port);
        add(ssl);
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
