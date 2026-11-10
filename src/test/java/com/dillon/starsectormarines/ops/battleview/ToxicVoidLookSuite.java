package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Look experiment for the toxic gorge (`voids.md`): a toxic world's crusted plain split by a gorge of corroded,
 * stained rock down to creeping acid sludge, crossed by a rock bridge and a plank span. {@code -Dsnapshot.toxic.depth=34}.
 * The plain is the toxic ground's crust where that tileset is in the mod, plain stone where it is not yet.
 */
public final class ToxicVoidLookSuite extends StyleVoidLookSuite {

    static final String CRUST = "toxic-ground.crust";

    @Override public String id() { return "toxic-void-look"; }

    @Override public String label() { return "Toxic gorge through a crusted plain (natural void look experiment)"; }

    @Override String key() { return "toxic"; }

    @Override float defaultDepth() { return 34f; }

    /** The gorge: three ledges, the sludge 34 m down at the default depth. */
    @Override NaturalVoid drop(float depth, float eye) {
        float f = depth / 34f;
        return new NaturalVoid(NaturalVoid.Style.TOXIC, List.of(
                new NaturalVoid.Step(11f * f, .6f),
                new NaturalVoid.Step(23f * f, 1.3f),
                new NaturalVoid.Step(34f * f, 1.9f)),
                eye, "graphics/battle/voids/toxic-wall.png", 11f, 43);
    }

    @Override NaturalVoidZoning.Drop zoned() { return NaturalVoidZoning.TOXIC_GORGE; }

    @Override GroundKind ground() { return GroundKind.STONE; }

    @Override String surface() {
        return Files.exists(Path.of("mod/data/tilesets/toxic-ground.tileset.json")) ? CRUST : "floors.stone";
    }

    /** A gorge's width, pinching and opening. */
    @Override float halfWidth(int x) {
        return 8f + 2.2f * (float) Math.sin(x / 15f + .7f) + 1f * (float) Math.sin(x / 5f);
    }
}
