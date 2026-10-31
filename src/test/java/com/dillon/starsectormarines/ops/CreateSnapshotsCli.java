package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.render2d.texture.TextureUsageCapture;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import java.util.ArrayList;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotCatalog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotRunner;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.LayoutLint;

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
        if (TextureUsageCapture.install("snapshots")) suites = labelled(suites);
        if (LayoutLint.enabled()) suites = linted(suites);
        // -Dsnapshot.translation=<locale> renders every selected suite in that translation mod.
        String locale = System.getProperty(HeadlessStrings.TRANSLATION_PROPERTY, "");
        try (var strings = locale.isBlank() ? new HeadlessStrings(projectRoot.resolve("mod"))
                : HeadlessStrings.translation(projectRoot.resolve("mod"), locale)) {
            List<Path> outputs = new SnapshotRunner().create(
                    new SnapshotContext(projectRoot, starsectorCore),
                    suites, outputRoot, true);
            System.out.println("Created " + outputs.size() + " visual snapshots from "
                    + suites.size() + " suite(s) under " + outputRoot.toAbsolutePath());
        } finally {
            // A stale suite rethrows at the end of the run; what the others laid out is still worth reading.
            if (LayoutLint.enabled()) {
                LayoutLint.write(LayoutLint.reportDirectory());
                System.out.println("Layout lint written under " + LayoutLint.reportDirectory());
            }
        }
    }

    /** Each suite's documents linted under its own id ({@code -Dsnapshot.layoutLint=<dir>}, {@link LayoutLint}). */
    private static List<SnapshotSuite> linted(List<SnapshotSuite> suites) {
        List<SnapshotSuite> out = new ArrayList<>(suites.size());
        for (SnapshotSuite suite : suites) {
            out.add(new SnapshotSuite() {
                @Override public String id() { return suite.id(); }
                @Override public String label() { return suite.label(); }
                @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
                    LayoutLint.suite(suite.id());
                    return suite.render(context);
                }
            });
        }
        return out;
    }

    /**
     * Each suite's draws recorded under its own id, so the usage record says which suite drew an
     * image at the size that decided its fit. A suite's artifacts are written before the next
     * suite renders, so the label holds for everything it draws.
     */
    private static List<SnapshotSuite> labelled(List<SnapshotSuite> suites) {
        List<SnapshotSuite> out = new ArrayList<>(suites.size());
        for (SnapshotSuite suite : suites) {
            out.add(new SnapshotSuite() {
                @Override public String id() { return suite.id(); }
                @Override public String label() { return suite.label(); }
                @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
                    TextureUsage.source("snapshot:" + suite.id());
                    return suite.render(context);
                }
            });
        }
        return out;
    }
}
