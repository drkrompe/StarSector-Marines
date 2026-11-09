package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Derelict security's flocks in a live ruin with a megastructure (`derelict-drone-flocks.md`, Roofs are the flock's
 * limit): the extensive-grade derelict fixture, whose last two layers are a roofed interior, played under autonomous
 * command. First it prints each flock's home and whether it is open sky, then plays to {@link #PLAY_TICKS} counting
 * member-ticks under a roof (the rule says none). Then the look preset, following the flocks: at their homes outside
 * the megastructure before the company comes, and from the first strike or pursuit, through the fight. Opt-in.
 */
public final class DerelictFlocksRuinsLookSuite implements SnapshotSuite {

    static final String FIXTURE = "src/test/resources/battle-fixtures/ruins-derelict-extensive-v1.json";
    static final int PLAY_TICKS = 6000;
    static final int RECORD_TICKS = 600;
    /** {@code -Dsnapshot.flocks.lineage=contingency} plays the fixture as contingency security, for its livery. */
    static final String LINEAGE_PROPERTY = "snapshot.flocks.lineage";
    static final String CONTINGENCY = "contingency";
    /** {@code -Dsnapshot.flocks.closeOnly=true} records only {@link #closeShot}, the look candidates' shot. */
    static final String CLOSE_ONLY_PROPERTY = "snapshot.flocks.closeOnly";
    static final float CLOSE_CELLS = 20f;
    /**
     * {@code -Dsnapshot.flocks.whineOnly=true} records only the whine candidates' clip (with
     * {@code -Dsnapshot.lookSound=true} for its soundtrack): close on the busiest flock from a little before its first
     * strike.
     */
    static final String WHINE_ONLY_PROPERTY = "snapshot.flocks.whineOnly";
    static final int WHINE_LEAD_TICKS = 300, WHINE_TICKS = 1200;
    static final float WHINE_CELLS = 28f;

    @Override public String id() { return "derelict-flocks-ruins-look"; }

    @Override public String label() { return "Derelict flocks outside a ruin's megastructure, waiting and striking"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE),
                    StandardCharsets.UTF_8));
            if (CONTINGENCY.equals(System.getProperty(LINEAGE_PROPERTY))) occupant(fixture, "CONTINGENCY_SECURITY");
            if (Boolean.getBoolean(CLOSE_ONLY_PROPERTY)) {
                return LookReview.record(context.modRoot(), closeShot(fixture));
            }
            int engaged = survey(fixture);
            if (Boolean.getBoolean(WHINE_ONLY_PROPERTY)) {
                // The whine candidates' clip: a flock rising from its wait into its first strike, heard from close by.
                LookReview.Shot base = shot("flocks-whine", fixture, Math.max(1, engaged - WHINE_LEAD_TICKS),
                        WHINE_TICKS, WHINE_CELLS, -1);
                return LookReview.record(context.modRoot(), new LookReview.Shot(base.name(), base.ground(),
                        base.footprintW(), base.footprintH(), base.stage(),
                        placement -> List.of(flockView("close", WHINE_CELLS, -1)), base.ticks(), base.everyTicks(),
                        base.stills(), base.stillFrames(), base.speed()));
            }
            List<SnapshotArtifact> out = new ArrayList<>(LookReview.record(context.modRoot(),
                    shot("flocks-waiting", fixture, 30, 240, 60f, 0)));
            out.addAll(LookReview.record(context.modRoot(),
                    shot("flocks-engaged", fixture, Math.max(1, engaged - 30), RECORD_TICKS, 60f, -1)));
            return out;
        }
    }

    /**
     * The look candidates' shot (`derelict-drone-flocks.md`, slice 7): close on a flock waiting by its cradle out in the
     * open, so the drones and the cradle read at their own size. Without the long survey.
     */
    static LookReview.Shot closeShot(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
        int flockId = 0;
        for (Flock flock : sim.flocks().flocks()) {
            if (open.open((int) flock.homeX(), (int) flock.homeY())) flockId = flock.id();
        }
        sim.close();
        int pinned = flockId;
        LookReview.Shot base = shot("flocks-close", fixture, 60, 120, CLOSE_CELLS, pinned);
        return new LookReview.Shot(base.name(), base.ground(), base.footprintW(), base.footprintH(), base.stage(),
                placement -> List.of(flockView("close", CLOSE_CELLS, pinned)), base.ticks(), base.everyTicks(),
                base.stills(), base.stillFrames(), base.speed());
    }

    /** Sets every {@code occupant} in the fixture, at any depth. */
    static void occupant(Object node, String occupant) throws Exception {
        if (node instanceof JSONObject o) {
            for (Iterator<?> keys = o.keys(); keys.hasNext(); ) {
                String key = (String) keys.next();
                if (key.equals("occupant")) o.put(key, occupant);
                else occupant(o.opt(key), occupant);
            }
        } else if (node instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) occupant(a.get(i), occupant);
        }
    }

    /** Prints the flocks' homes and roof-ticks over the play; returns the first tick a flock strikes or pursues. */
    static int survey(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
        for (Flock flock : sim.flocks().flocks()) {
            System.out.printf("    [look/flocks] flock %d home (%.1f, %.1f) open=%s, %d members%n", flock.id(),
                    flock.homeX(), flock.homeY(), open.open((int) flock.homeX(), (int) flock.homeY()), flock.size());
        }
        int engaged = -1;
        long roofTicks = 0L;
        while (sim.getSimTickIndex() < PLAY_TICKS) {
            int before = sim.getSimTickIndex();
            sim.advance(BattleSimulation.TICK_DT);
            // A decided battle stops ticking: the survey ends with it.
            if (sim.getSimTickIndex() == before) {
                System.out.printf("    [look/flocks] the battle was decided at tick %d%n", before);
                break;
            }
            for (Flock flock : sim.flocks().flocks()) {
                if (engaged < 0 && (flock.task() == FlockTask.STRIKE || flock.task() == FlockTask.PURSUE)) {
                    engaged = sim.getSimTickIndex();
                }
                for (long m : flock.memberIds()) {
                    if (sim.resolveUnit(m) == 0L) continue;
                    if (!open.open(sim.world().cellX(m), sim.world().cellY(m))) roofTicks++;
                }
            }
        }
        System.out.printf("    [look/flocks] first strike or pursuit at tick %d; member-ticks under a roof or in a "
                + "wall over %d ticks: %d%n", engaged, sim.getSimTickIndex(), roofTicks);
        return engaged < 0 ? PLAY_TICKS / 2 : engaged;
    }

    /** {@code flockId} pins the cameras on one flock (0 is the vault zone's, homed at the megastructure's door); -1 follows the busiest. */
    static LookReview.Shot shot(String name, JSONObject fixture, int from, int ticks, float cellsAcross, int flockId) {
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(fixture);
            } catch (Exception e) {
                throw new IllegalStateException("derelict fixture", e);
            }
            while (sim.getSimTickIndex() < from) {
                int before = sim.getSimTickIndex();
                sim.advance(BattleSimulation.TICK_DT);
                if (sim.getSimTickIndex() == before) break;
            }
            return sim;
        }, placement -> List.of(flockView("flock", cellsAcross, flockId), flockView("wide", 120f, flockId)), ticks, 3, 4);
    }

    /**
     * A camera on the busiest flock: the one striking or pursuing, else the one with most members up; it holds where it
     * last was once none flies; or, for {@code flockId} 0 and up, on that flock alone.
     */
    static LookReview.View flockView(String name, float cellsAcross, int flockId) {
        float[] last = {Float.NaN, Float.NaN};
        return new LookReview.View(name, sim -> {
            Flock best = null;
            for (Flock flock : sim.flocks().flocks()) {
                if (flock.size() == 0) continue;
                if (flockId >= 0) {
                    if (flock.id() == flockId) best = flock;
                    continue;
                }
                boolean busy = flock.task() == FlockTask.STRIKE || flock.task() == FlockTask.PURSUE;
                boolean bestBusy = best != null
                        && (best.task() == FlockTask.STRIKE || best.task() == FlockTask.PURSUE);
                if (best == null || (busy && !bestBusy) || (busy == bestBusy && flock.size() > best.size())) {
                    best = flock;
                }
            }
            if (best != null) {
                float sx = 0f, sy = 0f;
                int n = 0;
                for (long m : best.memberIds()) {
                    if (!sim.world().isAlive(m)) continue;
                    sx += sim.world().renderX(m);
                    sy += sim.world().renderY(m);
                    n++;
                }
                if (n > 0) {
                    last[0] = sx / n;
                    last[1] = sy / n;
                }
            }
            if (Float.isNaN(last[0])) {
                return new float[] {sim.getGrid().getWidth() / 2f, sim.getGrid().getHeight() / 2f, cellsAcross};
            }
            return new float[] {last[0], last[1], cellsAcross};
        });
    }
}
