package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buildings burn by material (`fire-nouns.md`, Buildings): a grass fire run into
 * a light-walled house and a fortified one on the green colony
 * ({@link FireBuildingsStage}). Verdicts are the mechanics: a light wall that
 * burns through is rubble you can walk through, the light house's rooms burn,
 * a room that catches is open to the sky, and the fortified house burns nowhere,
 * walls, windows or floors. How many wall cells fell and room cells burnt, and
 * how long the fire took to get in, are metrics.
 */
public final class FireBuildingsScene implements BehaviorScene {

    static final int MAX_TICKS = 30 * 150;

    @Override public String id() { return "fire-buildings"; }

    @Override public String label() { return "A grass fire takes a light-walled house and leaves a fortified one standing"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        FireBuildingsStage stage = FireBuildingsStage.build();
        try (var sim = stage.sim) {
            FireField fire = stage.fire;
            NavigationGrid grid = sim.getGrid();
            CellTopology topo = sim.getTopology();
            int w = grid.getWidth();
            int[] light = stage.light, fort = stage.fortified;
            int fallen = 0, standingBurnt = 0, roomsBurnt = 0, roofsShut = 0, fortBurning = 0;
            int atWalls = -1, firstRoom = -1, nearFort = -1;
            int tick = 0;
            for (; tick < MAX_TICKS; tick++) {
                fire.tick(tick);
                if (tick % FireField.STEP_TICKS != 0) continue;
                for (int i = 0, n = fire.burningCount(); i < n; i++) {
                    int idx = fire.burningCell(i), x = idx % w, y = idx / w;
                    int mask = fire.burnedMaskAt(idx);
                    if (FireBuildingsStage.inside(light, x, y)) {
                        if (atWalls < 0 && FireFuel.STRUCTURE.in(mask)) atWalls = tick;
                        if (firstRoom < 0 && FireFuel.FURNISHING.in(mask)) firstRoom = tick;
                    }
                    if (stage.ofBuilding(fort, x, y)) fortBurning++;
                    if (nearFort < 0 && Math.abs(x - FireBuildingsStage.cx(fort)) <= (fort[3] - fort[1]) / 2f + 2
                            && Math.abs(y - FireBuildingsStage.cy(fort)) <= (fort[4] - fort[2]) / 2f + 2) nearFort = tick;
                }
                if (tick > 300 && !fire.isActive()) break;
            }
            for (int y = light[2]; y <= light[4]; y++) {
                for (int x = light[1]; x <= light[3]; x++) {
                    int idx = y * w + x;
                    if (fire.stateAt(idx) != FireField.BURNT) continue;
                    int mask = fire.burnedMaskAt(idx);
                    if (FireFuel.STRUCTURE.in(mask)) {
                        if (grid.isWalkable(x, y)) fallen++;
                        else standingBurnt++;
                    }
                    if (FireFuel.FURNISHING.in(mask)) {
                        roomsBurnt++;
                        if (!topo.isRoofDestroyed(x, y)) roofsShut++;
                    }
                }
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("burnt-walls-fall", fallen > 0 && standingBurnt == 0,
                    fallen + " light wall cells burnt through and fell; " + standingBurnt + " burnt out and still stand"));
            verdicts.add(Verdict.of("rooms-burn", roomsBurnt > 0, roomsBurnt + " room cells of the light house burnt"));
            verdicts.add(Verdict.of("burning-rooms-open-to-the-sky", roofsShut == 0,
                    roofsShut + " burnt room cells still under their roof"));
            verdicts.add(Verdict.of("fortified-does-not-burn", fortBurning == 0,
                    fortBurning + " burning cell-steps inside the fortified house"
                            + (nearFort >= 0 ? ", the fire having reached it on tick " + nearFort : " (the fire never reached it)")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("wallCellsFallen", fallen);
            metrics.put("roomCellsBurnt", roomsBurnt);
            metrics.put("wallsCaughtTick", atWalls);
            metrics.put("firstRoomTick", firstRoom);
            metrics.put("fireReachedFortifiedTick", nearFort);
            metrics.put("ticks", tick);
            return List.of(new SceneReport(id(), "two-houses", tick, verdicts, metrics));
        }
    }
}
