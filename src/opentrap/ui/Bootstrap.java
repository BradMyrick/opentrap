package opentrap.ui;

import opentrap.core.SuiteContext;
import java.nio.file.Path;

/** Registers every tool with the suite shell, in tab order. */
public final class Bootstrap {
    private Bootstrap() {}

    public static void registerTools(SuiteContext ctx, SuiteFrame frame, Path home) throws Exception {
        frame.register(new opentrap.dashboard.DashboardTool());
        frame.register(new opentrap.target.TargetTool());
        frame.register(new opentrap.proxy.ProxyTool());
        frame.register(new opentrap.scanner.ScannerTool());
        frame.register(new opentrap.intruder.IntruderTool(ctx));
        frame.register(new opentrap.repeater.RepeaterTool());
        frame.register(new opentrap.collaborator.CollaboratorTool());
        frame.register(new opentrap.logger.LoggerTool());
        frame.register(new opentrap.sequencer.SequencerTool());
        frame.register(new opentrap.decoder.DecoderTool());
        frame.register(new opentrap.comparer.ComparerTool());
    }
}
