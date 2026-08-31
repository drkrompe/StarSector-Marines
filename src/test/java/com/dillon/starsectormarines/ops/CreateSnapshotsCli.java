package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.tools.snapshot.SnapshotCatalog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotRunner;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Path;
import java.util.List;

/** Command-line entry point for the shared visual-snapshot catalog. */
public final class CreateSnapshotsCli {

    private CreateSnapshotsCli() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path starsectorCore = args.length > 1
                ? Path.of(args[1])
                : Path.of(System.getProperty("starsectorDir"), "starsector-core");
        Path outputRoot = args.length > 2
                ? Path.of(args[2])
                : projectRoot.resolve("build/snapshots");
        String selector = args.length > 3 ? args[3] : "all";

        // Suites read art from the install as well as from `mod/`; hull
        // geometry arrives the same way, and without it every aircraft in every
        // suite draws and is shot at as the same size.
        InstalledHullSpecs.install(starsectorCore);

        SnapshotCatalog catalog = SnapshotCatalog.discover();
        List<SnapshotSuite> suites = catalog.select(selector);
        List<Path> outputs = new SnapshotRunner().create(
                new SnapshotContext(projectRoot, starsectorCore),
                suites, outputRoot, true);
        System.out.println("Created " + outputs.size() + " visual snapshots from "
                + suites.size() + " suite(s) under " + outputRoot.toAbsolutePath());
    }
}
