package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The cordon ridge's guns as the owner would see them (`heart-nouns.md`, Cordon's works): the Wellhollow story map
 * (arid seed 5, the airbase look's battle) played a moment in, then recorded on a fixed camera over the forward line
 * round the ridge flak nearest a third of the way across, away from the company's gap, so a stretch of the line's guns
 * and its flak read before the swarm reaches them. Stands the battle up, so opt-in.
 */
public final class HeartRidgeLookSuite implements SnapshotSuite {

    /** Ticks played unrecorded first: the guns are seated and the first waves are stepping off. */
    static final int FROM = 60;
    /** Share of the front's width the camera looks for a flak emplacement nearest. */
    static final float ACROSS = 0.3f;
    /** Cells across the frame: a stretch of the line with several nests and its flak. */
    static final float CELLS_ACROSS = 90f;

    @Override public String id() { return "heart-ridge-look"; }

    @Override public String label() { return "Heart cordon ridge: the forward line's guns and flak on the story map (arid seed 5)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            return new ArrayList<>(LookReview.record(context.modRoot(), shot()));
        }
    }

    static LookReview.Shot shot() {
        float[] at = new float[2];
        return new LookReview.Shot("ridge-guns", LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(HeartAirbaseLookSuite.FIXTURE));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            while (sim.getSimTickIndex() < FROM) sim.advance(BattleSimulation.TICK_DT);
            float want = sim.getGrid().getWidth() * ACROSS;
            long flak = 0L;
            int guns = 0;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.ALLY || !sim.turretState().isTurret(unit)) continue;
                guns++;
                if (!TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID.equals(sim.turretState().structure(unit).id)) continue;
                if (flak == 0L || Math.abs(sim.world().x(unit) - want) < Math.abs(sim.world().x(flak) - want)) flak = unit;
            }
            if (flak != 0L) {
                at[0] = sim.world().x(flak);
                at[1] = sim.world().y(flak);
            }
            System.out.printf("    [look/ridge] %d allied guns standing, framed on ridge flak at %.0f,%.0f%n", guns, at[0], at[1]);
            return sim;
        }, placement -> List.of(new LookReview.View("play", sim -> new float[]{at[0], at[1], CELLS_ACROSS})), 600, 6, 4);
    }
}
