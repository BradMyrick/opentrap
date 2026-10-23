package opentrap.ui;

import opentrap.core.*;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Save and restore the project as a single zip, as the original did. */
public final class StateIO {
    private StateIO() {}

    /** Project file extension. */
    public static final String EXT = ".otrap";

    /** A timestamped default project name, matching the startup dialog. */
    public static String defaultProjectFileName() {
        return "project-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + EXT;
    }

    private static File withExtension(File f) {
        if (f.getName().toLowerCase().endsWith(EXT)) return f;
        return new File(f.getParentFile(), f.getName() + EXT);
    }

    public static void saveDialog(SuiteContext ctx, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("save state");
        // Reuse the project file this session is bound to; otherwise pre-fill a
        // timestamped name (like the startup "new project" option does), so a
        // temporary session never has to have a name typed from scratch.
        if (ctx.projectFile() != null) fc.setSelectedFile(ctx.projectFile());
        else fc.setSelectedFile(new File(defaultProjectFileName()));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = withExtension(fc.getSelectedFile());
        if (f.exists()) {
            int r = JOptionPane.showConfirmDialog(parent,
                    "This file already exists. Are you sure?", "confirm", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }
        try {
            save(ctx, f);
            ctx.setProjectFile(f);   // bind the session, so later saves reuse it
            ctx.alert("suite", "state saved to " + f.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "An error occurred: " + e.getMessage(),
                    "error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void restoreDialog(SuiteContext ctx, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("restore state");
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            int n = restore(ctx, fc.getSelectedFile());
            ctx.alert("suite", "restored " + n + " items from " + fc.getSelectedFile().getName());
            JOptionPane.showMessageDialog(parent,
                    "Restore completed. " + n + " history items loaded.",
                    "restore state", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "An error occurred: " + e.getMessage(),
                    "error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void save(SuiteContext ctx, File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            zos.putNextEntry(new ZipEntry("opentrap"));
            ObjectOutputStream oos = new ObjectOutputStream(zos);
            oos.writeInt(4);                            // format version
            oos.writeObject(ctx.store().all());         // history
            oos.writeObject(ctx.scope());               // scope
            oos.writeObject(ctx.settings().snapshot()); // settings
            oos.writeObject(ctx.sessions().snapshot()); // session jar, macros, rules (v2)
            oos.writeObject(ctx.organizer().snapshot()); // organizer items (v3)
            // v4: scanner issues, and each tool's own working state.
            oos.writeObject(new ArrayList<>(ctx.issues()));
            java.util.LinkedHashMap<String, java.io.Serializable> toolStates = new java.util.LinkedHashMap<>();
            for (opentrap.core.ToolModule t : ctx.tools()) {
                java.io.Serializable s = t.saveState();
                if (s != null) toolStates.put(t.name(), s);
            }
            oos.writeObject(toolStates);
            oos.flush();
            zos.closeEntry();
        }
    }

    @SuppressWarnings("unchecked")
    public static int restore(SuiteContext ctx, File file) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry e = zis.getNextEntry();
            if (e == null) throw new IOException("Input file is not in correct format");
            ObjectInputStream ois = new ObjectInputStream(zis);
            int version = ois.readInt();
            if (version > 3) {
                ctx.alert("suite", "This file was saved by a later version - restore may fail");
            }
            List<LogEntry> entries = (List<LogEntry>) ois.readObject();
            Scope scope = (Scope) ois.readObject();
            java.util.Map<String, String> settings = (java.util.Map<String, String>) ois.readObject();
            opentrap.session.SessionManager.State session = null;
            if (version >= 2) {
                session = (opentrap.session.SessionManager.State) ois.readObject();
            }
            java.util.List<opentrap.organizer.OrganizerItem> organizer = null;
            if (version >= 3) {
                organizer = (java.util.List<opentrap.organizer.OrganizerItem>) ois.readObject();
            }
            java.util.List<opentrap.core.Issue> savedIssues = null;
            java.util.Map<String, java.io.Serializable> toolStates = null;
            if (version >= 4) {
                savedIssues = (java.util.List<opentrap.core.Issue>) ois.readObject();
                toolStates = (java.util.Map<String, java.io.Serializable>) ois.readObject();
            }

            ctx.store().replaceAll(entries == null ? new ArrayList<>() : entries);
            if (scope != null) {
                ctx.scope().include().clear();
                ctx.scope().include().addAll(scope.include());
                ctx.scope().exclude().clear();
                ctx.scope().exclude().addAll(scope.exclude());
            }
            ctx.settings().restore(settings);
            if (session != null) ctx.sessions().restore(session);
            if (organizer != null) ctx.organizer().restore(organizer);
            if (savedIssues != null) ctx.restoreIssues(savedIssues);
            if (toolStates != null) {
                for (opentrap.core.ToolModule t : ctx.tools()) {
                    java.io.Serializable s = toolStates.get(t.name());
                    if (s != null) {
                        try { t.restoreState(s); } catch (Exception ignore) {}
                    }
                }
            }
            int max = 0;
            for (LogEntry le : ctx.store().all()) max = Math.max(max, le.number());
            LogEntry.setCounter(max);
            ctx.scopeChanged();
            for (LogEntry le : ctx.store().all()) ctx.siteMapChanged(le);
            return ctx.store().size();
        }
    }
}
