package com.dillon.starsectormarines.tools.authoring;

import javax.swing.JComponent;

/** One lifecycle-aware page contributed to the standalone authoring workbench. */
public interface AuthoringPage extends AutoCloseable {

    JComponent component();

    default boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    default void close() {
    }
}
