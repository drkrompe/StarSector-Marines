package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.List;

/**
 * Look experiment for the dry rock gorge (`voids.md`): a barren world's stone plain split by a gorge of grey regolith
 * strata down to a rubble floor, crossed by a rock bridge and a plank span. {@code -Dsnapshot.drygorge.depth=40}.
 */
public final class DryGorgeVoidLookSuite extends StyleVoidLookSuite {

    @Override public String id() { return "dry-gorge-void-look"; }

    @Override public String label() { return "Dry rock gorge through a stone plain (natural void look experiment)"; }

    @Override String key() { return "drygorge"; }

    @Override float defaultDepth() { return 40f; }

    /** The gorge: three ledges, the floor 40 m down at the default depth. */
    @Override NaturalVoid drop(float depth, float eye) {
        float f = depth / 40f;
        return new NaturalVoid(NaturalVoid.Style.DRY_GORGE, List.of(
                new NaturalVoid.Step(13f * f, .6f),
                new NaturalVoid.Step(27f * f, 1.3f),
                new NaturalVoid.Step(40f * f, 1.9f)),
                eye, "graphics/battle/voids/regolith-wall.png", 12f, 37);
    }

    @Override NaturalVoidZoning.Drop zoned() { return NaturalVoidZoning.DRY_GORGE; }

    @Override GroundKind ground() { return GroundKind.STONE; }

    @Override String surface() { return "floors.stone"; }

    /** Narrower than a canyon, pinching and opening. */
    @Override float halfWidth(int x) {
        return 7.5f + 2f * (float) Math.sin(x / 17f + 2f) + 1f * (float) Math.sin(x / 5f);
    }
}
