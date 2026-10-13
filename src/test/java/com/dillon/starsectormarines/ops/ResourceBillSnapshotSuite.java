package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.ArrayList;
import java.util.List;

/** Production order panels at 1920x1080 and 100%, including large stock and short bills. */
public final class ResourceBillSnapshotSuite implements SnapshotSuite {
    public String id() { return "company-resource-bills"; }
    public String label() { return "Company stock and transaction bills"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        var frames = new ArrayList<SnapshotArtifact>();
        for (boolean expanded : List.of(false, true)) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> expanded
                    ? OpeningLocalizationSnapshotSuite.expand(text) : text)) {
                for (var frame : UiSnapshotSuite.renderResourceBills(context))
                    frames.add(new SnapshotArtifact((expanded ? "expanded/" : "english/")
                            + frame.relativePath(), frame.image()));
                if (!expanded) {
                    for (var frame : new EngineeringOverlaySnapshotSuite().render(context, true))
                        frames.add(new SnapshotArtifact("engineering/" + frame.relativePath(), frame.image()));
                    for (var frame : UiSnapshotSuite.renderPreparationResourceBills(context))
                        frames.add(new SnapshotArtifact("preparation/" + frame.relativePath(), frame.image()));
                    for (var frame : new SecondSquadSnapshotSuite().render(context))
                        frames.add(new SnapshotArtifact("barracks/" + frame.relativePath(), frame.image()));
                }
            }
        }
        return frames;
    }
}
