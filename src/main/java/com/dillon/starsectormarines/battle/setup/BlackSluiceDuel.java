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
 * When Voss orders the site abandoned, the <i>Greyhook</i> lifts and turns to fight: the Wolfs are faster than she is,
 * so she does not run. She and her Hounds fight under the ordinary warship doctrine and tactics, the Hounds screening
 * her as her escorts, and two of Rusk's Wolf (H) fly in across the west edge, already under way on a heading east, on
 * a visit nobody can command. Every hull of the duel holds the field ({@link Warship#holdTheField}): no doctrine
 * withdraws it and no visit ends, so the fight is over only when one side's hulls are all destroyed, and the winners
 * stay over the battle. Nobody leaves the map. The Wolfs' edge ({@link #WOLF_EDGE}) is how they arrive; nothing warps
 * in or out, and no area is declared for the fight, which goes where the fighting takes it, over the ground battle
 * included, kept off the map's edges only by the standing edge pressure every warship flies with. It stays gun against
 * gun: the Venture's fitting carries no wing, and a warship over a ground battle never launches one from its bay. No
 * hull of the duel ever fires at the ground. Nothing is scripted after the order: who falls is the ships' own doing,
 * and the debrief reads it from the {@link WarshipDuelLedger}.
 *
 * <p>The ground referee reads nothing here. Stray rounds and falling wreckage are real on the ground, which is the
 * one way the duel can touch the fight below. A ground battle that finishes first ends the duel where it stands.
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
     * they go, turning toward {@link #LIFT_TOWARD_X}, {@link #LIFT_TOWARD_Y}.
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
     * Where along the west edge the Wolfs fly in: either side of a line a little north of the pads, clear of the
     * south edge's margin, so they come straight on across the basin rather than turning off the edge.
     */
    static final float[] WOLF_ENTRY_ALONG = {78f + 14f, 78f - 14f};
    /** The Wolfs' entry heading: due east, in the hull-facing convention. */
    static final float WOLF_HEADING = 270f;
    /**
     * Where the Greyhook's and her Hounds' lift runs turn toward: over the middle of the station, north-west of the
     * cradle and toward the Wolfs' side, clear of the south edge's margin. Where they go from there is their doctrine's.
     */
    static final float LIFT_TOWARD_X = BlackSluice.APPROACH + 150f, LIFT_TOWARD_Y = 98f;
    /** A Wolf this many times its own reach from a Greyhook hull is closing: the radio says so. */
    static final float CLOSING_REACH_SHARE = 1.25f;

    /** The story beats the duel publishes for the radio. */
    public static final String WOLVES_SIGHTED_BEAT = "small_war.wolves_sighted",
            WOLVES_CLOSING_BEAT = "small_war.wolves_closing",
            FIRST_HULL_FIRE_BEAT = "small_war.first_hull_fire",
            GREYHOOK_DOWN_BEAT = "small_war.greyhook_down",
            WOLVES_DOWN_BEAT = "small_war.wolves_down";

    private Warship rig;
    private final List<Warship> hounds = new ArrayList<>();
    private final List<Warship> wolves = new ArrayList<>();
    private WarshipDuelLedger ledger;
    private boolean ordered;
    private int decidedTick = -1;
    private boolean sighted, closing, fired, greyhookDown, wolvesDown;

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
        ledger = sim.warships().duelLedger();
    }

    /** A hull of the duel: under doctrine, holding the field, firing at nothing on the ground. */
    private static Warship quiet(Warship ship) {
        ship.stationTo(ship.x, ship.y);
        ship.limitFlux();
        ship.useDoctrine();
        ship.holdTheField();
        ship.groundFire(Warship.GroundFire.HOLD);
        return ship;
    }

    @Override
    public void withdrawalOrdered(BattleSimulation sim) {
        if (ordered || rig == null) return;
        ordered = true;
        // She lifts and fights under her doctrine; her escorts lift out of their berths to screen her.
        quiet(rig).lift(LIFT_TOWARD_X, LIFT_TOWARD_Y);
        for (Warship hound : hounds) {
            quiet(hound).lift(LIFT_TOWARD_X, LIFT_TOWARD_Y);
            hound.escort(rig);
        }
        try {
            for (float along : WOLF_ENTRY_ALONG) {
                Warship wolf = quiet(Warship.fit(WOLF_HULL, WOLF_VARIANT, Faction.ALLY, 0f, along, WOLF_HEADING));
                // In across the west edge already under way on its heading over the basin, closing on its own handling.
                sim.warships().enterAcrossEdge(sim, wolf, along, WOLF_HEADING);
                // The mission's, not the player's. Holding the field, its visit never ends.
                wolf.visit(0f);
                wolves.add(wolf);
            }
        } catch (Exception e) {
            LOG.warn("Black Sluice duel could not fit the Wolfs; the Greyhook holds the sky unopposed", e);
            wolves.clear();
        }
        for (Warship wolf : wolves) sim.addWarship(wolf);
    }

    @Override
    public void advance(BattleSimulation sim) {
        if (!ordered || rig == null) return;
        radio(sim);
        if (decidedTick < 0 && !wolves.isEmpty() && (allDown(wolves) || allDown(greyhookSide()))) {
            decidedTick = sim.getSimTickIndex();
        }
    }

    private static boolean over(Warship ship) {
        return !ship.destroyed() && !ship.departed();
    }

    private static boolean allDown(List<Warship> ships) {
        for (Warship ship : ships) if (!ship.destroyed()) return false;
        return true;
    }

    /** Whether the hull's centre is over the map: a Wolf flying in is sighted once it has crossed the edge. */
    private static boolean onMap(Warship ship) {
        float x = ship.centerX(), y = ship.centerY();
        return x >= 0f && y >= 0f && x <= BlackSluice.WIDTH && y <= BlackSluice.HEIGHT;
    }

    /**
     * The fight on the radio: the Wolfs sighted crossing the edge, closing on the Greyhook, the first hull fire, the
     * Greyhook going down, and both Wolfs going down.
     */
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
        if (!greyhookDown && rig.destroyed()) {
            greyhookDown = true;
            sim.emitStoryBeat(GREYHOOK_DOWN_BEAT);
        }
        if (!wolvesDown && !wolves.isEmpty() && allDown(wolves)) {
            wolvesDown = true;
            sim.emitStoryBeat(WOLVES_DOWN_BEAT);
        }
    }

    private List<Warship> greyhookSide() {
        List<Warship> out = new ArrayList<>(hounds);
        out.add(rig);
        return out;
    }

    /** The tick one side's hulls were all destroyed, or -1 while both sides still fly (or no Wolf came). */
    public int decidedTick() { return decidedTick; }

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
            case DESTROYED -> "the Greyhook was shot down onto the basin, and Voss walked out of the wreck.";
            case OVER_THE_BATTLE -> wolves.isEmpty() ? "the Greyhook was still overhead when we finished."
                    : allDown(wolves) ? "the Greyhook held the sky, " + state(rig) + "."
                    : "the Greyhook was still fighting when we finished, " + state(rig) + ".";
            // Nothing of this duel leaves the map; kept so a ledger line never reads as nothing.
            case ESCAPED, BROKE_OFF, DEPARTED -> "the Greyhook left the field, " + state(rig) + ".";
        };
    }

    /** How a hull looked at the end: untouched, scarred, badly hurt or barely flying. */
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
