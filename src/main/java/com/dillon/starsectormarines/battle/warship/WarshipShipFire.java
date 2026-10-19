package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ship-versus-ship: a warship's mounts firing on hostile hulls, under {@link WarshipShipRules}.
 *
 * <p>It is the third customer of the ship's mounts beside ground fire and {@link WarshipAirFire}, not a second
 * weapon model. A mount takes a hull only when its weapon may fire at a ship, the ship's {@link ShipFireOrder}
 * allows it (and, under {@code AUTO}, no mission {@link HullFireHold} is pending), the hull is in the weapon's
 * reach and the slot's arc, the mount has ordnance and the flux pool lets the burst start; it then traverses onto the point the hull will be at, and fires the weapon's burst on the weapon's
 * own cooldown, magazine and flux. A mount in the middle of a ground or air burst finishes it first.
 *
 * <h2>What a round does</h2>
 * A projectile is aimed where the hull will be after the round's flight ({@link WarshipAirRules#leadPoint}),
 * thrown off by the weapon's cone as a hash of who fired what, and flies as an ordinary {@link WarshipShell} drawn
 * by the existing renderer. It meets the first hull on its way, friend or foe ({@link WarshipRoundSweep}): shield
 * first (as flux, where it crosses the bubble inside the arc) and then the armour rule on the plate it enters; one
 * that reaches its aim point having met none lands on any hull its blast reaches there. A round that reaches no hull,
 * because the hull moved or the cone threw it wide, carries on and comes down on the ground
 * ({@link WarshipShipRules#strayLanding}) as an ordinary detonation, still meeting any hull it crosses: stray fire is
 * real on the ground. A direct-fire round between hulls is not a point-defence target. A beam meets the first hull
 * along its line at once. A mount holds a round whose line to its aim point crosses a friendly hull
 * ({@link WarshipRoundSweep#clearLine}), and starts on a hull only along a clear line.
 *
 * <h2>What a missile does</h2>
 * A missile is not direct fire: point defence on the hull it was fired at may shoot it down. A guided missile leaves
 * its slot inside a widened launch cone ({@link WarshipShipRules#hullArcHalfDegrees}) and flies its own path to where
 * the hull will be when it arrives; it lands as a round does, on hulls first and on the ground past them if it missed.
 * A multi-warhead missile (the Sabot) flies as a carrier to its split range short of the hull, and its warheads are
 * aimed when it splits, at where the hull is then and will be when they arrive, spread evenly across the spec's arc
 * with a hashed jitter: the hull's size and the arc decide how many strike, and a warhead that reaches no hull carries
 * on to the ground. A carrier whose hull is gone by the split sends its warheads where it was going.
 */
public final class WarshipShipFire {

    /** A mount fires once its barrel is this close to the bearing of the lead point. */
    static final float ALIGNED_DEGREES = 3f;
    /** A mount that cannot bring its barrel onto the hull in this long gives it up. */
    static final float ALIGN_PATIENCE_SECONDS = 2.5f;
    private static final float GIVE_UP_REST_SECONDS = 0.5f;
    /** A ship turns toward a hostile hull this far beyond its longest anti-ship reach, so it is facing it by the time it can fire. */
    static final float NOTICE_REACH_SHARE = 1.5f;
    /** The hunter running a charge down keeps the role until another is nearer the charge by more than this, in cells. */
    static final float PACK_SWITCH_CELLS = 6f;

    /** One mount's trigger pull at one hull. */
    private static final class Engagement {
        final Warship target;
        int roundsLeft;
        float timer;
        float unaligned;
        /** Seconds the mount has held its next round for a friend in its line. */
        float fouled;
        boolean paid;
        /** For a missile: its role, the windows open when the volley was started and the one it launched into. */
        MissileWindow.Role role;
        Set<MissileWindow.Kind> open;
        MissileWindow.Kind window;

        Engagement(Warship target, int rounds) {
            this.target = target;
            this.roundsLeft = rounds;
        }
    }

    private final Map<Warship.Mount, Engagement> engagements = new IdentityHashMap<>();
    private final Map<String, Integer> roundsByWeapon = new TreeMap<>();
    private final List<WarshipShell> shells;
    /**
     * A missile at a hull flies its spec's flight magnified {@link WarshipScale#HULL_RANGE} times, as a gun's round
     * does ({@link WarshipScale#hullRoundSpeed}): its speeds, its split range and its warheads' speed are all that many
     * times the spec's, so at hull-fight range its motor's burn and coast, its split and its flight time are the
     * spec's seconds, and point defence has the spec's time to answer it.
     */
    static final float HULL_MISSILE_SCALE = WarshipScale.HULL_RANGE;

    private final WarshipPointDefence tracers;
    private int engagementsStarted;
    private int roundsFired;
    private int roundsOnHull;
    private int roundsStrayed;
    private int missilesFired;
    private int missilesShotDown;
    private int warheadsShotDown;
    private int releases;
    private float worstReleaseSpread;
    private int warheadsReleased;
    private int warheadsOnHull;
    private int warheadsStrayed;
    private float lastReleaseSpread = Float.NaN;
    private boolean held;
    private boolean missileWindows = MissileWindow.ENABLED;
    /** Open salvos: per target, per side (faction ordinal), the hull-fight seconds the salvo stays open. */
    private final Map<Warship, float[]> salvos = new IdentityHashMap<>();
    private final Map<String, Integer> launchesByWindow = new TreeMap<>();
    private final Map<String, Integer> launchesByRole = new TreeMap<>();
    private int gatedLaunches;
    private int gatedIntoOpening;
    private int gatedFittingRole;
    private int openerLaunches;
    private int openerIntoOpening;
    private int coordinatedLaunches;
    private int salvosOpened;

    WarshipShipFire(List<WarshipShell> shells, WarshipPointDefence tracers) {
        this.shells = shells;
        this.tracers = tracers;
    }

    /** Trigger pulls at hulls, for evidence. */
    public int engagements() {
        return engagementsStarted;
    }

    /** Rounds fired at hulls, for evidence. */
    public int roundsFired() {
        return roundsFired;
    }

    /** Rounds fired at a hull whose landing reached a hostile hull (shield or plate), for evidence. */
    public int roundsOnHull() {
        return roundsOnHull;
    }

    /** Rounds fired at a hull that reached none and came down on the ground, for evidence. */
    public int roundsStrayed() {
        return roundsStrayed;
    }

    /** Rounds fired at hulls by weapon id, for evidence. */
    public Map<String, Integer> roundsByWeapon() {
        return Collections.unmodifiableMap(roundsByWeapon);
    }

    /** Missiles (carriers and single missiles) launched at hulls, for evidence. */
    public int missilesFired() {
        return missilesFired;
    }

    /** Missiles (carriers and single missiles) aimed at a hull that point defence shot down, for evidence. */
    public int missilesShotDown() {
        return missilesShotDown;
    }

    /** Warheads aimed at a hull that point defence shot down after the split, for evidence. */
    public int warheadsShotDown() {
        return warheadsShotDown;
    }

    /** Carriers fired at a hull that split while it was still a hostile hull, so their warheads were re-aimed. */
    public int releases() {
        return releases;
    }

    /** The largest {@link #lastReleaseSpread} seen; zero before any release. */
    public float worstReleaseSpread() {
        return worstReleaseSpread;
    }

    /** Warheads released at hulls by carriers that reached their split, for evidence. */
    public int warheadsReleased() {
        return warheadsReleased;
    }

    /** Warheads that reached a hostile hull (shield or plate), for evidence. */
    public int warheadsOnHull() {
        return warheadsOnHull;
    }

    /** Warheads that reached no hull and came down on the ground, for evidence. */
    public int warheadsStrayed() {
        return warheadsStrayed;
    }

    /**
     * How far, in cells, the centre of the most recent release at a live hull lay from that hull's centre led by the
     * warheads' flight; {@code NaN} before any. Evidence that warheads are aimed at the hull when they split.
     */
    public float lastReleaseSpread() {
        return lastReleaseSpread;
    }

    /** Whether missiles at hulls wait for a window their role can use ({@link MissileWindow}); the control is off. */
    public boolean missileWindows() {
        return missileWindows;
    }

    /** Turns launch windows on or off for this battle; a scene's control loop turns them off. */
    public void missileWindows(boolean on) {
        this.missileWindows = on;
    }

    /** Missile volleys launched at hulls by the window they went into (the first open window, for the control). */
    public Map<String, Integer> launchesByWindow() {
        return Collections.unmodifiableMap(launchesByWindow);
    }

    /** Missile volleys launched at hulls by their launcher's role. */
    public Map<String, Integer> launchesByRole() {
        return Collections.unmodifiableMap(launchesByRole);
    }

    /** Volleys launched by a launcher whose role waits for windows (anything but general-purpose). */
    public int gatedLaunches() {
        return gatedLaunches;
    }

    /** Of {@link #gatedLaunches}, those launched while an opening was open on the target. */
    public int gatedIntoOpening() {
        return gatedIntoOpening;
    }

    /** Of {@link #gatedLaunches}, those launched while a window the launcher's role can use was open. */
    public int gatedFittingRole() {
        return gatedFittingRole;
    }

    /**
     * Volleys launched by a launcher that waits for an opening rather than a raised shield: a torpedo, a finisher or a
     * saturation launcher (an anti-shield missile's window is the shield itself).
     */
    public int openerLaunches() {
        return openerLaunches;
    }

    /** Of {@link #openerLaunches}, those launched while an opening was open on the target. */
    public int openerIntoOpening() {
        return openerIntoOpening;
    }

    /** Volleys launched while a packmate's salvo was open on the target, or by a striker while the tank held its eye. */
    public int coordinatedLaunches() {
        return coordinatedLaunches;
    }

    /** Salvos opened on a target by a launch into an opening, for packmates' launchers to join. */
    public int salvosOpened() {
        return salvosOpened;
    }

    /** Whether a salvo of {@code side}'s is open on {@code target} now. */
    boolean salvoOpen(Faction side, Warship target) {
        float[] open = salvos.get(target);
        return open != null && open[side.ordinal()] > 0f;
    }

    /** Runs the open salvos down on the hull fight's clock and forgets those on hulls no longer over the battle. */
    void advance(float dt) {
        float hullDt = dt / WarshipScale.HULL_TEMPO;
        salvos.entrySet().removeIf(e -> e.getKey().destroyed() || e.getKey().departed());
        for (float[] open : salvos.values()) {
            for (int i = 0; i < open.length; i++) open[i] = Math.max(0f, open[i] - hullDt);
        }
    }

    private void noteLaunch(Warship ship, Engagement e) {
        launchesByWindow.merge(e.window.label(), 1, Integer::sum);
        launchesByRole.merge(e.role.label(), 1, Integer::sum);
        if (e.role != MissileWindow.Role.GENERAL) {
            gatedLaunches++;
            if (MissileWindow.label(e.open).opening()) gatedIntoOpening++;
            if (MissileWindow.launch(e.role, e.open) != null) gatedFittingRole++;
            if (e.role != MissileWindow.Role.ANTI_SHIELD) {
                openerLaunches++;
                if (MissileWindow.label(e.open).opening()) openerIntoOpening++;
            }
            if (e.open.contains(MissileWindow.Kind.SALVO) || e.open.contains(MissileWindow.Kind.STRIKE)) {
                coordinatedLaunches++;
            }
            if (e.window.opening() && e.window != MissileWindow.Kind.SALVO) {
                // A launch into an opening calls the side's other launchers onto the same target.
                salvos.computeIfAbsent(e.target, t -> new float[Faction.values().length])[ship.faction.ordinal()]
                        = MissileWindow.SALVO_SECONDS;
                salvosOpened++;
            }
        }
    }

    void noteShotDown() {
        missilesShotDown++;
    }

    void noteWarheadShotDown() {
        warheadsShotDown++;
    }

    void noteWarhead(boolean onHull) {
        if (onHull) warheadsOnHull++;
        else warheadsStrayed++;
    }

    private int missilesOnHull;
    private int missilesStrayed;

    /** Single missiles (not carriers) aimed at a hull that reached a hostile hull, shield or plate, for evidence. */
    public int missilesOnHull() {
        return missilesOnHull;
    }

    /** Single missiles aimed at a hull that reached none and came down on the ground, for evidence. */
    public int missilesStrayed() {
        return missilesStrayed;
    }

    void noteMissileLanding(boolean onHull) {
        if (onHull) missilesOnHull++;
        else missilesStrayed++;
    }

    void noteLanding(boolean onHull) {
        if (onHull) roundsOnHull++;
        else roundsStrayed++;
    }

    private int hullsStruckInFlight;
    private int hullsStruck;
    private int unintendedStops;
    private int friendlyHits;
    private int escortHits;
    private int screenHits;
    private int roundsAtEscortedCharges;
    private float lineHeldSeconds;
    private int lineRefusals;
    private final Map<Faction, Float> lineHeldBySide = new TreeMap<>();

    /**
     * Tallies a round between hulls that reached {@code struck} (the hulls the strike just reached): fired by
     * {@code side} at {@code intended} ({@code null} for a round carrying on past the hull it missed), met on its way
     * or where it landed.
     */
    void noteStruck(Faction side, Warship intended, List<Warship> struck, boolean inFlight) {
        if (struck.isEmpty()) return;
        if (intended == null || !struck.contains(intended)) unintendedStops++;
        for (Warship ship : struck) {
            hullsStruck++;
            if (inFlight) hullsStruckInFlight++;
            if (side.friendlyTo(ship.faction)) friendlyHits++;
            Warship lead = WarshipEscort.liveLead(ship);
            if (lead != null) {
                escortHits++;
                if (lead == intended) screenHits++;
            }
        }
    }

    /** Hull hits by rounds between hulls (one per hull a round reached), for evidence. */
    public int hullsStruck() {
        return hullsStruck;
    }

    /** Of {@link #hullsStruck}, those a round met on its way rather than where it landed. */
    public int hullsStruckInFlight() {
        return hullsStruckInFlight;
    }

    /** Rounds between hulls stopped by a hull they were not fired at (a stray round meeting any hull counts). */
    public int unintendedStops() {
        return unintendedStops;
    }

    /** Hull hits on a hull friendly to the side that fired the round: friendly fire between hulls. */
    public int friendlyHits() {
        return friendlyHits;
    }

    /** Hull hits on an escort with a live charge, by anyone's round. */
    public int escortHits() {
        return escortHits;
    }

    /** Of {@link #escortHits}, rounds fired at the escort's own charge: the screen taking fire meant for its ship. */
    public int screenHits() {
        return screenHits;
    }

    private void noteLineHeld(Faction side, float dt) {
        lineHeldSeconds += dt;
        lineHeldBySide.merge(side, dt, Float::sum);
    }

    /** {@link #lineHeldSeconds} by the side whose mounts held, for evidence. */
    public Map<Faction, Float> lineHeldBySide() {
        return Collections.unmodifiableMap(lineHeldBySide);
    }

    /** Seconds of mount time spent holding a round, or not starting, for a friend in the line of fire. */
    public float lineHeldSeconds() {
        return lineHeldSeconds;
    }

    /** Times a mount found every hull in its reach fouled by a friend and started nothing. */
    public int lineRefusals() {
        return lineRefusals;
    }

    /** Whether a mission's {@link HullFireHold} is keeping {@link ShipFireOrder#AUTO} ships off hulls this tick. */
    boolean held() {
        return held;
    }

    void held(boolean on) {
        this.held = on;
    }

    /** Whether the ship may fire at hulls now, with no mission hold pending. */
    static boolean looksAtHulls(Warship ship) {
        return looksAtHulls(ship, false);
    }

    /**
     * Whether the ship may fire at hulls now: never under {@code OFF}; always under {@code ALL}; under {@code AUTO}
     * when no mission hold is pending and the ship carries a weapon that may fire at a ship, so a gunless hull (a
     * tender, a salvage rig) is never steered toward a fight it cannot answer.
     */
    static boolean looksAtHulls(Warship ship, boolean held) {
        if (ship.destroyed() || ship.departed() || ship.overloaded()) return false;
        return switch (ship.shipFire()) {
            case OFF -> false;
            case ALL -> true;
            case AUTO -> !held && armedAgainstHulls(ship);
        };
    }

    /** Whether any of the ship's mounts carries a weapon that may fire at a ship. */
    static boolean armedAgainstHulls(Warship ship) {
        for (Warship.Mount m : ship.mounts()) {
            if (!m.knockedOut() && WarshipShipRules.mayFireAtHull(m.weapon)) return true;
        }
        return false;
    }

    /** Whether {@code target} is a hull {@code ship} may fight. */
    static boolean hostileHull(Warship ship, Warship target) {
        // A hull still coming down through the cloud deck, or flying in with its centre still past the map's edge, is
        // not yet seen: at hull-fight ranges it would otherwise be under fire before it was out of the cloud or across
        // the edge.
        return target != ship && !target.destroyed() && !target.departed() && !target.descending()
                && !target.offMapInbound() && ship.faction.hostileTo(target.faction);
    }

    /**
     * Chooses the hull the ship turns its guns on, once per tick: the nearest hostile hull over the battle for a
     * ship whose duel helm is on, else the nearest within {@link #NOTICE_REACH_SHARE} of its longest anti-ship
     * reach; kept while it is still a hostile hull in that range. Ties go to the earlier-added hull.
     */
    void chooseTarget(Warship ship, List<Warship> ships) {
        // A ship still flying in from past the edge is not over the battle yet: it neither is fought nor fights.
        if (!looksAtHulls(ship, held) || ship.offMapInbound()) {
            ship.hullTarget(null);
            return;
        }
        // A ship free to manoeuvre goes to find the hull wherever it is over the battle; one holding its station
        // turns only toward a hull that is nearly in reach.
        // A departing ship answers what is in reach as it goes; it does not hunt.
        boolean hunting = ship.duelHelm() && !ship.departing();
        if (hunting && WarshipTactics.ENABLED) {
            // The group decided who fights whom (WarshipTactics); a hunter with no assignment yet falls through.
            Warship assigned = ship.manoeuvre().target();
            if (assigned != null && hostileHull(ship, assigned)) {
                engage(ship, assigned);
                return;
            }
        } else if (hunting) {
            Warship role = packTarget(ship, ships, held);
            if (role != null) {
                engage(ship, role);
                return;
            }
        }
        float notice = hunting ? Float.MAX_VALUE : ship.departing() ? longestReach(ship)
                : longestReach(ship) * NOTICE_REACH_SHARE;
        Warship current = ship.hullTarget();
        if (current != null && hostileHull(ship, current) && gap(ship, current) <= notice) return;
        Warship best = null;
        float bestGap = Float.MAX_VALUE;
        for (Warship other : ships) {
            if (!hostileHull(ship, other)) continue;
            float g = gap(ship, other);
            if (g <= notice && g < bestGap) {
                best = other;
                bestGap = g;
            }
        }
        if (best != null) engage(ship, best);
        else ship.hullTarget(null);
    }

    /** Turns the ship on {@code target}; the first hull it fights puts it on the base game's duel footing. */
    private static void engage(Warship ship, Warship target) {
        ship.duelFooting();
        ship.hullTarget(target);
    }

    /**
     * The pack's division of a hunt, re-decided every tick: when two or more ships of a side are hunting with their
     * duel helm (not escorting) and a hostile hull is being escorted (a <em>charge</em>), the hunter nearest the
     * charge runs it down and every other hunter pins the charge's escort nearest to itself. The runner keeps the
     * role until another hunter is nearer the charge by {@link #PACK_SWITCH_CELLS}. {@code null} when there is no
     * pack or no charge: a lone hunter fights the nearest hull, and so does a pack once the escorts are gone.
     */
    static Warship packTarget(Warship ship, List<Warship> ships) {
        return packTarget(ship, ships, false);
    }

    static Warship packTarget(Warship ship, List<Warship> ships, boolean held) {
        if (WarshipEscort.liveLead(ship) != null) return null;
        Warship charge = null;
        float chargeGap = Float.MAX_VALUE;
        for (Warship other : ships) {
            if (!hostileHull(ship, other) || WarshipEscort.escortsOf(other, ships).isEmpty()) continue;
            float g = gap(ship, other);
            if (g < chargeGap) {
                charge = other;
                chargeGap = g;
            }
        }
        if (charge == null) return null;
        Warship runner = null;
        float runnerGap = Float.MAX_VALUE;
        Warship holder = null;
        int hunters = 0;
        for (Warship other : ships) {
            if (other.faction != ship.faction || !looksAtHulls(other, held) || !other.duelHelm()
                    || WarshipEscort.liveLead(other) != null || !hostileHull(other, charge)) continue;
            hunters++;
            float g = gap(other, charge);
            if (g < runnerGap) {
                runner = other;
                runnerGap = g;
            }
            // Whoever was running the charge down last tick holds the role, so it does not flap between near equals.
            if (other.hullTarget() == charge && holder == null) holder = other;
        }
        if (hunters < 2) return null;
        if (holder != null && gap(holder, charge) <= runnerGap + PACK_SWITCH_CELLS) runner = holder;
        if (runner == ship) return charge;
        Warship pinned = null;
        float pinnedGap = Float.MAX_VALUE;
        for (Warship escort : WarshipEscort.escortsOf(charge, ships)) {
            if (!hostileHull(ship, escort)) continue;
            float g = gap(ship, escort);
            if (g < pinnedGap) {
                pinned = escort;
                pinnedGap = g;
            }
        }
        return pinned != null ? pinned : charge;
    }

    private static float gap(Warship ship, Warship other) {
        return other.distanceTo(ship.x, ship.y);
    }

    /** The longest reach, in cells, of the ship's guns that may fire at a hull; zero when it has none. */
    static float longestReach(Warship ship) {
        float reach = 0f;
        for (Warship.Mount m : ship.mounts()) {
            if (WarshipShipRules.gunAtHull(m.weapon) && !m.knockedOut()) {
                reach = Math.max(reach, WarshipScale.hullReach(m.weapon.rangePx()));
            }
        }
        return reach;
    }

    /**
     * Gives one mount its turn at the hulls. Returns {@code true} when the mount spent this tick on a hull (the
     * caller must then not run its ground logic), {@code false} when it is not engaged with one. The decrement of the
     * mount's cooldown is this method's whenever it returns {@code true}.
     */
    boolean tickMount(float dt, WarshipSystem system, Warship ship, Warship.Mount m, List<Warship> ships) {
        if (!looksAtHulls(ship, held) || !WarshipShipRules.mayFireAtHull(m.weapon) || ship.offMapInbound()) {
            engagements.remove(m);
            return false;
        }
        VanillaWeapon w = m.weapon;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        Engagement e = engagements.get(m);
        if (e != null && (!hostileHull(ship, e.target) || !inReach(ship, m, mx, my, e.target))) {
            engagements.remove(m);
            e = null;
        }
        if (e == null) {
            if (m.burstRemaining > 0 || m.cooldown - dt > 0f) return false;
            if (!ship.hasHullOrdnance(m)) return false;
            if (!ship.fluxPool().canPress(WarshipPointDefence.engagementFlux(w))) return false;
            if (w.missile() && !ship.missileVolleyReady()) return false;
            boolean clear = system.clearLineFire();
            Warship target = pick(ship, m, mx, my, ships, clear);
            if (target == null) {
                if (clear && pick(ship, m, mx, my, ships, false) != null) {
                    // Every hull it could fire at is behind a friend: it holds rather than fire into its own side.
                    lineRefusals++;
                    noteLineHeld(ship.faction, dt);
                }
                return false;
            }
            if (!mayStart(ship, target, WarshipPointDefence.engagementFlux(w))) return false;
            MissileWindow.Role role = null;
            Set<MissileWindow.Kind> open = null;
            MissileWindow.Kind window = null;
            if (w.missile()) {
                role = MissileWindow.role(w);
                open = MissileWindow.read(ship, target, mx, my, salvoOpen(ship.faction, target));
                if (missileWindows) {
                    window = MissileWindow.launch(role, open);
                    if (window == null) return false;
                } else {
                    if (MissilePacing.finisher(w) && !MissilePacing.softened(target)) return false;
                    window = MissileWindow.label(open);
                }
            }
            e = new Engagement(target, w.beam() ? 1 : Math.max(1, w.burstSize()));
            e.role = role;
            e.open = open;
            e.window = window;
            engagements.put(m, e);
            if (w.missile()) ship.missileVolleyStarted();
        }
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        m.aimed = false;
        Warship target = e.target;
        float[] aim = aimFor(mx, my, target, w);
        float bearing = AirBody.facingToward(aim[0] - mx, aim[1] - my);
        WarshipSystem.traverse(dt, ship, m, bearing);
        // A guided missile turns onto the hull after launch: inside its launch cone it need not wait on the barrel.
        // A hardpoint's barrel is fixed to the hull but lays within its own few degrees of arc, as in the base game.
        boolean aligned = WarshipShipRules.guided(w) || (m.slot.hardpoint()
                ? Math.abs(WarshipFacing.wrap(sightBearing(ship, mx, my, aim[0], aim[1]) - m.headingDegrees))
                        <= WarshipShipRules.hullArcHalfDegrees(m)
                : Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) <= ALIGNED_DEGREES);
        if (!aligned) {
            e.unaligned += dt;
            if (e.unaligned > ALIGN_PATIENCE_SECONDS) {
                engagements.remove(m);
                m.cooldown = Math.max(m.cooldown, GIVE_UP_REST_SECONDS);
            }
            return true;
        }
        e.unaligned = 0f;
        e.timer -= dt;
        if (e.timer > 0f) return true;
        if (system.clearLineFire() && !WarshipRoundSweep.clearLine(ship, target, mx, my, aim[0], aim[1], ships)) {
            // A friend has come into the line: the round waits for it to pass, and the mount gives up if it does not.
            e.fouled += dt;
            noteLineHeld(ship.faction, dt);
            if (e.fouled > ALIGN_PATIENCE_SECONDS) {
                engagements.remove(m);
                m.cooldown = Math.max(m.cooldown, GIVE_UP_REST_SECONDS);
            }
            return true;
        }
        e.fouled = 0f;
        if (!ship.hasHullOrdnance(m)) {
            engagements.remove(m);
            return true;
        }
        if (!e.paid) {
            // Paid for when the barrel is on the hull and about to fire, so a mount that never gets on spends nothing.
            float cost = WarshipPointDefence.engagementFlux(w);
            if (!mayStart(ship, target, cost)) {
                engagements.remove(m);
                return true;
            }
            ship.fluxPool().spend(cost);
            e.paid = true;
            engagementsStarted++;
            if (e.window != null) noteLaunch(ship, e);
        }
        fireRound(system, ship, m, target, aim);
        e.roundsLeft--;
        // Between hulls a gun cycles on the hull fight's clock (D1): the burst gap and the cooldown run long by it.
        e.timer = MissilePacing.roundDelay(w, w.burstDelaySeconds()) * WarshipScale.HULL_TEMPO;
        if (e.roundsLeft <= 0) {
            engagements.remove(m);
            m.cooldown = WarshipScale.HULL_TEMPO * Math.max(w.cooldownSeconds(),
                    w.beam() ? WarshipPointDefence.MIN_ENGAGEMENT_SECONDS : 0.05f);
        }
        return true;
    }

    /**
     * Whether a salvo costing {@code cost} may be started at {@code target}: within the ship's flux order, or past it
     * when the target is open or losing the flux war. Open is an overloaded target, or a shielded one whose shield is
     * down, pressed by a ship whose own shield is up or which is less deep in flux: it presses through that window
     * rather than venting, so a shield that drops is punished before it can come back up. Losing is a target at least {@link #PRESS_TARGET_FLUX} full and {@link #PRESS_LEAD} fuller than the
     * shooter: the leader keeps the pressure on until the target's shield gives. Either way only up to
     * {@link #PRESS_CEILING} of its own pool, under the level at which its own shield would drop
     * ({@link WarshipShield#DROP_LEVEL}), so pressing never overloads the presser.
     */
    static boolean mayStart(Warship ship, Warship target, float cost) {
        WarshipManoeuvre relay = ship.manoeuvre();
        if (relay.role == WarshipManoeuvre.Role.TANK || relay.role == WarshipManoeuvre.Role.STRIKER) {
            // A pack relay (WarshipTactics): a venting ship holds its fire; a tank keeps its pool low enough for its
            // shield to hold the target, firing only as that allows, except into an overloaded target; a striker the
            // target is not fighting needs no such headroom and fires on up to the pressing ceiling. What the tank's
            // shield holds is the hard flux its absorbed hits put in the pool: its own guns' flux is soft and vents
            // behind the shield, so it is the hard share the tank's ceiling counts (the ordinary limits below still
            // keep the whole pool short of the shield's drop).
            if (relay.relayVenting) return false;
            if (relay.role == WarshipManoeuvre.Role.TANK) {
                if (ship.hardFluxFraction() >= WarshipTactics.TANK_FIRE_SHARE && !target.overloaded()) {
                    return false;
                }
            } else if (target.hullTarget() != ship && ship.fluxPool().canPress(cost)
                    && ship.flux() + cost < PRESS_CEILING * ship.fluxPool().capacity()) {
                return true;
            }
        }
        if (ship.fluxPool().canStart(cost)) return true;
        if (!PRESSING || !ship.fluxPool().canPress(cost) || ship.flux() + cost >= PRESS_CEILING * ship.fluxPool().capacity()) {
            return false;
        }
        float own = ship.fluxFraction();
        float theirs = target.fluxFraction();
        if (target.overloaded()) return true;
        // An open target is pressed by a ship that is not itself open, or is less deep in flux than it: two ships with
        // their shields locked out do not trade hull at the limit, the one further gone breaks off to vent.
        if (target.hasShield() && !target.shieldAbsorbing()
                && (ship.shieldAbsorbing() || !ship.hasShield() || own < theirs)) return true;
        return theirs >= PRESS_TARGET_FLUX && theirs >= own + PRESS_LEAD;
    }

    /** {@code -Dbattle.warship.pressing=false} is the control: no ship fires past its own flux order. */
    static final boolean PRESSING = !"false".equalsIgnoreCase(System.getProperty("battle.warship.pressing"));
    /** Flux share a target has to carry before a ship presses it past its own soft limit. */
    static final float PRESS_TARGET_FLUX = 0.6f;
    /** How much fuller than the shooter a target has to be for the shooter to press it. */
    static final float PRESS_LEAD = 0.1f;
    /** Share of its own pool a ship pressing a losing target stops at. */
    static final float PRESS_CEILING = 0.8f;

    private static boolean inReach(Warship ship, Warship.Mount m, float mx, float my, Warship target) {
        float[] aim = aimFor(mx, my, target, m.weapon);
        // Gun or missile, a mount reaches a hull at the hull-to-hull reach, so the base game's ratio between a gun's
        // range and a missile's survives; a missile flies its own path there.
        float reach = WarshipScale.hullReach(m.weapon.rangePx());
        // A ship free to manoeuvre holds its missiles for the gun fight: a launcher at a hull waits until the hull is
        // inside the ship's gun reach, so the duel opens with gunnery and a magazine is the close-quarters blow, not a
        // long-range opener. A ship held on its station will never close, so it launches at the missile's own reach.
        float guns = m.weapon.missile() && ship.duelHelm() ? longestReach(ship) : 0f;
        if (guns > 0f) reach = Math.min(reach, guns);
        float sx = m.slot.hardpoint() ? sightX(ship, mx) : mx;
        float sy = m.slot.hardpoint() ? sightY(ship, my) : my;
        if (Math.hypot(aim[0] - sx, aim[1] - sy) > reach) return false;
        float bearing = WarshipFacing.bearing(sx, sy, aim[0], aim[1]);
        float off = Math.abs(WarshipFacing.wrap(bearing - (ship.facingDegrees + m.slot.angleDegrees())));
        return off <= WarshipShipRules.hullArcHalfDegrees(m);
    }

    /**
     * The bearing a hardpoint sights {@code (ax, ay)} along, and the point its reach is measured from: its mount pulled
     * in toward the hull's centre by
     * {@link WarshipScale#HULL_REACH_SHARE}. A hull is drawn {@code 1 / HULL_REACH_SHARE} times larger against its
     * guns' reach than in the base game, so a hardpoint set out toward a beam would see a hull its nose is laid on
     * that many times further off its own line than the base game's toe-in allows for, and with a few degrees of arc
     * never fire: an Eagle laid its nose on and fired one of its three hardpoints, its heavy needlers silent for the
     * whole duel, and a hardpoint set well aft lost reach to the drawn hull's length the same way. Pulling the mount in
     * restores the base game's angles and reach at the same share of reach. The round still flies from the muzzle.
     */
    static float sightBearing(Warship ship, float mx, float my, float ax, float ay) {
        return WarshipFacing.bearing(sightX(ship, mx), sightY(ship, my), ax, ay);
    }

    private static float sightX(Warship ship, float mx) {
        return WarshipShipRules.sight(ship.x, mx);
    }

    private static float sightY(Warship ship, float my) {
        return WarshipShipRules.sight(ship.y, my);
    }

    /** Where a mount aims to hit {@code target}: a gun's lead point, or where a missile's own flight will meet the hull. */
    static float[] aimFor(float mx, float my, Warship target, VanillaWeapon w) {
        if (!w.missile() || w.flight() == null) {
            return WarshipAirRules.leadPoint(mx, my, target.centerX(), target.centerY(),
                    target.velocityX, target.velocityY, w, WarshipScale.HULL_RANGE);
        }
        float tx = target.centerX();
        float ty = target.centerY();
        float ax = tx;
        float ay = ty;
        for (int pass = 0; pass < 2; pass++) {
            float t = missileSeconds(w.flight(), mx, my, ax, ay);
            ax = tx + target.velocityX * t;
            ay = ty + target.velocityY * t;
        }
        return new float[]{ax, ay};
    }

    /** How long a missile takes from the muzzle to {@code (ax, ay)}: for a carrier, its flight to the split and its warheads' after. */
    static float missileSeconds(MissileFlightSpec flight, float mx, float my, float ax, float ay) {
        MissileFlightSpec.Mirv mirv = flight.mirv();
        if (mirv == null) {
            return MissilePath.plan(flight, mx, my, ax, ay, 0L, HULL_MISSILE_SCALE).totalSeconds();
        }
        float dist = (float) Math.hypot(ax - mx, ay - my);
        float along = splitAlong(flight, mirv, mx, my, ax, ay, 0L);
        float ux = dist > 0f ? (ax - mx) / dist : 0f;
        float uy = dist > 0f ? (ay - my) / dist : 0f;
        float carrier = MissilePath.plan(flight, mx, my, mx + ux * along, my + uy * along, 0L,
                HULL_MISSILE_SCALE).totalSeconds();
        return carrier + (dist - along) / Math.max(1f, WarshipScale.cells(mirv.warheadSpeedPx()) * HULL_MISSILE_SCALE);
    }

    /** How far along its line to {@code (ax, ay)} a carrier splits, by the spec (vanilla's reading, {@link MirvSplit}). */
    private static float splitAlong(MissileFlightSpec flight, MissileFlightSpec.Mirv mirv, float mx, float my,
                                    float ax, float ay, long seed) {
        float dist = (float) Math.hypot(ax - mx, ay - my);
        float ux = dist > 0f ? (ax - mx) / dist : 0f;
        float uy = dist > 0f ? (ay - my) / dist : 0f;
        return MirvSplit.along(dist, WarshipScale.cells(mirv.splitRangePx()) * HULL_MISSILE_SCALE, mirv.minTimeToSplit(),
                mirv.canSplitEarly(), a -> MissilePath.plan(flight, mx, my, mx + ux * (float) a,
                        my + uy * (float) a, seed, HULL_MISSILE_SCALE).totalSeconds());
    }

    /**
     * The ship's chosen hull if this mount can reach it, else the nearest hostile hull it can; ties by add order. With
     * {@code clear}, only a hull it has a clear line to ({@link WarshipRoundSweep#clearLine}): a mount whose line to
     * the chosen hull crosses a friend takes another hull in its reach rather than fire through the friend.
     */
    private static Warship pick(Warship ship, Warship.Mount m, float mx, float my, List<Warship> ships, boolean clear) {
        Warship chosen = ship.hullTarget();
        if (chosen != null && hostileHull(ship, chosen) && inReach(ship, m, mx, my, chosen)
                && (!clear || clearTo(ship, m, mx, my, chosen, ships))) return chosen;
        Warship best = null;
        double bestDist = Double.MAX_VALUE;
        for (Warship other : ships) {
            if (!hostileHull(ship, other) || !inReach(ship, m, mx, my, other)) continue;
            if (clear && !clearTo(ship, m, mx, my, other, ships)) continue;
            double d = Math.hypot(other.centerX() - mx, other.centerY() - my);
            if (d < bestDist) {
                best = other;
                bestDist = d;
            }
        }
        return best;
    }

    /** Whether the mount's line to where it would aim at {@code target} is clear of friends. */
    private static boolean clearTo(Warship ship, Warship.Mount m, float mx, float my, Warship target,
                                   List<Warship> ships) {
        float[] aim = aimFor(mx, my, target, m.weapon);
        return WarshipRoundSweep.clearLine(ship, target, mx, my, aim[0], aim[1], ships);
    }

    private void fireRound(WarshipSystem system, Warship ship, Warship.Mount m, Warship target, float[] aim) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        ship.spendOrdnance(m);
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        roundsFired++;
        roundsByWeapon.merge(w.id(), 1, Integer::sum);
        system.fireCue(m, w, mx, my);
        float penetration = WarshipSystem.penetration(w.damageType());
        float blast = Math.max(1f, WarshipScale.cells(w.damageRadiusPx()));
        long seed = WarshipAirRules.roundSeed(m.slot.id(), m.shotsFired,
                ((long) ship.serial() << 20) ^ target.serial(), ship.faction.ordinal());
        if (w.missile() && w.flight() != null) {
            fireMissile(ship, w, mx, my, target, aim, seed, blast, m.headingDegrees);
            return;
        }
        if (w.beam()) {
            // A beam lands where it points: on the hull's near face, at once.
            float damage = w.damagePerSecond() * WarshipAirRules.BEAM_PULSE_SECONDS;
            WarshipRoundSweep.Contact c = system.inFlightCollision()
                    ? WarshipRoundSweep.first(system.ships(), ship, mx, my, target.centerX(), target.centerY()) : null;
            if (c != null) {
                // It meets the first hull along its line, which is its target's near face unless something is between.
                system.strikeContact(c, WarshipSystem.detonation(ship.faction, c.x(), c.y(), damage, penetration, blast),
                        mx, my, w.damageType(), w);
                noteStruck(ship.faction, target, system.struck(), true);
                noteLanding(true);
                tracers.trace(ship, w, mx, my, system.strikeX(c.x()), system.strikeY(c.y()), true);
                return;
            }
            boolean onHull = system.strikeHulls(WarshipSystem.detonation(ship.faction, target.centerX(),
                    target.centerY(), damage, penetration, blast), mx, my, w.damageType(), w);
            if (onHull) noteStruck(ship.faction, target, system.struck(), false);
            noteLanding(onHull);
            // The beam ends where it met the hull or its shield.
            tracers.trace(ship, w, mx, my, system.strikeX(target.centerX()), system.strikeY(target.centerY()), onHull);
            return;
        }
        float dx = aim[0] - mx;
        float dy = aim[1] - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float miss = WarshipShipRules.coneMiss(w, dist, WarshipAirRules.unit(seed, 0));
        float nx = dist > 0f ? -dy / dist : 0f;
        float ny = dist > 0f ? dx / dist : 0f;
        float lx = aim[0] + nx * miss;
        float ly = aim[1] + ny * miss;
        float reach = WarshipScale.hullReach(w.rangePx());
        float[] stray = WarshipShipRules.strayLanding(mx, my, lx, ly, reach, WarshipAirRules.unit(seed, 1));
        float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        float speed = Math.max(1f, WarshipScale.hullRoundSpeed(w.projectileSpeedPx()));
        MissilePath path = MissilePath.straight(mx, my, lx, ly, speed);
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, lx, ly, path, visual, w.damagePerShot(),
                penetration, blast, null, null, 0f, null, 0f).atHull(target, stray[0], stray[1], true));
    }

    /** A missile launched at a hull: a carrier whose warheads are aimed at the split, or one missile to the hull. */
    private void fireMissile(Warship ship, VanillaWeapon w, float mx, float my, Warship target, float[] aim,
                             long seed, float blast, float launchDegrees) {
        boolean heavy = MissileArchetype.of(w) == MissileArchetype.HEAVY;
        missilesFired++;
        MissileFlightSpec flight = w.flight();
        float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        MissileFlightSpec.Mirv mirv = flight.mirv();
        if (mirv != null) {
            float dist = (float) Math.hypot(aim[0] - mx, aim[1] - my);
            float along = splitAlong(flight, mirv, mx, my, aim[0], aim[1], seed);
            float ux = dist > 0f ? (aim[0] - mx) / dist : 0f;
            float uy = dist > 0f ? (aim[1] - my) / dist : 0f;
            MissilePath carrier = MissilePath.atHull(flight, mx, my, mx + ux * along, my + uy * along, seed,
                    HULL_MISSILE_SCALE, launchDegrees, false);
            // Placeholder warheads at where the hull is expected: the split re-aims them at where it actually is.
            List<WarshipShell.Warhead> heads = warheads(ship.faction, mirv, carrier.landX(), carrier.landY(),
                    aim[0], aim[1], seed);
            float dartBlast = Math.max(1f, WarshipScale.cells(mirv.damageRadiusPx()));
            shells.add(new WarshipShell(w, ship.faction, ship, mx, my, aim[0], aim[1], carrier, visual, 0f,
                    WarshipSystem.penetration(mirv.damageType()), dartBlast, heads, null, flight.hitpoints(),
                    mirv.splitSound(), 1f).aimedAtHull(target));
            return;
        }
        float lx = aim[0];
        float ly = aim[1];
        if (!WarshipShipRules.guided(w)) {
            // An unguided rocket is thrown off by its cone as a gun's round is.
            float dist = (float) Math.hypot(lx - mx, ly - my);
            float miss = WarshipShipRules.coneMiss(w, dist, WarshipAirRules.unit(seed, 0));
            lx += dist > 0f ? -(aim[1] - my) / dist * miss : 0f;
            ly += dist > 0f ? (aim[0] - mx) / dist * miss : 0f;
        }
        MissilePath path = MissilePath.atHull(flight, mx, my, lx, ly, seed, HULL_MISSILE_SCALE, launchDegrees, heavy);
        float[] stray = WarshipShipRules.strayLanding(mx, my, path.landX(), path.landY(),
                WarshipScale.cells(w.rangePx()), WarshipAirRules.unit(seed, 1));
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, path.landX(), path.landY(), path, visual,
                w.damagePerShot(), WarshipSystem.penetration(w.damageType()), blast, null, null, flight.hitpoints(),
                null, 0f).atHull(target, stray[0], stray[1], false));
    }

    /**
     * A carrier's warheads from its split point {@code (rx, ry)} toward {@code (ax, ay)}: spread evenly across the
     * spec's arc, each jittered by a hash of the seed by up to half the gap between them (less the jitter's mean, so
     * the fan stays centred on the aim), flying straight at the warhead speed to as far as the aim point is.
     */
    static List<WarshipShell.Warhead> warheads(Faction faction, MissileFlightSpec.Mirv mirv, float rx, float ry,
                                               float ax, float ay, long seed) {
        int n = Math.max(1, mirv.count());
        float blast = Math.max(1f, WarshipScale.cells(mirv.damageRadiusPx()));
        float visual = Math.max(1f, WarshipScale.cells(mirv.visualRadiusPx()));
        float speed = Math.max(1f, WarshipScale.cells(mirv.warheadSpeedPx()) * HULL_MISSILE_SCALE);
        float chord = Math.max(1f, (float) Math.hypot(ax - rx, ay - ry));
        double forward = Math.atan2(ay - ry, ax - rx);
        double arc = Math.toRadians(mirv.arcDegrees());
        float penetration = WarshipSystem.penetration(mirv.damageType());
        List<WarshipShell.Warhead> out = new ArrayList<>(n);
        // Each warhead is jittered off its even share of the arc, but the pattern stays centred on the aim: the jitter
        // shuffles warheads within the fan rather than walking the whole fan off the hull it was aimed at.
        double[] jitter = new double[n];
        double meanJitter = 0.0;
        for (int i = 0; i < n; i++) {
            jitter[i] = (WarshipAirRules.unit(seed, 10 + i) - 0.5) * (n == 1 ? arc : arc / (n - 1));
            meanJitter += jitter[i] / n;
        }
        if (n == 1) meanJitter = 0.0;
        for (int i = 0; i < n; i++) {
            double even = n == 1 ? 0.0 : arc * ((double) i / (n - 1) - 0.5);
            double a = forward + even + jitter[i] - meanJitter;
            float tx = rx + (float) Math.cos(a) * chord;
            float ty = ry + (float) Math.sin(a) * chord;
            float seconds = chord / speed;
            BloomPath path = BloomPath.plan(seed + i * 0x9E3779B1L, rx, ry, tx, ty, seconds, (float) a, 0f, 0f);
            out.add(new WarshipShell.Warhead(WarshipSystem.detonation(faction, tx, ty, mirv.damage(), penetration,
                    blast), path.totalSeconds(), visual, tx, ty, path, mirv.warheadHitpoints()));
        }
        return out;
    }

    /**
     * A carrier fired at a hull has reached its split: its warheads, re-aimed at the hull as it is now (led by their
     * own flight) while it is still a hostile hull over the battle, each landing on hulls and carrying on to the
     * ground if it misses.
     */
    List<WarheadStrike> release(WarshipShell shell, float rx, float ry) {
        MissileFlightSpec.Mirv mirv = shell.weapon.flight().mirv();
        Warship target = shell.hullTarget();
        long seed = WarshipAirRules.roundSeed(shell.weapon.id(), Float.floatToIntBits(shell.fromX),
                ((long) Float.floatToIntBits(shell.fromY) << 32) ^ Float.floatToIntBits(shell.toX),
                shell.faction.ordinal());
        List<WarshipShell.Warhead> heads = shell.warheads();
        if (!target.destroyed() && !target.departed() && shell.faction.hostileTo(target.faction)) {
            float speed = Math.max(1f, WarshipScale.cells(mirv.warheadSpeedPx()) * HULL_MISSILE_SCALE);
            float ax = target.centerX();
            float ay = target.centerY();
            for (int pass = 0; pass < 2; pass++) {
                float t = (float) Math.hypot(ax - rx, ay - ry) / speed;
                ax = target.centerX() + target.velocityX * t;
                ay = target.centerY() + target.velocityY * t;
            }
            heads = warheads(shell.faction, mirv, rx, ry, ax, ay, seed);
            float cx = 0f;
            float cy = 0f;
            for (WarshipShell.Warhead h : heads) {
                cx += h.aimX() / heads.size();
                cy += h.aimY() / heads.size();
            }
            lastReleaseSpread = (float) Math.hypot(cx - ax, cy - ay);
            worstReleaseSpread = Math.max(worstReleaseSpread, lastReleaseSpread);
            releases++;
        }
        List<WarheadStrike> out = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) {
            WarshipShell.Warhead h = heads.get(i);
            float chord = (float) Math.hypot(h.aimX() - rx, h.aimY() - ry);
            float[] stray = WarshipShipRules.strayLanding(rx, ry, h.aimX(), h.aimY(), 2f * chord,
                    WarshipAirRules.unit(seed, 40 + i));
            out.add(new WarheadStrike(shell.weapon, shell.faction, shell.shooter, rx, ry, h, mirv.sprite())
                    .atHull(target, stray[0], stray[1]));
        }
        warheadsReleased += out.size();
        return out;
    }

    /** A warhead that reached no hull, carrying on from where it passed to the ground it comes down on. */
    static WarshipShell carryOn(WarheadStrike strike) {
        PendingDetonation det = strike.detonation();
        float speed = Math.max(1f, strike.path().speed(strike.delaySeconds));
        MissilePath leg = MissilePath.straight(strike.aimX, strike.aimY, strike.strayX(), strike.strayY(), speed);
        return new WarshipShell(strike.weapon, strike.faction, strike.shooter, strike.aimX, strike.aimY,
                strike.strayX(), strike.strayY(), leg, strike.visualCells, det.damage, det.penetration,
                det.aoeRadius, null, null, 0f, null, 0f).directFire().markStray();
    }
}
