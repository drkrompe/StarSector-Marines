package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.MapEdge;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelHelm;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The duel over Black Sluice ({@code warship-behaviours.md}, ship against ship). The <i>Greyhook</i>, Voss's Venture,
 * sits landed on the berths south of the ridge with her two Hound (P) escorts from the start, quiet: they fire at
 * nothing on the ground, and the battle's hull fire is held until Voss's withdrawal beat ({@link HullFireHold#untilBeat}).
 * When Voss orders the site abandoned, the <i>Greyhook</i> lifts and fights a withdrawal: she flies a wide circuit
 * over the basin, turns her broadside on the Wolfs when they come, and her Hounds screen her; and two of Rusk's Wolf
 * (H) fly in across the west edge, already under way on a heading east, on a visit nobody can command. She climbs
 * as she fights ({@link #climb}): slowly while a Wolf has her in reach, faster with a Hound left to screen her, at
 * her best while nothing is shooting. She breaks for her edge only once she has made her height, or when she is
 * crippled, or when nothing is left to stop her, and then fights her way east and off the map; her escape is the
 * fight's to give, not a clock's. Nothing warps in or out: each side has one approved edge ({@link #WOLF_EDGE},
 * {@link #GREYHOOK_EDGE}), every hull that comes or goes crosses it, and no area is declared for the fight, which goes
 * where the fighting takes it, over the ground battle included, kept off the map's edges only by the standing edge
 * pressure every warship flies with. It stays gun against gun: the Venture's
 * fitting carries no wing, and a warship over a ground battle never launches one from its bay. Every hull fights
 * hulls by the ordinary default; no hull of the duel ever fires at the ground. Nothing is scripted after the order:
 * who escapes, breaks off or falls is the ships' own doing, and the debrief reads it from the
 * {@link WarshipDuelLedger}.
 *
 * <p>The ground referee reads nothing here. Stray rounds and falling wreckage are real on the ground, which is the
 * one way the duel can touch the fight below.
 */
public final class BlackSluiceDuel implements SmallWarDuel {

    private static final Logger LOG = Global.getLogger(BlackSluiceDuel.class);

    /** The Venture and her fitting: guns only, no wing. */
    static final String GREYHOOK_HULL = "venture", GREYHOOK_VARIANT = "venture_Greyhook";
    /** Rusk's Wolfs and their fitting. */
    static final String WOLF_HULL = "wolf_hegemony", WOLF_VARIANT = "wolf_hegemony_Rusk";
    /**
     * Where the Greyhook and her escorts sit: landed on the southern pad field at true scale
     * ({@code landed-ships-true-scale.md}), the Greyhook in the cradle and a Hound in each berth beside it, noses
     * west. They lift when Voss orders the withdrawal and fly their lift runs, their bodies easing to flight scale as
     * they go.
     */
    static final float RIG_X = BlackSluice.VENTURE_CRADLE.x(), RIG_Y = BlackSluice.VENTURE_CRADLE.y();
    static final List<BlackSluice.Berth> HOUND_BERTHS = BlackSluice.HOUND_BERTHS;
    /**
     * The Wolfs' edge: west, the side the company landed on and the far side of the basin from the pads, so their
     * whole approach is in sight before a gun bears. At flight presence a pulse laser reaches about eighty cells;
     * dropped seventy-five cells east of the pads they were on the eastern Hound as it lifted, and both Hounds fell
     * within fifteen seconds of the order.
     */
    public static final MapEdge WOLF_EDGE = MapEdge.WEST;
    /**
     * The Greyhook side's edge: east. It is her side's ground (dispatch, the berth yard and its gate, the berth road
     * down the east side, the service road Drent's crews leave by) and it lies away from the Wolfs, so leaving means
     * fighting across the basin. South would be no withdrawal at all: the cradle sits a few cells off the south edge,
     * inside her own edge margin, and she would be gone before the Wolfs were over the basin.
     */
    public static final MapEdge GREYHOOK_EDGE = MapEdge.EAST;
    /**
     * Where along the west edge the Wolfs fly in: either side of a line a little north of the pads, clear of the
     * south edge's margin, so they come straight on across the basin rather than turning off the edge.
     */
    static final float[] WOLF_ENTRY_ALONG = {78f + 14f, 78f - 14f};
    /** The Wolfs' entry heading: due east, in the hull-facing convention. */
    static final float WOLF_HEADING = 270f;
    /**
     * The Greyhook's climbing circuit, flown whenever no Wolf is hers to fight: an ellipse over the middle of the map,
     * clear of her edge margin, flown so that from the cradle she first heads west along its southern side, toward the
     * Wolfs. Its northern side passes over the ground fight.
     */
    static final float CIRCUIT_X = 0.5f * BlackSluice.WIDTH, CIRCUIT_Y = 98f,
            CIRCUIT_RX = 60f, CIRCUIT_RY = 22f;
    /** How far round the circuit, in degrees, her station is kept ahead of her. */
    static final float CIRCUIT_LEAD_DEGREES = 35f;
    /** Seconds of steady climbing, with nothing shooting at her, before the Greyhook has made her height. */
    static final float CLIMB_SECONDS = 80f;
    /** The share of that rate she keeps while a Wolf has her in reach and a Hound is still screening her. */
    static final float CLIMB_SCREENED = 0.5f;
    /** The share she keeps while a Wolf has her in reach and no Hound is left to screen her. */
    static final float CLIMB_ALONE = 0.25f;
    /** Below this share of her hull the Greyhook stops fighting for height and runs for her edge as she is. */
    static final float CRIPPLED_HULL = 0.35f;
    /** A Wolf this many times its own reach from a Greyhook hull is closing: the radio says so. */
    static final float CLOSING_REACH_SHARE = 1.25f;

    /** Why the Greyhook broke for her edge. */
    public enum BreakOff { NONE, CLIMBED, CRIPPLED, UNOPPOSED }

    /** The story beats the duel publishes for the radio. */
    public static final String WOLVES_SIGHTED_BEAT = "small_war.wolves_sighted",
            WOLVES_CLOSING_BEAT = "small_war.wolves_closing",
            FIRST_HULL_FIRE_BEAT = "small_war.first_hull_fire";
    /** Seconds without a hostile hull over the battle before the Wolfs leave. */
    static final float WOLF_DWELL_SECONDS = 10f;

    private Warship rig;
    private final List<Warship> hounds = new ArrayList<>();
    private final List<Warship> wolves = new ArrayList<>();
    private WarshipDuelLedger ledger;
    private boolean ordered;
    private float climb;
    private BreakOff breakOff = BreakOff.NONE;
    private int breakTick = -1;
    private boolean sighted, closing, fired;

    /** Puts the rig and its escorts on their pads, quiet, before the first tick. */
    public void stage(BattleSimulation sim) {
        if (rig != null) throw new IllegalStateException("The duel is staged once");
        try {
            rig = Warship.fit(GREYHOOK_HULL, GREYHOOK_VARIANT, Faction.DEFENDER, RIG_X, RIG_Y,
                    BlackSluice.VENTURE_CRADLE.facing());
            rig.berth(RIG_X, RIG_Y);
            rig.limitFlux();
            rig.groundFire(Warship.GroundFire.HOLD);
            for (BlackSluice.Berth berth : HOUND_BERTHS) {
                Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER,
                        berth.x(), berth.y(), berth.facing());
                hound.berth(berth.x(), berth.y());
                hound.limitFlux();
                hound.groundFire(Warship.GroundFire.HOLD);
                hounds.add(hound);
            }
        } catch (Exception e) {
            // A hull table that cannot be read leaves the chapter without a duel, never without its ground fight.
            LOG.warn("Black Sluice duel could not fit Greyhook's hulls; no duel overhead", e);
            rig = null;
            hounds.clear();
            return;
        }
        sim.addWarship(rig);
        for (Warship hound : hounds) sim.addWarship(hound);
        sim.warships().holdHullFire(HullFireHold.untilBeat(SmallWarSequence.VOSS_ABANDON_BEAT));
        sim.warships().assignEdge(Faction.ALLY, WOLF_EDGE);
        sim.warships().assignEdge(Faction.DEFENDER, GREYHOOK_EDGE);
        ledger = sim.warships().duelLedger();
    }

    private static Warship quiet(Warship ship) {
        ship.stationTo(ship.x, ship.y);
        ship.limitFlux();
        ship.useDoctrine();
        ship.groundFire(Warship.GroundFire.HOLD);
        return ship;
    }

    @Override
    public void withdrawalOrdered(BattleSimulation sim) {
        if (ordered || rig == null) return;
        ordered = true;
        // She lifts round onto her circuit and fights from it; her escorts lift out of their berths to screen her.
        // No doctrine: she does not leave on her own judgement, only when the climb says she may (advance).
        float[] first = circuitStation(rig.x, rig.y);
        rig.lift(first[0], first[1]);
        for (Warship hound : hounds) {
            hound.lift(first[0], first[1]);
            quiet(hound).escort(rig);
        }
        try {
            for (float along : WOLF_ENTRY_ALONG) {
                Warship wolf = quiet(Warship.fit(WOLF_HULL, WOLF_VARIANT, Faction.ALLY, 0f, along, WOLF_HEADING));
                // In across the west edge already under way on its heading over the basin, closing on its own handling.
                sim.warships().enterAcrossEdge(sim, wolf, along, WOLF_HEADING);
                wolf.visit(WOLF_DWELL_SECONDS);
                wolves.add(wolf);
            }
        } catch (Exception e) {
            LOG.warn("Black Sluice duel could not fit the Wolfs; the Greyhook leaves unopposed", e);
            wolves.clear();
        }
        for (Warship wolf : wolves) sim.addWarship(wolf);
    }

    @Override
    public void advance(BattleSimulation sim) {
        if (!ordered || rig == null) return;
        radio(sim);
        boolean wolfOver = false;
        for (Warship wolf : wolves) wolfOver |= over(wolf);
        if (over(rig) && !rig.departing()) fightForHeight(sim, wolfOver);
        // An escort with no lead left and nobody to fight goes home after it: one finite visit for Greyhook too.
        boolean leadGone = rig.destroyed() || rig.departed();
        if (!leadGone || wolfOver) return;
        for (Warship hound : hounds) {
            if (over(hound) && !hound.departing()) sim.warships().leaveAcrossEdge(sim, hound);
        }
    }

    private static boolean over(Warship ship) {
        return !ship.destroyed() && !ship.departed();
    }

    /**
     * One tick of the Greyhook's withdrawal: she climbs at the rate the fight allows her, flies her circuit while no
     * Wolf is hers to fight (her duel helm has her broadside on one when it is), and breaks for her edge once she has
     * made her height, is crippled, or has nobody left to stop her.
     */
    private void fightForHeight(BattleSimulation sim, boolean wolfOver) {
        boolean engaged = false;
        for (Warship wolf : wolves) {
            engaged |= over(wolf) && onMap(wolf)
                    && rig.distanceTo(wolf.centerX(), wolf.centerY()) <= WarshipDuelHelm.reach(wolf);
        }
        boolean screened = false;
        for (Warship hound : hounds) screened |= over(hound) && !hound.departing();
        climb += BattleSimulation.TICK_DT / CLIMB_SECONDS * climbShare(engaged, screened);
        if (rig.hullTarget() == null && !rig.liftRunning() && !rig.berthed()) {
            float[] at = circuitStation(rig.x, rig.y);
            rig.stationTo(at[0], at[1]);
        }
        BreakOff why = climb >= 1f ? BreakOff.CLIMBED
                : rig.hullFraction() < CRIPPLED_HULL ? BreakOff.CRIPPLED
                : !wolves.isEmpty() && !wolfOver ? BreakOff.UNOPPOSED
                : BreakOff.NONE;
        if (why == BreakOff.NONE) return;
        breakOff = why;
        breakTick = sim.getSimTickIndex();
        sim.warships().leaveAcrossEdge(sim, rig);
    }

    /** Whether the hull's centre is over the map: a Wolf flying in is sighted once it has crossed the edge. */
    private static boolean onMap(Warship ship) {
        float x = ship.centerX(), y = ship.centerY();
        return x >= 0f && y >= 0f && x <= BlackSluice.WIDTH && y <= BlackSluice.HEIGHT;
    }

    /** The share of a clear climb's rate the Greyhook makes: all of it unengaged, less under a Wolf's guns. */
    static float climbShare(boolean engaged, boolean screened) {
        if (!engaged) return 1f;
        return screened ? CLIMB_SCREENED : CLIMB_ALONE;
    }

    /** The point on the circuit {@link #CIRCUIT_LEAD_DEGREES} ahead of where {@code (x, y)} stands on it. */
    static float[] circuitStation(float x, float y) {
        double a = Math.atan2((y - CIRCUIT_Y) / CIRCUIT_RY, (x - CIRCUIT_X) / CIRCUIT_RX);
        a -= Math.toRadians(CIRCUIT_LEAD_DEGREES);
        return new float[]{CIRCUIT_X + CIRCUIT_RX * (float) Math.cos(a), CIRCUIT_Y + CIRCUIT_RY * (float) Math.sin(a)};
    }

    /** The approach on the radio: the Wolfs sighted crossing the edge, closing on the Greyhook, the first hull fire. */
    private void radio(BattleSimulation sim) {
        if (!sighted) {
            for (Warship wolf : wolves) sighted |= over(wolf) && onMap(wolf);
            if (sighted) sim.emitStoryBeat(WOLVES_SIGHTED_BEAT);
        }
        if (!closing) {
            for (Warship wolf : wolves) {
                if (!over(wolf) || !onMap(wolf)) continue;
                float near = WarshipDuelHelm.reach(wolf) * CLOSING_REACH_SHARE;
                for (Warship ours : greyhookSide()) {
                    closing |= over(ours) && ours.distanceTo(wolf.centerX(), wolf.centerY()) <= near;
                }
            }
            if (closing) sim.emitStoryBeat(WOLVES_CLOSING_BEAT);
        }
        if (!fired) {
            for (Warship hull : hulls()) {
                for (Warship.Mount m : hull.mounts()) fired |= !m.pointDefence() && m.shotsFired > 0;
            }
            if (fired) sim.emitStoryBeat(FIRST_HULL_FIRE_BEAT);
        }
    }

    private List<Warship> greyhookSide() {
        List<Warship> out = new ArrayList<>(hounds);
        out.add(rig);
        return out;
    }

    /** How far the Greyhook has climbed toward her height: zero at the order, one when she may break. */
    public float climb() { return climb; }
    /** Why she broke for her edge, or {@link BreakOff#NONE} while she is still fighting for height. */
    public BreakOff breakOff() { return breakOff; }
    /** The tick she broke for her edge, or -1. */
    public int breakTick() { return breakTick; }

    /** The Greyhook, Voss's Venture, or {@code null} when none could be fitted. */
    public Warship greyhook() { return rig; }
    public List<Warship> hounds() { return List.copyOf(hounds); }
    public List<Warship> wolves() { return List.copyOf(wolves); }
    /** Every hull of the duel: the Greyhook, her escorts, and the Wolfs once they have come. */
    public List<Warship> hulls() {
        List<Warship> out = new ArrayList<>();
        if (rig != null) out.add(rig);
        out.addAll(hounds);
        out.addAll(wolves);
        return out;
    }
    public boolean ordered() { return ordered; }

    @Override
    public String outcomeSummary() {
        if (!ordered || ledger == null) return null;
        StringBuilder s = new StringBuilder("Overhead: ");
        s.append(rigLine()).append(' ');
        s.append(group("Greyhook's two Hounds", hounds)).append(' ');
        s.append(wolves.isEmpty() ? "The Wolfs never came." : group("The two Wolfs Rusk lent", wolves));
        String ground = groundLine();
        if (ground != null) s.append(' ').append(ground);
        return s.toString();
    }

    private String rigLine() {
        WarshipDuelLedger.Entry e = ledger.entry(rig);
        WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
        return switch (o) {
            case ESCAPED -> "the Greyhook got away, " + state(rig) + ".";
            case DESTROYED -> "the Greyhook was shot down onto the basin.";
            case BROKE_OFF, DEPARTED -> "the Greyhook left the field, " + state(rig) + ".";
            case OVER_THE_BATTLE -> "the Greyhook was still over the berths when we finished.";
        };
    }

    /** How a hull that got away looked going: untouched, scratched, hurt or barely flying. */
    static String state(Warship ship) {
        float f = ship.hullFraction();
        if (f >= 0.95f) return "untouched";
        if (f >= 0.7f) return "scarred";
        if (f >= 0.35f) return "badly hurt";
        return "barely flying";
    }

    private String group(String who, List<Warship> ships) {
        int[] counts = new int[WarshipDuelLedger.Outcome.values().length];
        for (Warship ship : ships) {
            WarshipDuelLedger.Entry e = ledger.entry(ship);
            counts[(e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome()).ordinal()]++;
        }
        List<String> parts = new ArrayList<>();
        add(parts, counts, WarshipDuelLedger.Outcome.DESTROYED, "shot down");
        add(parts, counts, WarshipDuelLedger.Outcome.ESCAPED, "got away");
        add(parts, counts, WarshipDuelLedger.Outcome.BROKE_OFF, "broke off hurt");
        add(parts, counts, WarshipDuelLedger.Outcome.DEPARTED, "left when the fight was done");
        add(parts, counts, WarshipDuelLedger.Outcome.OVER_THE_BATTLE, "still overhead");
        return who + ": " + String.join(", ", parts) + ".";
    }

    private static void add(List<String> parts, int[] counts, WarshipDuelLedger.Outcome o, String words) {
        int n = counts[o.ordinal()];
        if (n == 0) return;
        parts.add((n == 1 ? "one " : n == 2 ? "both " : n + " ") + words);
    }

    private String groundLine() {
        int ours = 0, allies = 0, theirs = 0;
        for (WarshipDuelLedger.Cause c : WarshipDuelLedger.Cause.values()) {
            ours += ledger.killed(c, Faction.MARINE);
            allies += ledger.killed(c, Faction.ALLY);
            theirs += ledger.killed(c, Faction.DEFENDER);
        }
        if (ours + allies + theirs == 0) return "Nothing that fell out of that fight killed anyone on the ground.";
        List<String> parts = new ArrayList<>();
        if (ours > 0) parts.add(ours + " of ours");
        if (allies > 0) parts.add(allies + " of Rusk's people");
        if (theirs > 0) parts.add(theirs + " of Greyhook's");
        return String.format(Locale.ROOT, "Stray fire and falling wreckage killed %s.", String.join(", ", parts));
    }

    /** The ledger's line for one hull of the duel, for evidence. */
    public WarshipDuelLedger.Entry entry(Warship ship) {
        return ledger == null ? null : ledger.entry(ship);
    }
}
