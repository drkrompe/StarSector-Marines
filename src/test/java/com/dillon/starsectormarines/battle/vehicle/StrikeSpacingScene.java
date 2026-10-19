package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.drone.DroneBaySystem;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One drone skimmer's strike drone against a three-rifle picket, at three spacings: does a strike punish a bunched
 * picket and spare a spread one? It is the instrument for the strike drone's blast, separated from the hover line's
 * own scene because that scene asks whether the bay works at all, and this one asks what one blast is worth.
 *
 * <p>The picket stands in a file across the strike's approach, 1, 2 and 3 cells apart. The hull is the real skimmer
 * with its whole bay, so escorts and halo drones shoot as well; what the strike did is read off the detonation tick
 * alone, against durability taken the tick before, with each marine's distance from the blast point. Who is left
 * standing at the end of the loop is a reading, not a verdict.
 *
 * <p><b>The picket is held where it was put, because a stationary picket does not stay put.</b> A squad spawned with
 * no move speed still walks: under the escorts' fire a file three cells apart closed to within two cells of its middle
 * man by the time the strike arrived, and the strike took two of three at every spacing alike. That huddle is a squad
 * behaviour, not the blast, so the measured loops put each marine back on its spot every tick. {@code spread-free}
 * keeps the free picket as the reading of that huddle ({@code driftBeforeStrike}).
 */
public final class StrikeSpacingScene implements BehaviorScene {

    public static final String BUNCHED = "bunched";
    public static final String FILE = "file";
    public static final String SPREAD = "spread";
    /** The spread picket left to its own feet: it closes up under fire, which is the reading. */
    public static final String SPREAD_FREE = "spread-free";

    static final int WIDTH = 64;
    static final int HEIGHT = 48;
    static final float SKIMMER_X = 16.5f, SKIMMER_Y = 24.5f;
    static final int PICKET_X = 34;
    static final int HOLDOUT_X0 = 54;
    /** Ticks played after the strike so the reading includes what the escorts finish. */
    static final int AFTER_STRIKE = 150;
    static final int MAX_TICKS = 900;

    private static final String RIFLE = "weapon.field-rifle";

    private final int frameEvery;

    public StrikeSpacingScene() { this(10); }

    public StrikeSpacingScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "drone-strike-spacing"; }

    @Override public String label() {
        return "Drone strike against a three-rifle picket at 1, 2 and 3 cells' spacing: what one strike drone's blast "
                + "kills, read off the detonation tick";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(picket(BUNCHED, 1, true, frames));
        reports.add(picket(FILE, 2, true, frames));
        reports.add(picket(SPREAD, 3, true, frames));
        reports.add(picket(SPREAD_FREE, 3, false, frames));
        return reports;
    }

    private SceneReport picket(String loop, int spacing, boolean held, FrameSink frames) {
        int y0 = 24 - spacing;
        SceneWorld world = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(HOLDOUT_X0, 40, 62, 40).wall(HOLDOUT_X0, 47, 62, 47)
                .wall(HOLDOUT_X0, 40, HOLDOUT_X0, 47).wall(62, 40, 62, 47)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 58, 44)
                .squad("picket").faction(Faction.DEFENDER).size(3).inFile(PICKET_X, y0, 0, spacing)
                .primary(RIFLE).stationary().done()
                .build();
        HoverLineScene.dirt(world.sim().getTopology());
        BattleSimulation sim = world.sim();
        long hull = sim.convoy().spawn(VehicleType.DRONE_SKIMMER, Faction.MARINE,
                VehicleMission.deployed(SKIMMER_X, SKIMMER_Y));
        sim.convoy().body(hull).teleport(SKIMMER_X, SKIMMER_Y, -90f);
        long[] picket = world.members("picket");
        DroneBaySystem bays = sim.droneBays();

        float[] before = new float[picket.length];
        float[] after = new float[picket.length];
        float[] distance = new float[picket.length];
        float[] at = new float[picket.length * 2];
        int strikeTick = -1;
        int end = MAX_TICKS;
        float[] spawnAt = new float[picket.length * 2];
        for (int i = 0; i < picket.length; i++) {
            spawnAt[2 * i] = sim.world().x(picket[i]);
            spawnAt[2 * i + 1] = sim.world().y(picket[i]);
        }
        float drift = 0f;
        for (int tick = 0; tick < end; tick++) {
            for (int i = 0; i < picket.length; i++) {
                if (!sim.world().isAlive(picket[i])) continue;
                float ox = sim.world().x(picket[i]) - spawnAt[2 * i], oy = sim.world().y(picket[i]) - spawnAt[2 * i + 1];
                if (strikeTick < 0) drift = Math.max(drift, (float) Math.hypot(ox, oy));
                // Held: put back where it was placed, so the blast is measured against the spacing asked for.
                if (held) sim.world().setPos(picket[i], spawnAt[2 * i], spawnAt[2 * i + 1]);
            }
            float[] prior = new float[picket.length];
            float[] priorAt = new float[picket.length * 2];
            for (int i = 0; i < picket.length; i++) {
                prior[i] = durability(sim, picket[i]);
                priorAt[2 * i] = sim.world().x(picket[i]);
                priorAt[2 * i + 1] = sim.world().y(picket[i]);
            }
            int detonatedBefore = bays.strikesDetonated();
            sim.advance(BattleSimulation.TICK_DT);
            frames.advanced(loop, sim, tick);
            if (strikeTick < 0 && bays.strikesDetonated() > detonatedBefore) {
                strikeTick = tick;
                end = Math.min(MAX_TICKS, tick + AFTER_STRIKE);
                for (int i = 0; i < picket.length; i++) {
                    before[i] = prior[i];
                    after[i] = durability(sim, picket[i]);
                    // Where the tick began is where the blast found them.
                    at[2 * i] = priorAt[2 * i];
                    at[2 * i + 1] = priorAt[2 * i + 1];
                    distance[i] = (float) Math.hypot(at[2 * i] - bays.lastStrikeX(), at[2 * i + 1] - bays.lastStrikeY());
                }
            }
            if (tick % frameEvery == 0) {
                frames.frame(loop, sim, tick, String.format(Locale.ROOT, "%s / spacing %d / tick %d / strikes %d",
                        loop, spacing, tick, bays.strikesDetonated()));
            }
        }

        float reach = DroneBaySystem.STRIKE_BLAST_RADIUS + UnitType.MARINE.radius;
        int killedByStrike = 0, inReach = 0, hurtInReach = 0, hurtOutside = 0;
        float strikeDamage = 0f;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < picket.length; i++) {
            float taken = before[i] - after[i];
            strikeDamage += taken;
            if (before[i] > 0f && after[i] <= 0f) killedByStrike++;
            boolean inside = distance[i] <= reach;
            if (inside) {
                inReach++;
                if (taken > 0f) hurtInReach++;
            } else if (taken >= DroneBaySystem.STRIKE_DAMAGE * 0.5f) {
                hurtOutside++;
            }
            rows.append(String.format(Locale.ROOT, "%s%.2f cells: %.0f -> %.0f", i == 0 ? "" : "; ",
                    distance[i], before[i], Math.max(0f, after[i])));
        }
        int deadAtEnd = 0;
        for (long id : picket) if (!sim.world().isAlive(id)) deadAtEnd++;

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("strike-detonated", strikeTick >= 0,
                "the strike drone detonated at tick " + strikeTick));
        verdicts.add(Verdict.of("blast-reach-honoured", strikeTick >= 0 && hurtInReach == inReach && hurtOutside == 0,
                String.format(Locale.ROOT, "reach %.2f cells; %d of %d inside it hurt, %d outside it took a blast's "
                        + "worth (%s)", reach, hurtInReach, inReach, hurtOutside, rows)));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("spacing", spacing);
        metrics.put("held", held ? 1 : 0);
        metrics.put("driftBeforeStrike", drift);
        metrics.put("strikeTick", strikeTick);
        metrics.put("strikeReachCells", reach);
        metrics.put("inStrikeReach", inReach);
        metrics.put("killedByStrike", killedByStrike);
        metrics.put("strikeDamage", strikeDamage);
        for (int i = 0; i < picket.length; i++) metrics.put("distance" + i, distance[i]);
        metrics.put("blastX", bays.lastStrikeX());
        metrics.put("blastY", bays.lastStrikeY());
        for (int i = 0; i < picket.length; i++) {
            metrics.put("x" + i, at[2 * i]);
            metrics.put("y" + i, at[2 * i + 1]);
        }
        metrics.put("picketDeadAtEnd", deadAtEnd);
        return new SceneReport(id(), loop, end, verdicts, metrics);
    }

    private static float durability(BattleSimulation sim, long id) {
        if (!sim.world().isAlive(id)) return 0f;
        return sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
    }
}
