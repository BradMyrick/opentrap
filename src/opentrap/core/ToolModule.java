package opentrap.core;

import javax.swing.JMenu;

/**
 * The contract every tool implements. The shell knows nothing else about a
 * tool: it asks for a name, some tabs, and optionally some menus, then renders
 * whatever it is given. Adding a tenth tool costs one registration call.
 */
public interface ToolModule {

    /** Lower-case display name used on the tab and in "send to" menus. */
    String name();

    /** Tabs shown inside this tool. One entry means no inner tab bar. */
    Tab[] tabs();

    /** Menus contributed to the suite menu bar, or null for none. */
    default JMenu[] menus() { return null; }

    /** Force an inner tab bar even with a single tab. */
    default boolean alwaysTabbed() { return false; }

    /** Called once after every tool is registered and the context is live. */
    default void init(SuiteContext ctx) {}

    /** Called when the suite is shutting down. */
    default void shutdown() {}

    /**
     * A serialisable snapshot of this tool's working state, saved with the
     * project. Return null if the tool has nothing worth persisting.
     */
    default java.io.Serializable saveState() { return null; }

    /** Restore a snapshot previously produced by {@link #saveState()}. */
    default void restoreState(java.io.Serializable state) {}

    /** A tab inside a tool: a title and the component to show. */
    record Tab(String title, javax.swing.JComponent component) {}
}
