package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** A mech and vehicles as bought, with nothing fitted, on the bay and doll at 1920x1080 and 100%. */
public final class BoughtMachinesSnapshotSuite implements SnapshotSuite {
    public String id() { return "bought-machines"; }
    public String label() { return "A bought mech and hull stand bare"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        try (var strings = new HeadlessStrings(context.modRoot(), text -> text)) {
            return UiSnapshotSuite.renderBoughtMachines(context);
        }
    }
}
