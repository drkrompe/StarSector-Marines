package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The heart's story beats as optional objectives (`heart-story-beats.md`,
 * {@code optional-objectives.md}): each beat that needs a place names the laid
 * piece it is staged in, so a map that could not hold the piece never rolls the
 * beat, and the battle's board publishes {@code heart.objective_<key>_*}.
 *
 * <p>The runtimes read the battle and decide; they never move a unit or spawn
 * one. Beats whose machinery is still to come (the lost column's survivors
 * joining a lane, the ammunition convoy, close air support) are not in the
 * pool yet.
 */
public final class HeartObjectives {

    private HeartObjectives() { }

    /** F1: the lost column's holdout in the market town. */
    public static final String LOST_COLUMN = "lost_column";
    /** F5: the lanes and the company meet at the reservoir yard. */
    public static final String LINKUP = "linkup";
    /** B2: the people still signalling from the village. */
    public static final String VILLAGE = "village";
    /** B3: every gun standing when the battle ends. */
    public static final String GUNS = "guns_hold";
    /** B4: the company over the brook at its own ford. */
    public static final String FORD = "ford";

    /** Seconds the lost column holds out before it falls. */
    static final int HOLDOUT_FALLS_SECONDS = 420;
    /** The barrage line whose opening burns the village. */
    static final int VILLAGE_LINE = 3;
    /** Cells round a laid piece that count as being at it. */
    static final int AT = 2;
    /** Cells round the reservoir yard that count as being in it: the lanes come out at its edges. */
    static final int YARD_MARGIN = 6;
    static final int TICKS_PER_SECOND = 30;

    /** The heart's pool for this form. */
    public static List<OptionalObjective> pool(Form form) {
        if (form == Form.BROAD_FRONT) {
            return List.of(
                    new OptionalObjective(LOST_COLUMN, "The lost column",
                            "A militia column went in before the offensive and is holding a compound at the town's"
                                    + " edge. Reach it before it falls.",
                            Set.of(HeartLayout.HOLDOUT), Set.of(), 3,
                            new Reward(4000, Map.of("supplies", 40), Map.of()), new Cost("heart.satellites", 1),
                            ctx -> reach(ctx, HeartLayout.HOLDOUT, HOLDOUT_FALLS_SECONDS * TICKS_PER_SECOND)),
                    new OptionalObjective(LINKUP, "Linkup at the workings",
                            "Meet an allied lane at the reservoir yard, for the last push on the mothers.",
                            Set.of(HeartLayout.RESERVOIR_YARD), Set.of(), 2,
                            new Reward(3000, Map.of(), Map.of()), Cost.NONE, ctx -> linkup(ctx)));
        }
        return List.of(
                new OptionalObjective(VILLAGE, "The village still has people",
                        "Someone in the village is signalling. Reach the house before the barrage's third line"
                                + " opens on it.",
                        Set.of(HeartLayout.VILLAGE_HOLDOUT), Set.of(), 3,
                        new Reward(0, Map.of(), Map.of("regard", 1)), Cost.NONE, ctx -> village(ctx)),
                new OptionalObjective(GUNS, "They go for the guns",
                        "Keep every gun in the battery standing to the end.",
                        Set.of(HeartLayout.GUN_FLANK + 1), Set.of(), 2,
                        new Reward(3000, Map.of(), Map.of()), new Cost("heart.battery", 1), ctx -> guns(ctx)),
                new OptionalObjective(FORD, "The ford",
                        "The barrage lifts past the brook. Take the company over at its own ford.",
                        Set.of(HeartLayout.COMPANY_FORD), Set.of(), 2,
                        new Reward(2000, Map.of(), Map.of()), Cost.NONE,
                        ctx -> reach(ctx, HeartLayout.COMPANY_FORD, Integer.MAX_VALUE)));
    }

    /** How many beats a battle runs, least and most: denser country holds more set pieces. */
    public static int[] count(Density density) {
        return density == Density.RURAL ? new int[]{1, 2} : new int[]{2, 3};
    }

    /** The inclusive bounds {x0, y0, x1, y1} of a laid piece grown by {@code margin}, or null when it was not laid. */
    static int[] bounds(LaidLayout laid, String name, int margin) {
        Laid piece = laid.find(name);
        if (piece == null || !piece.isLaid() || piece.cells().isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] c : piece.cells()) {
            x0 = Math.min(x0, c[0]); y0 = Math.min(y0, c[1]);
            x1 = Math.max(x1, c[0]); y1 = Math.max(y1, c[1]);
        }
        return new int[]{x0 - margin, y0 - margin, x1 + margin, y1 + margin};
    }

    /** Whether a living unit of {@code faction}, on its feet, stands inside the bounds. */
    static boolean present(BattleSimulation sim, Faction faction, int[] b) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != faction || sim.isRiding(unit)) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            if (x >= b[0] && x <= b[2] + 1 && y >= b[1] && y <= b[3] + 1) return true;
        }
        return false;
    }

    /** The company reaches the piece by {@code deadline}: succeeded when it does, failed after. */
    static ObjectiveRuntime reach(ObjectiveContext ctx, String piece, int deadline) {
        int[] b = bounds(ctx.laid(), piece, AT);
        return (c, tick) -> {
            if (b == null) return Status.LAPSED;
            if (present(c.sim(), Faction.MARINE, b)) return Status.SUCCEEDED;
            return tick >= deadline ? Status.FAILED : Status.ACTIVE;
        };
    }

    /** F5: the company and an allied lane in the reservoir yard together. */
    static ObjectiveRuntime linkup(ObjectiveContext ctx) {
        int[] b = bounds(ctx.laid(), HeartLayout.RESERVOIR_YARD, YARD_MARGIN);
        return (c, tick) -> {
            if (b == null) return Status.LAPSED;
            return present(c.sim(), Faction.MARINE, b) && present(c.sim(), Faction.ALLY, b)
                    ? Status.SUCCEEDED : Status.ACTIVE;
        };
    }

    /** B2: the company at the village holdout before the barrage's third line opens on it. */
    static ObjectiveRuntime village(ObjectiveContext ctx) {
        int[] b = bounds(ctx.laid(), HeartLayout.VILLAGE_HOLDOUT, AT);
        return (c, tick) -> {
            HeartSequence heart = c.sim().getHeart();
            WalkingBarrage barrage = heart == null ? null : heart.barrage();
            if (b == null || barrage == null) return Status.LAPSED;
            if (present(c.sim(), Faction.MARINE, b)) return Status.SUCCEEDED;
            return barrage.firingLine() >= VILLAGE_LINE ? Status.FAILED : Status.ACTIVE;
        };
    }

    /** B3: every gun of the battery still standing; failed with the first one lost. */
    static ObjectiveRuntime guns(ObjectiveContext ctx) {
        return new ObjectiveRuntime() {
            @Override public Status advance(ObjectiveContext c, int tick) {
                long[] guns = battery(c);
                if (guns == null) return Status.LAPSED;
                for (long gun : guns) if (!standing(c.sim(), gun)) return Status.FAILED;
                return Status.ACTIVE;
            }

            @Override public Status end(ObjectiveContext c) {
                long[] guns = battery(c);
                if (guns == null) return Status.LAPSED;
                for (long gun : guns) if (!standing(c.sim(), gun)) return Status.FAILED;
                return Status.SUCCEEDED;
            }
        };
    }

    private static long[] battery(ObjectiveContext c) {
        HeartSequence heart = c.sim().getHeart();
        WalkingBarrage barrage = heart == null ? null : heart.barrage();
        long[] guns = barrage == null ? new long[0] : barrage.guns();
        return guns.length == 0 ? null : guns;
    }

    private static boolean standing(BattleSimulation sim, long gun) {
        return sim.resolveUnit(gun) != 0L && sim.world().isAlive(gun);
    }
}
