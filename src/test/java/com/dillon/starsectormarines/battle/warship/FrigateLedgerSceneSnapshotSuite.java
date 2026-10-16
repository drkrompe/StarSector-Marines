package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;

/** The frigate ledger loops (1920x1080), from the same run that produced their verdicts and readings. */
public final class FrigateLedgerSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public FrigateLedgerSceneSnapshotSuite() {
        super(new FrigateLedgerScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** The close view is shown to people, so it is recorded at the full 1920x1080 too. */
    @Override protected float closeViewScale() { return 1f; }

    @Override protected float[] closeView(String loopId) {
        return null;
    }
}
