package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Builds the finished town for the world it stands on ({@link Habitat}): every town wall in the habitat's material.
 * Runs once the town is placed, so it sees every wall. Inert for the open town, which is the map it was.
 *
 * <p>Presentation only. A wall keeps its cell, tags and hit points and changes only its surface, and its doors and
 * windows are drawn from their jamb walls, so they follow. A fort lot's walls are its works' (`fort-works.md`) and are
 * left to them. A building's doors stay ordinary doors on every world: where the world is hazardous the whole town is
 * sealed inside one wall with a few locks in it ({@code SealedTown}), not a lock on every door.
 */
public final class HabitatStage implements GenStage {

    @Override public void run(GenContext c) {
        apply(c, HabitatZoning.forProfile(c.get(BspKeys.MARKET_PROFILE)));
    }

    /**
     * Builds a placed town in {@code habitat}: the call for a site that lays its own buildings with its own recipe, after
     * they are stamped. Inert for the open town.
     */
    public static void apply(GenContext c, Habitat habitat) {
        if (habitat == null || habitat == Habitat.OPEN) return;
        rebuild(c.topology, habitat);
    }

    /** Every town wall, in the habitat's material. */
    static void rebuild(CellTopology topology, Habitat habitat) {
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (topology.getWorksStyle(x, y) != 0) continue;
                String id = topology.getSurfaceId(x, y);
                String built = habitat.wallFor(id);
                if (built != null && !built.equals(id)) topology.setSurface(x, y, topology.surfaceIndex(built));
            }
        }
    }
}
