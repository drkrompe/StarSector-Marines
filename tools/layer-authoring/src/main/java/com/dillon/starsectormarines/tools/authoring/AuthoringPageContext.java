package com.dillon.starsectormarines.tools.authoring;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Domain-neutral paths and host callbacks available to contributed authoring pages. */
public final class AuthoringPageContext {

    private final Path projectRoot;
    private final Path starsectorCoreRoot;
    private final Consumer<String> statusReporter;
    private final Runnable stateChanged;

    public AuthoringPageContext(Path projectRoot, Path starsectorCoreRoot,
                                Consumer<String> statusReporter, Runnable stateChanged) {
        if (projectRoot == null) throw new IllegalArgumentException("project root is required");
        if (starsectorCoreRoot == null) {
            throw new IllegalArgumentException("Starsector core root is required");
        }
        if (statusReporter == null) throw new IllegalArgumentException("status reporter is required");
        if (stateChanged == null) throw new IllegalArgumentException("state callback is required");
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.starsectorCoreRoot = starsectorCoreRoot.toAbsolutePath().normalize();
        this.statusReporter = statusReporter;
        this.stateChanged = stateChanged;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path starsectorCoreRoot() {
        return starsectorCoreRoot;
    }

    public void reportStatus(String message) {
        statusReporter.accept(message == null ? "" : message);
    }

    /** Notifies the host that page state, especially its dirty state, changed. */
    public void stateChanged() {
        stateChanged.run();
    }
}
