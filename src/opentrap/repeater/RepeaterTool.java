package opentrap.repeater;

import opentrap.core.*;
import opentrap.ui.*;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Manual request editing and reissue, one numbered tab per request. */
public class RepeaterTool implements ToolModule {

    private SuiteContext ctx;
    private final JTabbedPane tabs = new JTabbedPane();
    private final AtomicInteger counter = new AtomicInteger(0);
    private boolean followRedirects = false;
    private boolean updateContentLength = true;

    public RepeaterTool() {}

    @Override public String name() { return "repeater"; }

    @Override public void init(SuiteContext ctx) {
        this.ctx = ctx;
        ctx.registerSink("repeater", (svc, req, resp, cap) ->
            SwingUtilities.invokeLater(() -> newTab(svc, req, cap)));
    }

    @Override public Tab[] tabs() {
        tabs.setFont(Ui.PLAIN);
        if (tabs.getTabCount() == 0) newTab(new HttpService("127.0.0.1", 80, false),
                new HttpMessage(Bytes.bytes("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")), null);
        return new Tab[]{ new Tab("repeater", tabs) };
    }

    @Override public boolean alwaysTabbed() { return false; }

    @Override public JMenu[] menus() {
        JMenu menu = new JMenu("repeater");
        JMenuItem nt = new JMenuItem("new tab");
        nt.addActionListener(e -> newTab(new HttpService("127.0.0.1", 80, false),
                new HttpMessage(Bytes.bytes("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")), null));
        JMenuItem dt = new JMenuItem("delete tab");
        dt.addActionListener(e -> { if (tabs.getTabCount() > 1) tabs.remove(tabs.getSelectedIndex()); });
        JMenuItem rt = new JMenuItem("rename tab");
        rt.addActionListener(e -> {
            int i = tabs.getSelectedIndex();
            if (i < 0) return;
            String name = JOptionPane.showInputDialog(ctx.frame(), "enter the new tab name", tabs.getTitleAt(i));
            if (name != null && !name.isBlank()) tabs.setTitleAt(i, name);
        });
        JCheckBoxMenuItem ucl = new JCheckBoxMenuItem("update Content-Length", updateContentLength);
        ucl.addActionListener(e -> updateContentLength = ucl.isSelected());
        JCheckBoxMenuItem fr = new JCheckBoxMenuItem("follow redirects", followRedirects);
        fr.addActionListener(e -> followRedirects = fr.isSelected());
        for (JMenuItem m : new JMenuItem[]{nt, dt, rt}) m.setFont(Ui.PLAIN);
        ucl.setFont(Ui.PLAIN); fr.setFont(Ui.PLAIN);
        menu.add(nt); menu.add(dt); menu.add(rt);
        menu.addSeparator();
        menu.add(ucl); menu.add(fr);
        return new JMenu[]{ menu };
    }

    private void newTab(HttpService svc, HttpMessage req, String caption) {
        if (svc == null) svc = new HttpService("127.0.0.1", 80, false);
        if (req == null) req = new HttpMessage(Bytes.bytes("GET / HTTP/1.1\r\nHost: " + svc.host() + "\r\n\r\n"));
        RepeaterTab tab = new RepeaterTab(svc, req);
        String title = caption != null && !caption.isBlank() ? caption : String.valueOf(counter.incrementAndGet());
        tabs.addTab(title, tab);
        tabs.setSelectedComponent(tab);
    }

    private class RepeaterTab extends JPanel {
        private final MessageEditor reqEditor;
        private final MessageEditor respEditor;
        private final JTextField hostField;
        private final JTextField portField;
        private final JCheckBox sslBox;
        private final JLabel status = Ui.label("ready");
        private final JLabel lengthLabel = Ui.label("length: 0");
        private final JButton go = Ui.button("go");
        private final JButton cancel = Ui.button("cancel");
        private volatile Thread worker;

        RepeaterTab(HttpService svc, HttpMessage req) {
            super(new BorderLayout());
            reqEditor = new MessageEditor(ctx, MessageEditor.REQUEST, true);
            respEditor = new MessageEditor(ctx, MessageEditor.RESPONSE, false);
            reqEditor.setMessage(svc, req);

            JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            cancel.setEnabled(false);
            hostField = Ui.field(svc.host(), 16);
            portField = Ui.field(String.valueOf(svc.port()), 5);
            sslBox = Ui.check("use SSL", svc.secure());
            north.add(go);
            north.add(cancel);
            north.add(Ui.label("host"));
            north.add(hostField);
            north.add(Ui.label("port"));
            north.add(portField);
            north.add(sslBox);
            add(north, BorderLayout.NORTH);

            JPanel reqPanel = new JPanel(new BorderLayout());
            reqPanel.add(bold(" request"), BorderLayout.NORTH);
            reqPanel.add(reqEditor, BorderLayout.CENTER);
            JPanel respPanel = new JPanel(new BorderLayout());
            respPanel.add(bold(" response"), BorderLayout.NORTH);
            respPanel.add(respEditor, BorderLayout.CENTER);
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reqPanel, respPanel);
            // Equal halves that stay equal as the window resizes.
            split.setResizeWeight(0.5);
            split.addComponentListener(new java.awt.event.ComponentAdapter() {
                private boolean set = false;
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    if (!set && split.getHeight() > 0) { set = true; split.setDividerLocation(0.5); }
                }
            });
            split.setDividerSize(8);
            add(split, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout());
            south.setBorder(Ui.pad(2, 6, 2, 6));
            south.add(status, BorderLayout.CENTER);
            south.add(lengthLabel, BorderLayout.EAST);
            add(south, BorderLayout.SOUTH);

            sslBox.addActionListener(e -> {
                String p = portField.getText().trim();
                if (p.equals("80") && sslBox.isSelected()) portField.setText("443");
                else if (p.equals("443") && !sslBox.isSelected()) portField.setText("80");
            });
            go.addActionListener(e -> send());
            cancel.addActionListener(e -> { if (worker != null) worker.interrupt(); });
        }

        private JLabel bold(String s) {
            JLabel l = Ui.bold(s);
            l.setBorder(Ui.pad(3, 2, 3, 2));
            return l;
        }

        private void send() {
            HttpService svc;
            try {
                svc = new HttpService(hostField.getText().trim(),
                        Integer.parseInt(portField.getText().trim()), sslBox.isSelected());
            } catch (NumberFormatException ex) {
                status.setText("bad port number");
                return;
            }
            HttpMessage req = new HttpMessage(reqEditor.bytes());
            if (updateContentLength && req.body().length > 0) req.updateContentLength();

            status.setText("waiting");
            go.setEnabled(false);
            cancel.setEnabled(true);

            worker = new Thread(() -> {
                HttpClient.Options opt = new HttpClient.Options();
                opt.updateContentLength = updateContentLength;
                opt.followRedirects = followRedirects;
                opt.unpackGzip = true;
                long t0 = System.currentTimeMillis();
                HttpClient.Result r = new HttpClient().send(svc, req, opt);
                long ms = System.currentTimeMillis() - t0;
                SwingUtilities.invokeLater(() -> {
                    go.setEnabled(true);
                    cancel.setEnabled(false);
                    if (!r.ok()) {
                        status.setText(r.error);
                        return;
                    }
                    respEditor.setMessage(svc, r.response, req);
                    int redirs = r.redirectsFollowed;
                    status.setText("done" + (redirs > 0 ? " (followed " + redirs + " redirect"
                            + (redirs == 1 ? "" : "s") + ")" : "") + "  " + ms + " ms");
                    lengthLabel.setText("length: " + r.response.length());

                    LogEntry entry = new LogEntry("repeater", svc, req);
                    entry.setResponse(r.response);
                    entry.setRemoteIp(r.remoteIp);
                    entry.setDurationMillis(ms);
                    ctx.store().add(entry);
                    ctx.siteMapChanged(entry);
                });
            }, "repeater-send");
            worker.setDaemon(true);
            worker.start();
        }
    }
}
