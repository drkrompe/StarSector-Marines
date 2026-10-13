package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** Equipment discovery and squad editor return, at 1920x1080 and 100%. */
public final class BarracksEquipmentSnapshotSuite implements SnapshotSuite {
    public String id() { return "barracks-equipment"; }
    public String label() { return "Barracks equipment workspace"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBarracksEquipment(context);
    }
}
