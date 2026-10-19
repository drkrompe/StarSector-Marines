package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.HullRefit;
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
 * sits landed alone in the cradle south of the ridge from the start, quiet: she fires at nothing on the ground, and the
 * battle's hull fire is held until Voss's withdrawal beat ({@link HullFireHold#untilBeat}). When Voss orders the site
 * abandoned, the <i>Greyhook</i> lifts and turns to fight: Rusk's ships are faster than she is, so she does not run.
 * She fights under the ordinary warship doctrine and tactics, and the pair the Rusk family has lent ({@link #RUSK_PAIR}:
 * a Hegemony Enforcer and Rusk's own Wolf) fly in across the west edge, already under way on a heading east, on a visit
 * nobody can command. Two bow-fighting hulls on one target fight it as a pack relay ({@code warship-tactics.md},
 * slice 7): the sturdier Enforcer holds her eye and the Wolf strikes from her quarter. Each lent hull carries its own
 * refit ({@link RuskHull#refit}), the one thing this mission tunes on them. Every hull of the duel holds the field
 * ({@link Warship#holdTheField}): no doctrine withdraws it and no visit ends, so the fight is over only when one
 * side's hulls are all destroyed, and the winners stay over the battle. Nobody leaves the map. Rusk's edge
 * ({@link #RUSK_EDGE}) is how they arrive; nothing warps in or out, and no area is declared for the fight, which goes
 * where the fighting takes it, over the ground battle included, kept off the map's edges only by the standing edge
 * pressure every warship flies with. It stays gun against gun: the Venture's fitting carries no wing, and a warship
 * over a ground battle never launches one from its bay. No hull of the duel ever fires at the ground. Nothing is
 * scripted after the order: who falls is the ships' own doing, and the debrief reads it from the
 * {@link WarshipDuelLedger}.
 *
 * <p>The ground referee reads nothing here. Stray rounds and falling wreckage are real on the ground, which is the
 * one way the duel can touch the fight below. A ground battle that finishes first ends the duel where it stands.
 */
public final class BlackSluiceDuel implements SmallWarDuel {

    private static final Logger LOG = Global.getLogger(BlackSluiceDuel.class);

    /** The Venture and her fitting: guns only, no wing. */
    static final String GREYHOOK_HULL = "venture", GREYHOOK_VARIANT = "venture_Greyhook";
    /**
     * Rusk's refit: capacity and dissipation raised together (the guns and the shield draw on the one pool) by a
     * quarter, the shield's upkeep cut by a quarter and each absorbed point costing a fifth less flux, and the arc
     * widened by twenty degrees. No gun, plate or hull figure changes. Written for his hunting Wolfs, it now rides on
     * his Enforcer ({@link #ENFORCER}).
     */
    public static final HullRefit RUSK_REFIT = new HullRefit("Rusk's hunter refit", 1.25f, 1.25f, 20f, 0.75f, 0.8f);
    /**
     * One of the hulls Rusk lends: what the radio and the captions call it, its hull and fitting, and the refit it
     * flies in.
     */
    public record RuskHull(String name, String hullId, String variantId, HullRefit refit) {
        public RuskHull {
            if (name == null || hullId == null || variantId == null || refit == null) {
                throw new IllegalArgumentException("A lent hull needs a name, a hull, a fitting and a refit");
            }
        }

        /** The same hull in another fitting or refit, for a tuning diagnostic. */
        public RuskHull with(String hull, String variant, HullRefit other) {
            return new RuskHull(name, hull, variant, other);
        }
    }
    /**
     * Rusk's Enforcer: the base game's own Balanced Enforcer, one of the Hegemony's stock medium-combat fits
     * (Arbalests and Heavy Mortars on the turrets, Harpoons and Reapers in the racks), fitted as Hegemony line ships
     * are, in Rusk's refit. Nine hundred of armour that her autocannons glance off makes it the pair's anvil: the
     * relay's tank, holding her eye from nearest. The refit is what lets it finish her: measured over the five duel
     * seeds, without it the Enforcer has to break off to vent, she kills the Wolf on four of them, and the lone Enforcer
     * cannot finish her inside the twenty-five-minute bound.
     */
    public static final RuskHull ENFORCER = new RuskHull("Enforcer", "enforcer", "enforcer_Balanced", RUSK_REFIT);
    /**
     * Rusk's Wolf: his own hunter fitting ({@code wolf_hegemony_Rusk}: an Antimatter Blaster, a pulse laser, Harpoons)
     * as fitted, with no refit: the frigate that darts in on her quarter while the Enforcer holds her eye, and the one
     * hull of the pair her autocannons bite. In the hunter refit it ended at 68 to 97 percent of its hull over the five
     * duel seeds; as fitted it is the hull at risk.
     */
    public static final RuskHull WOLF = new RuskHull("Wolf", "wolf_hegemony", "wolf_hegemony_Rusk", HullRefit.NONE);
    /**
     * The pair Rusk's family lends (owner, 2026-10-19: "We don't need this to be an even fight. We could have an
     * enforcer and a wolf even"): one Enforcer and one Wolf, flying in at {@link #RUSK_ENTRY_ALONG} in this order.
     */
    public static final List<RuskHull> RUSK_PAIR = List.of(ENFORCER, WOLF);
    /**
     * The Greyhook's refit for this fight: her vents opened up, dissipation raised by a third and a little more
     * capacity, and her shield taking a tenth less flux for each point it absorbs. No gun, plate or hull figure
     * changes. Her Heavy Autocannons' kinetic rounds glance off an Enforcer's nine hundred of armour and only her pulse
     * laser bites it, so her way through the pair is the Wolf; under two hulls' fire her shield holds the hard flux of
     * every hit, and she fires in short bursts between its lockouts. The refit puts the Wolf in danger without letting
     * her outlast the pair: measured over the five duel seeds, as fitted she falls 318 to 441 seconds after the first
     * hit with the Wolf at 51 to 84 percent; refitted, 428 to 623 seconds, the Wolf at 9 to 50 percent or killed (one
     * seed). Much more (a quarter more pool, half again the dissipation and a shield at 0.6 of the flux) and she kills
     * the Wolf and then holds the lone Enforcer off past the twenty-five-minute bound.
     */
    public static final HullRefit GREYHOOK_REFIT = new HullRefit("the Greyhook's open vents", 1.1f, 1.35f, 0f, 1f, 0.9f);
    /**
     * Where the Greyhook sits: landed in the cradle on the southern pad field at true scale
     * ({@code landed-ships-true-scale.md}), nose west, the frigate pads either side of her empty. She lifts when Voss
     * orders the withdrawal and flies her lift run, her body easing to flight scale as she goes, turning toward
     * {@link #LIFT_TOWARD_X}, {@link #LIFT_TOWARD_Y}.
     */
    static final float RIG_X = BlackSluice.VENTURE_CRADLE.x(), RIG_Y = BlackSluice.VENTURE_CRADLE.y();
    /**
     * Rusk's edge: west, the side the company landed on and the far side of the basin from the pads, so their
     * whole approach is in sight before a gun bears.
     */
    public static final MapEdge RUSK_EDGE = MapEdge.WEST;
    /**
     * Where along the west edge Rusk's hulls fly in, in {@link #RUSK_PAIR} order: either side of a line a little north of the pads, clear of the
     * south edge's margin, so they come straight on across the basin rather than turning off the edge.
     */
    static final float[] RUSK_ENTRY_ALONG = {84f + 22f, 84f - 22f};
    /** Rusk's entry heading: due east, in the hull-facing convention. */
    static final float RUSK_HEADING = 270f;
    /**
     * Where the Greyhook's lift run turns toward: over the middle of the station, north-west of the
     * cradle and toward Rusk's side, clear of the south edge's margin. Where they go from there is their doctrine's.
     */
    static final float LIFT_TOWARD_X = BlackSluice.APPROACH + 150f, LIFT_TOWARD_Y = 98f;
    /**
     * One of Rusk's hulls this many times its own reach from a Greyhook hull is closing: the radio says so. An
     * Enforcer's reach is long enough that a wider share would hear it closing the moment it crosses the edge, with
     * the sighting.
     */
    static final float CLOSING_REACH_SHARE = 1.1f;

    /** The story beats the duel publishes for the radio. */
    public static final String RUSK_SIGHTED_BEAT = "small_war.rusk_sighted",
            RUSK_CLOSING_BEAT = "small_war.rusk_closing",
            FIRST_HULL_FIRE_BEAT = "small_war.first_hull_fire",
            RUSK_FIRST_DOWN_BEAT = "small_war.rusk_first_down",
            GREYHOOK_DOWN_BEAT = "small_war.greyhook_down",
            RUSK_DOWN_BEAT = "small_war.rusk_down";

    private final String greyhookVariant;
    private final List<RuskHull> pair;
    private final HullRefit greyhookRefit;
    private Warship rig;
    private final List<Warship> rusk = new ArrayList<>();
    private WarshipDuelLedger ledger;
    private boolean ordered;
    private int decidedTick = -1;
    private boolean sighted, closing, fired, firstDown, greyhookDown, ruskDown;

    /** The chapter's duel: Rusk's Enforcer and Wolf, each in its own refit, the Greyhook in hers. */
    public BlackSluiceDuel() {
        this(GREYHOOK_VARIANT, RUSK_PAIR, GREYHOOK_REFIT);
    }

    /**
     * The duel with other hulls, fittings or refits on either side, for a tuning diagnostic; the chapter plays the
     * {@linkplain #BlackSluiceDuel() default}. Rusk's hulls fly in at {@link #RUSK_ENTRY_ALONG} in list order, at most
     * one per entry.
     */
    BlackSluiceDuel(String greyhookVariant, List<RuskHull> pair, HullRefit greyhookRefit) {
        if (pair.isEmpty() || pair.size() > RUSK_ENTRY_ALONG.length) {
            throw new IllegalArgumentException("Rusk lends one to " + RUSK_ENTRY_ALONG.length + " hulls, not " + pair.size());
        }
        this.greyhookVariant = greyhookVariant;
        this.pair = List.copyOf(pair);
        this.greyhookRefit = greyhookRefit;
    }

    /** Puts the rig in its cradle, quiet, before the first tick. */
    public void stage(BattleSimulation sim) {
        if (rig != null) throw new IllegalStateException("The duel is staged once");
        try {
            rig = Warship.fit(GREYHOOK_HULL, greyhookVariant, Faction.DEFENDER, RIG_X, RIG_Y,
                    BlackSluice.VENTURE_CRADLE.facing());
            rig.refit(greyhookRefit);
            rig.berth(RIG_X, RIG_Y);
            rig.limitFlux();
            rig.groundFire(Warship.GroundFire.HOLD);
        } catch (Exception e) {
            // A hull table that cannot be read leaves the chapter without a duel, never without its ground fight.
            LOG.warn("Black Sluice duel could not fit the Greyhook; no duel overhead", e);
            rig = null;
            return;
        }
        sim.addWarship(rig);
        sim.warships().holdHullFire(HullFireHold.untilBeat(SmallWarSequence.VOSS_ABANDON_BEAT));
        sim.warships().assignEdge(Faction.ALLY, RUSK_EDGE);
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
        // She lifts and fights under her doctrine.
        quiet(rig).lift(LIFT_TOWARD_X, LIFT_TOWARD_Y);
        try {
            for (int i = 0; i < pair.size(); i++) {
                RuskHull lent = pair.get(i);
                float along = RUSK_ENTRY_ALONG[i];
                Warship ship = quiet(Warship.fit(lent.hullId(), lent.variantId(), Faction.ALLY, 0f, along, RUSK_HEADING));
                ship.refit(lent.refit());
                // In across the west edge already under way on its heading over the basin, closing on its own handling.
                sim.warships().enterAcrossEdge(sim, ship, along, RUSK_HEADING);
                // The mission's, not the player's. Holding the field, its visit never ends.
                ship.visit(0f);
                rusk.add(ship);
            }
        } catch (Exception e) {
            LOG.warn("Black Sluice duel could not fit Rusk's hulls; the Greyhook holds the sky unopposed", e);
            rusk.clear();
        }
        for (Warship ship : rusk) sim.addWarship(ship);
    }

    @Override
    public void advance(BattleSimulation sim) {
        if (!ordered || rig == null) return;
        radio(sim);
        if (decidedTick < 0 && !rusk.isEmpty() && (allDown(rusk) || rig.destroyed())) {
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

    /** Whether the hull's centre is over the map: a hull flying in is sighted once it has crossed the edge. */
    private static boolean onMap(Warship ship) {
        float x = ship.centerX(), y = ship.centerY();
        return x >= 0f && y >= 0f && x <= BlackSluice.WIDTH && y <= BlackSluice.HEIGHT;
    }

    /**
     * The fight on the radio: Rusk's hulls sighted crossing the edge, closing on the Greyhook, the first hull fire, the
     * first of Rusk's hulls going down while the Greyhook and another of his still fly, the Greyhook going down, and
     * the last of Rusk's going down.
     */
    private void radio(BattleSimulation sim) {
        if (!sighted) {
            for (Warship ship : rusk) sighted |= over(ship) && onMap(ship);
            if (sighted) sim.emitStoryBeat(RUSK_SIGHTED_BEAT);
        }
        if (!closing) {
            for (Warship ship : rusk) {
                if (!over(ship) || !onMap(ship)) continue;
                float near = WarshipDuelHelm.reach(ship) * CLOSING_REACH_SHARE;
                closing |= over(rig) && rig.distanceTo(ship.centerX(), ship.centerY()) <= near;
            }
            if (closing) sim.emitStoryBeat(RUSK_CLOSING_BEAT);
        }
        if (!fired) {
            for (Warship hull : hulls()) {
                for (Warship.Mount m : hull.mounts()) fired |= !m.pointDefence() && m.shotsFired > 0;
            }
            if (fired) sim.emitStoryBeat(FIRST_HULL_FIRE_BEAT);
        }
        if (!firstDown && rusk.size() > 1 && !allDown(rusk) && !rig.destroyed()) {
            for (Warship ship : rusk) firstDown |= ship.destroyed();
            if (firstDown) sim.emitStoryBeat(RUSK_FIRST_DOWN_BEAT);
        }
        if (!greyhookDown && rig.destroyed()) {
            greyhookDown = true;
            sim.emitStoryBeat(GREYHOOK_DOWN_BEAT);
        }
        if (!ruskDown && !rusk.isEmpty() && allDown(rusk)) {
            ruskDown = true;
            sim.emitStoryBeat(RUSK_DOWN_BEAT);
        }
    }

    /** The tick the Greyhook or all of Rusk's hulls were destroyed, or -1 while both sides still fly (or none came). */
    public int decidedTick() { return decidedTick; }

    /** The Greyhook, Voss's Venture, or {@code null} when none could be fitted. */
    public Warship greyhook() { return rig; }
    /** Rusk's hulls once they have come, in {@link #RUSK_PAIR} order. */
    public List<Warship> rusk() { return List.copyOf(rusk); }
    /** What the radio calls each of Rusk's hulls, in {@link #rusk()} order. */
    public List<RuskHull> pair() { return pair; }
    /** Every hull of the duel: the Greyhook, and Rusk's once they have come. */
    public List<Warship> hulls() {
        List<Warship> out = new ArrayList<>();
        if (rig != null) out.add(rig);
        out.addAll(rusk);
        return out;
    }
    public boolean ordered() { return ordered; }

    @Override
    public String outcomeSummary() {
        if (!ordered || ledger == null) return null;
        StringBuilder s = new StringBuilder("Overhead: ");
        s.append(rigLine()).append(' ');
        s.append(rusk.isEmpty() ? "Rusk's ships never came." : ruskLine());
        String ground = groundLine();
        if (ground != null) s.append(' ').append(ground);
        return s.toString();
    }

    private String rigLine() {
        WarshipDuelLedger.Entry e = ledger.entry(rig);
        WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
        return switch (o) {
            case DESTROYED -> "the Greyhook was shot down onto the basin, and Voss walked out of the wreck.";
            case OVER_THE_BATTLE -> rusk.isEmpty() ? "the Greyhook was still overhead when we finished."
                    : allDown(rusk) ? "the Greyhook held the sky, " + state(rig) + "."
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

    /** One clause per lent hull, by name: "Rusk's Enforcer was still overhead, scarred; his Wolf was shot down." */
    private String ruskLine() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < rusk.size(); i++) {
            Warship ship = rusk.get(i);
            WarshipDuelLedger.Entry e = ledger.entry(ship);
            WarshipDuelLedger.Outcome o = e == null ? WarshipDuelLedger.Outcome.OVER_THE_BATTLE : e.outcome();
            String fate = switch (o) {
                case DESTROYED -> " was shot down";
                case OVER_THE_BATTLE -> " was still overhead, " + state(ship);
                case ESCAPED, BROKE_OFF, DEPARTED -> " left the field, " + state(ship);
            };
            parts.add((i == 0 ? "Rusk's " : "his ") + pair.get(i).name() + fate);
        }
        String line = String.join("; ", parts) + ".";
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
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
