package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** The Mech Bay and the Shuttle Bay before and after a machine leaves, at 1920x1080 and 100%. */
public final class BayRemovalSnapshotSuite implements SnapshotSuite {
    public String id() { return "bay-removal"; }
    public String label() { return "A scrapped or stored machine leaves the bay"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        try (var strings = new HeadlessStrings(context.modRoot(), text -> text)) {
            return UiSnapshotSuite.renderBayRemoval(context);
        }
    }
}
