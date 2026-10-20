import opentrap.testkit.Harness;

/**
 * Entry point for the test run.
 *
 * <p>Suites are listed explicitly so the run order is fixed and a missing suite
 * is a compile error rather than a silently skipped file.
 */
public final class RunTests {

    public static void main(String[] args) {
        System.out.println("OpenTRAP tests");
        System.out.println();

        new opentrap.engine.EngineMappingTest().run();
        new opentrap.proxy.ProxyDefaultsTest().run();
        new opentrap.ui.SendToComparerTest().run();

        System.exit(Harness.report());
    }
}
