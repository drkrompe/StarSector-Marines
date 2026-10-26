package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierRules;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The heart's story beats as optional objectives (`heart-story-beats.md`,
 * {@code optional-objectives.md}): each beat that needs a place names the laid
 * piece it is staged in, so a map that could not hold the piece never rolls the
 * beat, and the battle's board publishes {@code heart.objective_<key>_*}.
 *
 * <p>The runtimes read the battle and decide; they never order the company.
 * Some stage what their beat needs: the guns' and the spores' beats dig a flak
 * position for the company and raise Harrier packs, which fly only under that
 * cover (`colony-breach-nouns.md`, Harrier), and the ammunition beat sends its
 * trucks up through the shared supply delivery (`allied-tools.md`). The lost
 * column's survivors joining a lane wait on allied forces.
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

    /** F2: the company into a broken lane's ground. */
    public static final String PLUG = "plug_the_gap";
    /** F3: the spore colonies over the company's approach burnt out. */
    public static final String AIR = "air_corridor";
    /** F4: the first Matriarch out of the heart killed. */
    public static final String MATRIARCH = "matriarch_down";
    /** F6: the satellite against the supply road taken. */
    public static final String ROAD = "road_open";
    /** B1: the company never close enough to stop a gun. */
    public static final String CLEAR = "clear_of_the_walk";
    /** B5: an ammunition truck through to the guns, for one more line. */
    public static final String AMMUNITION = "one_more_line";
    /** B6: every mother down in the quiet after the last line. */
    public static final String LAST = "last_stretch";

    /** Seconds the company has to get into a broken lane's ground. */
    static final int PLUG_SECONDS = 90;
    /** Seconds a Matriarch may live once she is out. */
    static final int MATRIARCH_SECONDS = 150;
    /** Seconds after the barrage ends for every mother to fall. */
    static final int LAST_STRETCH_SECONDS = 240;
    /** The flak position the air beats dig for the company. */
    static final String FLAK = "structure.turret-anti-air-flak";
    /** A beat that would dig flak this near a flak position already standing uses that one. */
    static final float FLAK_REUSE = 6f;
    /** Harriers in a pack the beats raise. */
    static final int PACK = 5;
    /** How far from the guns the breakout's pack rises: just inside a Harrier's sense. */
    static final float PACK_STANDOFF = HarrierRules.SENSE_RANGE - 4f;
    /** Seconds between packs off the approach's spore colonies, and how many they may raise. */
    static final int SPORE_PACK_SECONDS = 120, SPORE_PACKS = 3;
    /** Satellites whose colonies stand over the company's approach. */
    static final int APPROACH_SATELLITES = 2;
    /** The barrage line whose opening sends a pack out along the flank toward the guns. */
    static final int BREAKOUT_LINE = 2;

    /** The barrage line whose opening sends the ammunition trucks up. */
    static final int AMMUNITION_LINE = 3;
    /** Trucks in the ammunition run, and ticks between them leaving the park. */
    static final int TRUCKS = 2, TRUCK_GAP_TICKS = 300;
    /** The extra line: as deep past the last as the Burn's plan steps, and as long as one of its lines fires. */
    static final float EXTRA_LINE_STEP = 0.06f, EXTRA_LINE_SECONDS = 40f;

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
                            new Reward(3000, Map.of(), Map.of()), Cost.NONE, ctx -> linkup(ctx)),
                    new OptionalObjective(PLUG, "A lane breaks",
                            "If the surge breaks an allied lane, get into its ground before the hole opens wide.",
                            Set.of(), Set.of(), 2,
                            new Reward(2500, Map.of(), Map.of()), Cost.NONE, ctx -> plug(ctx)),
                    new OptionalObjective(AIR, "Spores ground the air",
                            "The spore colonies over your approach launch Harriers. The dump's flak can answer"
                                    + " them; burn the colonies out.",
                            Set.of(HeartLayout.AMMO_DUMP + 1), Set.of(), 2,
                            new Reward(2500, Map.of(), Map.of()), Cost.NONE, ctx -> air(ctx)),
                    new OptionalObjective(MATRIARCH, "The Matriarch's charge",
                            "The first Matriarch out of a mother goes for the armour. Kill her before she is through.",
                            Set.of(), Set.of(), 2,
                            new Reward(3000, Map.of(), Map.of()), Cost.NONE, ctx -> matriarch(ctx)),
                    new OptionalObjective(ROAD, "The supply road",
                            "A satellite beside the supply road keeps cutting it. Take it.",
                            Set.of(HeartLayout.SUPPLY_ROAD), Set.of(), 2,
                            new Reward(2500, Map.of(), Map.of()), Cost.NONE, ctx -> road(ctx)));
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
                        ctx -> reach(ctx, HeartLayout.COMPANY_FORD, Integer.MAX_VALUE)),
                new OptionalObjective(CLEAR, "Clear of the walk",
                        "Follow the barrage close, but never so close that a gun holds its fire for you.",
                        Set.of(), Set.of(), 2,
                        new Reward(2000, Map.of(), Map.of()), Cost.NONE, ctx -> clear(ctx)),
                new OptionalObjective(AMMUNITION, "One more line",
                        "The battery is low. Ammunition trucks come up from the vehicle park when the third line"
                                + " opens; if one reaches the guns before the last line lifts, the plan gains one"
                                + " more line.",
                        Set.of(HeartLayout.VEHICLE_PARK), Set.of(), 2,
                        new Reward(2000, Map.of(), Map.of()), Cost.NONE, ctx -> ammunition(ctx)),
                new OptionalObjective(LAST, "The guns fall silent",
                        "When the last line lifts, finish the mothers before the swarm regrows.",
                        Set.of(), Set.of(), 2,
                        new Reward(3000, Map.of(), Map.of()), Cost.NONE, ctx -> last(ctx)));
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

    /**
     * B3: every gun of the battery still standing; failed with the first one
     * lost. A flak position goes in among the guns, and when the barrage's
     * second line opens a Harrier pack rises off the flank toward them, on the
     * side of the nearest living bore.
     */
    static ObjectiveRuntime guns(ObjectiveContext ctx) {
        long[] dug = battery(ctx);
        float[] middle = dug == null ? null : centroid(ctx.sim(), dug);
        if (middle != null) stocked(ctx, HeartGuns.BATTERY_FLAK, HeartGuns.FLAK_ROUNDS, new int[]{(int) middle[0], (int) middle[1]},
                digFlak(ctx.sim(), "battery-flak", (int) middle[0], (int) middle[1]));
        return new ObjectiveRuntime() {
            boolean released;

            @Override public Status advance(ObjectiveContext c, int tick) {
                long[] guns = battery(c);
                if (guns == null) return Status.LAPSED;
                if (!released && middle != null && c.hasBeat("barrage_line_" + BREAKOUT_LINE)) {
                    released = true;
                    SwarmBore bore = nearestBore(c.sim(), middle[0], middle[1]);
                    if (bore != null) {
                        float dx = bore.x() - middle[0], dy = bore.y() - middle[1];
                        float d = (float) Math.max(1.0, Math.sqrt(dx * dx + dy * dy)), r = Math.min(d, PACK_STANDOFF);
                        c.publish(tick, raisePack(c.sim(), bore, middle[0] + dx / d * r, middle[1] + dy / d * r)
                                ? "battery_threatened" : "harriers_grounded");
                    }
                }
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

    private static boolean standing(BattleSimulation sim, long unit) {
        return sim.resolveUnit(unit) != 0L && sim.world().isAlive(unit);
    }

    /** F2: once a lane breaks, the company in that lane's ground within {@link #PLUG_SECONDS}; lapses if none breaks. */
    static ObjectiveRuntime plug(ObjectiveContext ctx) {
        return new ObjectiveRuntime() {
            HeartLane broken;
            int deadline;

            @Override public Status advance(ObjectiveContext c, int tick) {
                HeartSequence heart = c.sim().getHeart();
                HeartLanes lanes = heart == null ? null : heart.lanes();
                if (lanes == null) return Status.LAPSED;
                if (broken == null) {
                    List<HeartLane> grounds = lanes.grounds();
                    for (int n = 1; n <= grounds.size() && broken == null; n++) {
                        if (c.hasBeat("lane_broken_" + n)) broken = grounds.get(n - 1);
                    }
                    if (broken == null) return Status.ACTIVE;
                    deadline = tick + PLUG_SECONDS * TICKS_PER_SECOND;
                }
                if (inLane(c.sim(), broken)) return Status.SUCCEEDED;
                return tick >= deadline ? Status.FAILED : Status.ACTIVE;
            }
        };
    }

    /** Whether a marine on its feet stands in a lane's ground. */
    static boolean inLane(BattleSimulation sim, HeartLane lane) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.isRiding(unit)
                    || sim.identity().type(unit).isTurret()) continue;
            float across = lane.advancesAlongY() ? sim.world().x(unit) : sim.world().y(unit);
            if (Math.abs(across - lane.across()) <= lane.halfWidth()) return true;
        }
        return false;
    }

    /**
     * F3: the spore colonies of the satellites nearest the company burnt out.
     * The ammunition dump digs a flak position, and while a target satellite's
     * colonies stand, one of them raises a Harrier pack whenever the company or
     * an ally comes within a Harrier's sense of it, at most {@link #SPORE_PACKS}
     * and one per {@link #SPORE_PACK_SECONDS}.
     */
    static ObjectiveRuntime air(ObjectiveContext ctx) {
        int[] dump = bounds(ctx.laid(), HeartLayout.AMMO_DUMP + 1, 0);
        if (dump != null) {
            int[] at = {(dump[0] + dump[2]) / 2, (dump[1] + dump[3]) / 2};
            stocked(ctx, HeartGuns.DUMP, HeartGuns.FLAK_ROUNDS, at, digFlak(ctx.sim(), "dump-flak", at[0], at[1]));
        }
        return new ObjectiveRuntime() {
            List<SwarmBore> targets;
            int packs, nextPack;

            @Override public Status advance(ObjectiveContext c, int tick) {
                if (dump == null) return Status.LAPSED;
                BattleSimulation sim = c.sim();
                if (targets == null) {
                    List<SwarmBore> near = approachSatellites(sim);
                    if (near.isEmpty()) return Status.ACTIVE;
                    targets = near;
                }
                boolean standing = false, sprouted = false;
                for (SwarmBore bore : targets) {
                    if (bore.collapsed()) { sprouted = true; continue; }
                    for (long colony : bore.colonies()) {
                        sprouted = true;
                        if (!standing(sim, colony)) continue;
                        standing = true;
                        if (packs < SPORE_PACKS && tick >= nextPack && preyNear(sim, colony)) {
                            if (raisePack(sim, sim.world().x(colony), sim.world().y(colony))) {
                                packs++;
                                nextPack = tick + SPORE_PACK_SECONDS * TICKS_PER_SECOND;
                                c.publish(tick, "harrier_pack_" + packs);
                            } else {
                                // The colony would launch, but nothing on the company's side can answer a flier.
                                c.publish(tick, "harriers_grounded");
                            }
                        }
                    }
                }
                if (sprouted && !standing) {
                    c.publish(tick, "air_corridor_open");
                    return Status.SUCCEEDED;
                }
                return Status.ACTIVE;
            }
        };
    }

    /** The satellites standing nearest the company, nearest first. */
    static List<SwarmBore> approachSatellites(BattleSimulation sim) {
        float[] company = companyCentre(sim);
        List<SwarmBore> satellites = new ArrayList<>();
        if (company == null) return satellites;
        for (SwarmBore bore : sim.swarmBores().bores()) if (!bore.mother() && !bore.collapsed()) satellites.add(bore);
        satellites.sort((a, b) -> Float.compare(distance2(a, company[0], company[1]), distance2(b, company[0], company[1])));
        return new ArrayList<>(satellites.subList(0, Math.min(APPROACH_SATELLITES, satellites.size())));
    }

    /** Whether a marine or an ally stands within a Harrier's sense of the unit. */
    private static boolean preyNear(BattleSimulation sim, long of) {
        float x = sim.world().x(of), y = sim.world().y(of), reach = HarrierRules.SENSE_RANGE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction f = sim.identity().faction(unit);
            if (f != Faction.MARINE && f != Faction.ALLY) continue;
            float dx = sim.world().x(unit) - x, dy = sim.world().y(unit) - y;
            if (dx * dx + dy * dy <= reach * reach) return true;
        }
        return false;
    }

    /** F4: every Matriarch out at the first sighting killed within {@link #MATRIARCH_SECONDS}; lapses if none comes. */
    static ObjectiveRuntime matriarch(ObjectiveContext ctx) {
        return new ObjectiveRuntime() {
            List<Long> marked;
            int deadline;

            @Override public Status advance(ObjectiveContext c, int tick) {
                BattleSimulation sim = c.sim();
                if (marked == null) {
                    List<Long> out = new ArrayList<>();
                    for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.identity().type(unit) == UnitType.SWARM_MATRIARCH && sim.world().isAlive(unit)) out.add(unit);
                    }
                    if (out.isEmpty()) return Status.ACTIVE;
                    marked = out;
                    deadline = tick + MATRIARCH_SECONDS * TICKS_PER_SECOND;
                }
                for (long unit : marked) {
                    if (standing(sim, unit)) return tick >= deadline ? Status.FAILED : Status.ACTIVE;
                }
                return Status.SUCCEEDED;
            }
        };
    }

    /** F6: the satellite nearest the supply road collapsed by the battle's end. */
    static ObjectiveRuntime road(ObjectiveContext ctx) {
        Laid road = ctx.laid().find(HeartLayout.SUPPLY_ROAD);
        List<int[]> cells = road == null || !road.isLaid() ? List.of() : road.cells();
        return new ObjectiveRuntime() {
            SwarmBore cutter;

            @Override public Status advance(ObjectiveContext c, int tick) {
                if (cells.isEmpty()) return Status.LAPSED;
                if (cutter == null) cutter = nearestToRoad(c.sim(), cells);
                if (cutter == null || !cutter.collapsed()) return Status.ACTIVE;
                c.publish(tick, "road_open");
                return Status.SUCCEEDED;
            }

            @Override public Status end(ObjectiveContext c) {
                return cells.isEmpty() ? Status.LAPSED : Status.FAILED;
            }
        };
    }

    /** The standing satellite nearest any cell of the road, or null. */
    static SwarmBore nearestToRoad(BattleSimulation sim, List<int[]> road) {
        SwarmBore best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.mother() || bore.collapsed()) continue;
            for (int[] cell : road) {
                float d = distance2(bore, cell[0], cell[1]);
                if (d < bestDistance) { bestDistance = d; best = bore; }
            }
        }
        return best;
    }

    /**
     * B1: no gun ever held its fire for the company, to the barrage's end. A round held for an allied lane's body is the
     * lane's, not the company's: Burn town 3's only held round was for a militia technical a lane had driven under the
     * second line (2026-10-26).
     */
    static ObjectiveRuntime clear(ObjectiveContext ctx) {
        return (c, tick) -> {
            HeartSequence heart = c.sim().getHeart();
            WalkingBarrage barrage = heart == null ? null : heart.barrage();
            if (barrage == null) return Status.LAPSED;
            if (barrage.vetoedForSide() > 0) return Status.FAILED;
            return c.hasBeat("barrage_ended") ? Status.SUCCEEDED : Status.ACTIVE;
        };
    }

    /**
     * B5: when the barrage's third line opens, {@link #TRUCKS} ammunition trucks are sent from the vehicle park's tool
     * shed ({@link HeartGuns#send}: every truck comes from the one producer, waiting while it has none in stock or the
     * swarm is on the park) for the battery. The first through adds one more line to the plan, if the last has not
     * lifted, and refills the battery's rounds; every truck lost, or the battle ending first, fails it.
     */
    static ObjectiveRuntime ammunition(ObjectiveContext ctx) {
        return new ObjectiveRuntime() {
            int sentAt = -1;
            int sent;
            boolean arrived;
            Status outcome;

            @Override public Status advance(ObjectiveContext c, int tick) {
                if (outcome != null) return outcome;
                HeartSequence heart = c.sim().getHeart();
                WalkingBarrage barrage = heart == null ? null : heart.barrage();
                HeartGuns guns = heart == null ? null : heart.guns();
                if (barrage == null || guns == null || !guns.sends()) return Status.LAPSED;
                if (sentAt < 0) {
                    if (!c.hasBeat("barrage_line_" + AMMUNITION_LINE)) return Status.ACTIVE;
                    sentAt = tick;
                }
                if (arrived) {
                    boolean added = barrage.extend(EXTRA_LINE_STEP, EXTRA_LINE_SECONDS);
                    c.publish(tick, added ? "ammunition_up" : "ammunition_late");
                    outcome = added ? Status.SUCCEEDED : Status.FAILED;
                    return outcome;
                }
                if (sent < TRUCKS && tick >= sentAt + sent * TRUCK_GAP_TICKS
                        && guns.send(c.sim(), errand(sent + 1), battery(barrage, guns, () -> arrived = true), "one more line")) {
                    sent++;
                }
                if (sent == TRUCKS && onRoad(guns) == 0) {
                    c.publish(tick, "ammunition_lost");
                    outcome = Status.FAILED;
                    return outcome;
                }
                return Status.ACTIVE;
            }

            private int onRoad(HeartGuns guns) {
                int n = 0;
                for (int i = 1; i <= sent; i++) n += guns.onRoad(errand(i));
                return n;
            }

            @Override public Status end(ObjectiveContext c) {
                if (outcome != null) return outcome;
                return sentAt < 0 ? Status.LAPSED : Status.FAILED;
            }
        };
    }

    /** The place B5's {@code n}th truck is sent under. */
    static String errand(int n) {
        return "one-more-line-" + n;
    }

    /**
     * The battery as a delivery's recipient: the middle of its standing guns, gone once none stands. Arrival refills
     * the battery's rounds, as any ammunition truck's does, and tells the beat through {@code arrived}.
     */
    private static SupplyDelivery.Recipient battery(WalkingBarrage barrage, HeartGuns guns, Runnable arrived) {
        return new SupplyDelivery.Recipient() {
            @Override public String name() { return "battery"; }

            @Override public int[] where(BattleSimulation sim) {
                float[] middle = centroid(sim, barrage.guns());
                return middle == null ? null : new int[]{(int) middle[0], (int) middle[1]};
            }

            @Override public void arrived(BattleSimulation sim) {
                GunStock stock = guns.stock(HeartGuns.BATTERY);
                if (stock != null) stock.refill(sim);
                arrived.run();
            }
        };
    }

    /** B6: every mother collapsed within {@link #LAST_STRETCH_SECONDS} of the barrage's end; lapses if it never ends. */
    static ObjectiveRuntime last(ObjectiveContext ctx) {
        return new ObjectiveRuntime() {
            int deadline = -1;

            @Override public Status advance(ObjectiveContext c, int tick) {
                if (deadline < 0) {
                    if (!c.hasBeat("barrage_ended")) return Status.ACTIVE;
                    deadline = tick + LAST_STRETCH_SECONDS * TICKS_PER_SECOND;
                }
                for (SwarmBore bore : c.sim().swarmBores().bores()) {
                    if (bore.mother() && !bore.collapsed()) return tick >= deadline ? Status.FAILED : Status.ACTIVE;
                }
                return Status.SUCCEEDED;
            }
        };
    }

    /** The living bore nearest {@code (x, y)}, or null. */
    static SwarmBore nearestBore(BattleSimulation sim, float x, float y) {
        SwarmBore best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            float d = distance2(bore, x, y);
            if (d < bestDistance) { bestDistance = d; best = bore; }
        }
        return best;
    }

    /**
     * Raises a pack of Harriers in the default look at {@code (x, y)} when the
     * company's side has anti-air to answer it, and reports whether it did: a
     * pack never flies over a map that cannot shoot back.
     */
    static boolean raisePack(BattleSimulation sim, float x, float y) {
        if (!SwarmFliers.hasAirCover(sim)) return false;
        sim.swarmFliers().releaseUnderCover(sim, HarrierLook.DEFAULT, x, y, PACK, 0L);
        return true;
    }

    /** As {@link #raisePack(BattleSimulation, float, float)}, the pack hatching out of {@code bore}'s mouth and flying out to {@code (x, y)}. */
    static boolean raisePack(BattleSimulation sim, SwarmBore bore, float x, float y) {
        if (!SwarmFliers.hasAirCover(sim)) return false;
        sim.swarmFliers().hatchUnderCover(sim, HarrierLook.DEFAULT, bore.entityId(), bore.x() + 0.5f, bore.y() + 0.5f,
                x, y, PACK, 0L);
        return true;
    }

    /** Digs the company a flak position on the open cell nearest {@code (x, y)}; 0 when there is none within reach. */
    /**
     * Puts a gun a beat dug on the heart's stock at {@code place}, laying the place where it is new, so it spends
     * rounds and calls the vehicle park's trucks ({@link HeartGuns}).
     */
    static void stocked(ObjectiveContext ctx, String place, int rounds, int[] cell, long gun) {
        HeartSequence heart = ctx.sim().getHeart();
        if (heart == null || heart.guns() == null || gun == 0L) return;
        heart.guns().place(place, rounds, cell).add(place, gun);
    }

    static long digFlak(BattleSimulation sim, String name, int x, int y) {
        long standing = flakNear(sim, x, y);
        if (standing != 0L) return standing;
        int[] cell = openCellNear(sim, x, y, 8);
        return cell == null ? 0L : sim.spawn(MapTurret.create(name, Faction.MARINE, FLAK, cell[0], cell[1]));
    }

    /** The company's flak position already dug within {@link #FLAK_REUSE} of {@code (x, y)}, or 0. */
    private static long flakNear(BattleSimulation sim, int x, int y) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) != Faction.MARINE || !sim.turretState().isTurret(u)
                    || !FLAK.equals(sim.turretState().structure(u).id)) continue;
            float dx = sim.world().x(u) - x, dy = sim.world().y(u) - y;
            if (dx * dx + dy * dy <= FLAK_REUSE * FLAK_REUSE) return u;
        }
        return 0L;
    }

    /**
     * The logistics base's air defence (`heart-nouns.md`, Logistics base): a flak position at the first ammunition
     * dump and one at the vehicle park, and on The Burn one among the battery ({@code battery} its centre, or
     * {@code null}), each on the guns' stock so it spends rounds and calls trucks. It is what lets the flier law be
     * met over the heart: with no anti-air standing, no brood spire launches and no brood rises
     * (`colony-breach-nouns.md`, Brood spire). The beats that dig flak at the dump and the battery (F3, B3) find
     * these rather than digging a second.
     */
    public static void airDefence(BattleSimulation sim, LaidLayout laid, HeartGuns guns, int[] battery) {
        int[] dump = bounds(laid, HeartLayout.AMMO_DUMP + 1, 0);
        if (dump != null) baseFlak(sim, guns, HeartGuns.DUMP, "dump-flak", (dump[0] + dump[2]) / 2, (dump[1] + dump[3]) / 2);
        int[] park = bounds(laid, HeartLayout.VEHICLE_PARK, 0);
        if (park != null) baseFlak(sim, guns, HeartGuns.PARK_FLAK, "park-flak", (park[0] + park[2]) / 2, (park[1] + park[3]) / 2);
        if (battery != null) baseFlak(sim, guns, HeartGuns.BATTERY_FLAK, "battery-flak", battery[0], battery[1]);
    }

    private static void baseFlak(BattleSimulation sim, HeartGuns guns, String place, String name, int x, int y) {
        long gun = digFlak(sim, name, x, y);
        if (gun == 0L) return;
        guns.place(place, HeartGuns.FLAK_ROUNDS, new int[]{x, y}).add(place, gun);
    }

    /** The walkable open cell nearest {@code (x0, y0)}: no building, wall, door or prop. */
    static int[] openCellNear(BattleSimulation sim, int x0, int y0, int radius) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (int r = 0; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                int x = x0 + dx, y = y0 + dy;
                if (x < 1 || y < 1 || x >= grid.getWidth() - 1 || y >= grid.getHeight() - 1) continue;
                if (!grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.isFixture(x, y) || topology.isWall(x, y)) continue;
                return new int[]{x, y};
            }
        }
        return null;
    }

    /** The middle of the living units given, or null when none lives. */
    private static float[] centroid(BattleSimulation sim, long[] units) {
        float sx = 0, sy = 0;
        int n = 0;
        for (long unit : units) {
            if (!standing(sim, unit)) continue;
            sx += sim.world().x(unit); sy += sim.world().y(unit); n++;
        }
        return n == 0 ? null : new float[]{sx / n, sy / n};
    }

    /** The middle of the company's people, or null before any is on the map. */
    static float[] companyCentre(BattleSimulation sim) {
        float sx = 0, sy = 0;
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit).isTurret()) continue;
            sx += sim.world().x(unit); sy += sim.world().y(unit); n++;
        }
        return n == 0 ? null : new float[]{sx / n, sy / n};
    }

    private static float distance2(SwarmBore bore, float x, float y) {
        float dx = bore.x() - x, dy = bore.y() - y;
        return dx * dx + dy * dy;
    }
}
