package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.List;

/**
 * Look experiment for the arid canyon (`voids.md`): a desert's sand plain split by a canyon of banded sandstone,
 * terraced down to a dry wash, crossed by a sandstone bridge and a plank span. {@code -Dsnapshot.canyon.depth=44}.
 */
public final class CanyonVoidLookSuite extends StyleVoidLookSuite {

    @Override public String id() { return "canyon-void-look"; }

    @Override public String label() { return "Arid canyon through a sand plain (natural void look experiment)"; }

    @Override String key() { return "canyon"; }

    @Override float defaultDepth() { return 44f; }

    /** The canyon: four terraces, wide, the floor 44 m down at the default depth. */
    @Override NaturalVoid drop(float depth, float eye) {
        float f = depth / 44f;
        return new NaturalVoid(NaturalVoid.Style.CANYON, List.of(
                new NaturalVoid.Step(9f * f, .6f),
                new NaturalVoid.Step(21f * f, 1.7f),
                new NaturalVoid.Step(33f * f, 2.6f),
                new NaturalVoid.Step(44f * f, 3.4f)),
                eye, "graphics/battle/voids/canyon-wall.png", 12f, 29);
    }

    @Override GroundKind ground() { return GroundKind.SAND; }

    @Override String surface() { return "floors.sand"; }

    /** Wider than a gorge, opening and narrowing. */
    @Override float halfWidth(int x) {
        return 10f + 2.5f * (float) Math.sin(x / 19f + 1.3f) + 1.1f * (float) Math.sin(x / 6f);
    }
}
