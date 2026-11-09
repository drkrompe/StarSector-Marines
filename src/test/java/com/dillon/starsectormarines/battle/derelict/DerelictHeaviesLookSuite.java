package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
 * Derelict security's heavies in a live ruin (`derelict-security-network.md`): the extensive-grade fixture, flown on
 * the playBattle company's lift (one Hermes making six twelve-seat trips), played under autonomous command and
 * recorded through the look preset: the walkers at their posts before the company comes, from the first tick a walker
 * takes a target following the walkers through the fight, and on the vast site's interior, its vault frame on the
 * vault and a line frame in a chamber. Opt-in.
 */
public final class DerelictHeaviesLookSuite implements SnapshotSuite {

    static final String FIXTURE = "src/test/resources/battle-fixtures/ruins-derelict-extensive-v1.json";
    static final String VAST_FIXTURE = "src/test/resources/battle-fixtures/ruins-derelict-vast-v1.json";
    /** The latest tick a walker's first target is waited for. */
    static final int CONTACT_BY = 6000;
    static final int RECORD_TICKS = 600;
    /** Ticks the vast site plays before its interior stills, long enough for the walkers to stand on their posts. */
    static final int INTERIOR_SETTLE_TICKS = 120;
    static final float INTERIOR_CELLS = 36f;

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
            // The interior: the vast site's vault frame on the vault and a line frame in a chamber.
            JSONObject vast = new JSONObject(Files.readString(context.projectRoot().resolve(VAST_FIXTURE),
                    StandardCharsets.UTF_8));
            out.addAll(interior(context, vast));
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
     * The vast site's walkers at their interior posts after the network has stood them there, one still each through
     * the look preset with the roof lifted: the roof fades only for the company's own people inside, so the walkers
     * holding the halls are seen under it only this way.
     */
    static List<SnapshotArtifact> interior(SnapshotContext context, JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        while (sim.getSimTickIndex() < INTERIOR_SETTLE_TICKS) sim.advance(BattleSimulation.TICK_DT);
        List<SnapshotArtifact> out = new ArrayList<>();
        String[] names = {"derelict-interior-vault", "derelict-interior-chamber"};
        List<Long> all = walkers(sim);
        for (int i = 0; i < names.length && i < all.size(); i++) {
            long walker = all.get(i);
            out.add(new SnapshotArtifact(names[i] + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                    1080, sim.world().renderX(walker), sim.world().renderY(walker), INTERIOR_CELLS)
                    .withoutRoofs().render(sim, names[i])));
        }
        return out;
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
