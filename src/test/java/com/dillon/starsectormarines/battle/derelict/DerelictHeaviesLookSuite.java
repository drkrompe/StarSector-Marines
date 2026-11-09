package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Derelict security's heavies in a live ruin (`derelict-security-network.md`): the extensive-grade fixture (eight
 * boats) played under autonomous command, recorded through the look preset twice: the walkers at their posts before the company comes,
 * and from the first tick a walker takes a target, following the walkers through the fight. Opt-in.
 */
public final class DerelictHeaviesLookSuite implements SnapshotSuite {

    static final String FIXTURE = "src/test/resources/battle-fixtures/ruins-derelict-extensive-v1.json";
    /** The latest tick a walker's first target is waited for. */
    static final int CONTACT_BY = 6000;
    static final int RECORD_TICKS = 600;

    @Override public String id() { return "derelict-heavies-look"; }

    @Override public String label() { return "Derelict security's walkers holding and fighting in a live ruin"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE),
                    StandardCharsets.UTF_8));
            int contact = firstContact(fixture);
            List<SnapshotArtifact> out = new ArrayList<>(LookReview.record(context.modRoot(),
                    shot("derelict-posts", fixture, 1, 90, 30f)));
            out.addAll(LookReview.record(context.modRoot(),
                    shot("derelict-fight", fixture, Math.max(1, contact - 60), RECORD_TICKS,
                            44f)));
            return out;
        }
    }

    /** The first tick a derelict walker holds a target, or {@link #CONTACT_BY} when none does. */
    static int firstContact(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        while (sim.getSimTickIndex() < CONTACT_BY) {
            sim.advance(BattleSimulation.TICK_DT);
            for (long walker : walkers(sim)) {
                if (sim.targetOf(walker) != 0L) {
                    System.out.printf("    [look/derelict] first walker contact at tick %d%n", sim.getSimTickIndex());
                    return sim.getSimTickIndex();
                }
            }
        }
        System.out.println("    [look/derelict] no walker contact by tick " + CONTACT_BY);
        return CONTACT_BY;
    }

    /** The fixture played unrecorded to {@code from}, then recorded on the walkers' middle. */
    static LookReview.Shot shot(String name, JSONObject fixture, int from, int ticks, float cellsAcross) {
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(fixture);
            } catch (Exception e) {
                throw new IllegalStateException("derelict fixture", e);
            }
            while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
            return sim;
        }, placement -> List.of(walkersView(cellsAcross)), ticks, 3, 4);
    }

    /**
     * A camera on the middle of the walkers holding a target, or on the last walker posted before one does; it holds
     * where they last were once none stands.
     */
    static LookReview.View walkersView(float cellsAcross) {
        float[] last = {Float.NaN, Float.NaN};
        return new LookReview.View("walkers", sim -> {
            float sx = 0f;
            float sy = 0f;
            int n = 0;
            List<Long> shown = new ArrayList<>();
            for (long walker : walkers(sim)) if (sim.targetOf(walker) != 0L) shown.add(walker);
            // The walkers in the fight, or before one is, the last posted (the siege frame beside a line frame).
            List<Long> all = walkers(sim);
            if (shown.isEmpty() && !all.isEmpty()) shown = List.of(all.get(all.size() - 1));
            for (long walker : shown) {
                sx += sim.world().renderX(walker);
                sy += sim.world().renderY(walker);
                n++;
            }
            if (n > 0) {
                last[0] = sx / n;
                last[1] = sy / n;
            }
            if (Float.isNaN(last[0])) return new float[] {sim.getGrid().getWidth() / 2f, sim.getGrid().getHeight() / 2f,
                    cellsAcross};
            return new float[] {last[0], last[1], cellsAcross};
        });
    }

    private static List<Long> walkers(BattleSimulation sim) {
        List<Long> out = new ArrayList<>();
        DerelictSecurityDirector director = sim.derelictSecurity();
        if (director == null) return out;
        for (DerelictSecurityDirector.Walker walker : director.walkers()) {
            for (int i = 0, n = sim.squadMemberCount(walker.squadId()); i < n; i++) {
                long id = sim.squadMemberAt(walker.squadId(), i);
                if (sim.world().isAlive(id)) out.add(id);
            }
        }
        return out;
    }
}
