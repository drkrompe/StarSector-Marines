package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Derelict security's guardians (`ruins-guardians.md`) on the vast ruin, through the look preset with the roof lifted:
 * the hive cradle in its light well with its flock up, and the vault frame on its post in the vault's approach, each
 * wide and close. The site is played only until the network has stood them. Opt-in. No outcome is judged.
 */
public final class RuinsGuardiansLookSuite implements SnapshotSuite {

    static final String VAST_FIXTURE = DerelictHeaviesLookSuite.VAST_FIXTURE;
    static final int SETTLE_TICKS = 120;
    static final float WIDE_CELLS = 36f;
    static final float CLOSE_CELLS = 16f;

    @Override public String id() { return "ruins-guardians-look"; }

    @Override public String label() { return "Derelict security's guardians: the hive in its well, the vault frame"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(VAST_FIXTURE),
                    StandardCharsets.UTF_8));
            BattleSimulation sim = HeadlessBattles.fixture(fixture);
            while (sim.getSimTickIndex() < SETTLE_TICKS && step(sim)) {
                // played to the guardians' posts, or to the end of a battle decided before them
            }
            List<SnapshotArtifact> out = new ArrayList<>();
            Flock hive = null;
            for (Flock flock : sim.flocks().flocks()) if (flock.hatch() != null) hive = flock;
            if (hive != null && sim.resolveUnit(hive.cradle()) != 0L) {
                long housing = hive.cradle();
                System.out.printf("    [look/guardians] hive at (%.1f, %.1f), %d drones up%n",
                        sim.world().x(housing), sim.world().y(housing), hive.size());
                out.addAll(stills(context, sim, "guardian-hive", sim.world().renderX(housing),
                        sim.world().renderY(housing)));
            } else {
                System.out.println("    [look/guardians] no hive stood on this site");
            }
            DerelictSecurityDirector director = sim.derelictSecurity();
            if (director != null) {
                for (DerelictSecurityDirector.Walker walker : director.walkers()) {
                    if (walker.frame() != DerelictWalkerFrame.VAULT) continue;
                    if (sim.squadMemberCount(walker.squadId()) <= 0) continue;
                    long frame = sim.squadMemberAt(walker.squadId(), 0);
                    System.out.printf("    [look/guardians] vault frame at (%.1f, %.1f), %s%n",
                            sim.world().x(frame), sim.world().y(frame), director.braceOf(frame));
                    out.addAll(stills(context, sim, "guardian-vault-frame", sim.world().renderX(frame),
                            sim.world().renderY(frame)));
                    break;
                }
            }
            return out;
        }
    }

    private static List<SnapshotArtifact> stills(SnapshotContext context, BattleSimulation sim, String name, float x,
                                                 float y) throws IOException {
        return List.of(
                new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y,
                        WIDE_CELLS).withoutRoofs().render(sim, name)),
                new SnapshotArtifact(name + "-close.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, x, y, CLOSE_CELLS).withoutRoofs().render(sim, name + "-close")));
    }

    /** Advances one tick; false once the battle is decided, when the tick stops advancing. */
    private static boolean step(BattleSimulation sim) {
        int before = sim.getSimTickIndex();
        sim.advance(BattleSimulation.TICK_DT);
        return sim.getSimTickIndex() != before;
    }
}
