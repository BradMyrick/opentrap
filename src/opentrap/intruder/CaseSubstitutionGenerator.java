package opentrap.intruder;

import opentrap.ui.Ui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Generates case variants of each base item from a set of modifications. */
class CaseSubstitutionGenerator extends AbstractGenerator {

    private final JCheckBox noChange = Ui.check("no change", true);
    private final JCheckBox lower    = Ui.check("to lower case", false);
    private final JCheckBox upper    = Ui.check("to upper case", false);
    private final JCheckBox proper1  = Ui.check("to Propername", false);
    private final JCheckBox proper2  = Ui.check("to ProperName", false);
    private final JPanel panel;

    CaseSubstitutionGenerator() {
        for (JCheckBox c : new JCheckBox[]{noChange, lower, upper, proper1, proper2}) {
            c.addActionListener(e -> fire());
        }
        panel = Ui.vbox(noChange, lower, upper, proper1, proper2);
    }

    /** Selected case modes, mapped to {@link PayloadProcessor} indices. */
    private List<Integer> modes() {
        List<Integer> m = new ArrayList<>();
        if (noChange.isSelected()) m.add(0);
        if (lower.isSelected())    m.add(1);
        if (upper.isSelected())    m.add(2);
        if (proper1.isSelected())  m.add(3);
        if (proper2.isSelected())  m.add(4);
        return m;
    }

    @Override public String label() { return " case substitution"; }
    @Override public JComponent config() { return panel; }

    @Override public int count() {
        return Math.min(base.size() * Math.max(modes().size(), 1), CAP);
    }

    @Override public List<String> generate() {
        List<Integer> modes = modes();
        Set<String> out = new LinkedHashSet<>();
        for (String item : base) {
            for (int mode : modes) {
                out.add(PayloadProcessor.applyCase(item, mode));
                if (out.size() >= CAP) return new ArrayList<>(out);
            }
        }
        return new ArrayList<>(out);
    }
}
