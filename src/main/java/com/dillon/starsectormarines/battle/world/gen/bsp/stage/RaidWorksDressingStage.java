package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustries;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustry;
import com.dillon.starsectormarines.battle.world.gen.raid.WorksDefences;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.WallMasks;

/**
 * A Raid site's works dressed by its industry ({@link RaidIndustry#dressWorks}), then given its defences by security
 * ({@link WorksDefences}): after the buildings, roads and
 * doorways are final and before the landscape pass takes the open country. Inert on every map that is not a Raid
 * site.
 */
public final class RaidWorksDressingStage implements GenStage {

    @Override public void run(GenContext c) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        if (plan == null) return;
        RaidIndustry industry = RaidIndustries.byKey(plan.raidIndustry());
        Precinct works = plan.objective();
        if (industry == null || works == null) return;
        industry.dressWorks(c, plan, works);
        WorksDefences.lay(c, plan, works, industry);
        finishLateBuildings(c);
    }

    /**
     * The buildings a dresser stood after the map was finished (a set piece's hut or shed, {@code MilitarySetPieces})
     * given what {@link FinalizeStage} gave the rest: their walls' hit points, cover and wall tags, and a building of
     * their own, which is what a roof is drawn over. Without it they stood roofless.
     */
    private static void finishLateBuildings(GenContext c) {
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                if (c.topology.isWall(x, y) && c.grid.getWallHp(x, y) == 0) {
                    c.grid.setWallHp(x, y, FinalizeStage.WALL_HP_DEFAULT);
                }
            }
        }
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) c.grid.recomputeCoverAt(x, y);
        c.topology.tagDefaultWalls(c.grid);
        FinalizeStage.dressUnstyledWalls(c.topology);
        WallMasks.stampUnclaimed(c.topology);
        Buildings buildings = c.get(BspKeys.BUILDINGS);
        if (buildings == null) {
            c.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(c.topology, c.seed));
        } else {
            BuildingFloodFill.populate(c.topology, c.seed, buildings);
        }
    }
}
