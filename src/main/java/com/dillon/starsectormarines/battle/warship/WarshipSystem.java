package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Ticks the warships over the battle: each mount picks the nearest enemy it can
 * reach and traverse to, then fires its weapon's burst at that ground, every
 * round a real detonation that lands after the flight time its own speed
 * implies.
 *
 * <p>The shells are not a second damage model. A round becomes an ordinary
 * {@link PendingDetonation}, so splash, line of sight, wall breach, roof loss
 * and friendly fire come from the pipeline that already owns them, at the
 * figures the weapon itself carries. Friendly fire is unconditional, as it is
 * for every detonation.
 */
public final class WarshipSystem {

    /** Penetration by the game's damage type — what armour has to stop. */
    private static final float PENETRATION_KINETIC = 12f;
    private static final float PENETRATION_EXPLOSIVE = 8f;
    private static final float PENETRATION_ENERGY = 10f;
    private static final float PENETRATION_OTHER = 4f;

    private final List<Warship> ships = new ArrayList<>();
    private final List<Warship> shipsView = Collections.unmodifiableList(ships);
    private final List<WarshipShell> shells = new ArrayList<>();
    private final List<WarshipShell> shellsView = Collections.unmodifiableList(shells);
    private final List<WarheadStrike> strikes = new ArrayList<>();
    private final List<WarheadStrike> strikesView = Collections.unmodifiableList(strikes);
    private final List<SplitEvent> splits = new ArrayList<>();
    private final List<SplitEvent> splitsView = Collections.unmodifiableList(splits);
    private final WarshipHitFx hitFx = new WarshipHitFx();
    private final List<Interceptable> interceptables = new ArrayList<>();
    private final List<Interceptable> interceptablesView = Collections.unmodifiableList(interceptables);
    private final Map<Warship.Mount, Volley> volleys = new IdentityHashMap<>();
    private int fusedRounds;
    private final Random random = new Random(20261011L);
    private final WarshipPointDefence pointDefence = new WarshipPointDefence();
    private final WarshipAirFire airFire = new WarshipAirFire(shells, pointDefence);
    private final WarshipShipFire shipFire = new WarshipShipFire(shells, pointDefence);
    private final WarshipTactics tactics = new WarshipTactics();
    private final List<WarshipWreck> wrecks = new ArrayList<>();
    private final List<WarshipWreck> wrecksView = Collections.unmodifiableList(wrecks);
    private final Set<Warship> wrecked = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Ships the battle may not end without ({@link #concludeBeforeBattleEnds}). */
    private final List<Warship> mustConclude = new ArrayList<>();
    private int nextSerial;
    /** Wreck keys for the crash machinery: far below every aircraft id, which are positive. */
    private static final long WRECK_KEY_BASE = Long.MIN_VALUE / 2;
    /** How often a falling hull sheds a burning piece onto its own track, in seconds. */
    private static final float WRECK_FIRE_PERIOD_SECONDS = 0.2f;

    /** A round this heavy lands as a heavy blast; lighter ones are only a detonation. */
    private static final float HEAVY_IMPACT_DAMAGE = 100f;

    /**
     * Seconds between two fire cues from one mount. A Devastator fires twelve
     * rounds a second and a cue per round would be static, so a burst is heard
     * as one report per this interval; a single shot always sounds.
     */
    private static final float FIRE_CUE_GAP_SECONDS = 0.3f;
    /** Seconds between two landing cues for one weapon, for the same reason. */
    private static final float IMPACT_CUE_GAP_SECONDS = 0.2f;
    /** How many cluster bodies the cinematic preset splits a Hammer into. */
    private static final int SYNTHETIC_SPLIT_COUNT = 6;
    /** The fan, in degrees, a synthetic cluster leaves the split in. */
    private static final float SYNTHETIC_ARC_DEGREES = 140f;
    /** How far along its flight a synthetic cluster's carrier splits. */
    private static final float SYNTHETIC_SPLIT_SHARE = 0.55f;
    /** A warhead's weave, as a share of its chord, in the presets that curve the bloom. */
    private static final float BLOOM_WEAVE = 0.04f;
    /** The spread of a rack volley is never wider than this radius, whatever its size, in cells. */
    private static final float MAX_VOLLEY_RADIUS_CELLS = 16f;
    /** A warhead's flight never takes less than this long, so the sim always sees it leave. */
    private static final float MIN_WARHEAD_SECONDS = 0.15f;
    /** The share of its own speed a warhead averages at least, however long the preset staggers the sheaf. */
    private static final float MIN_SPEED_SHARE = 0.4f;
    private static final int PERM_STREAM = 300;

    private final List<WarshipSoundCue> cues = new ArrayList<>();
    private final List<WarshipSoundCue> cuesView = Collections.unmodifiableList(cues);
    private final Map<Object, Float> lastCueAt = new IdentityHashMap<>();
    private final Set<Warship.Mount> litBeams = Collections.newSetFromMap(new IdentityHashMap<>());
    private float clock;

    /** Sounds asked for since the last {@link #beginFrame()}, for whoever presents the battle. */
    public List<WarshipSoundCue> cuesThisFrame() {
        return cuesView;
    }

    /** Forgets last frame's cues; called once per simulation advance so a paused caller does not replay them. */
    public void beginFrame() {
        cues.clear();
    }

    private boolean cueAllowed(Object key, float gap) {
        Float last = lastCueAt.get(key);
        if (last != null && clock - last < gap) return false;
        lastCueAt.put(key, clock);
        return true;
    }

    /** The game's generic missile blast, for a missile whose projectile names no explosion sound (the Annihilator's). */
    static final String MISSILE_EXPLOSION_SOUND = "explosion_missile";

    /** The sound a weapon's round lands with: its own, else the generic missile blast for a missile, else none. */
    static String impactSound(VanillaWeapon w) {
        String own = w.impactSoundId();
        if (own != null) return own;
        return w.missile() ? MISSILE_EXPLOSION_SOUND : null;
    }

    private void cue(WarshipSoundCue.Kind kind, String soundId, float x, float y) {
        if (soundId != null) cues.add(new WarshipSoundCue(kind, soundId, x, y, 1f));
    }

    /** The game's own deep boom (the jump gate's), used low and slow for a ship breaking the deck. */
    static final String ARRIVAL_REPORT_SOUND = "gate_explosion";
    /** The game's heavy hit, used for the pressure wave arriving on the ground. */
    static final String ARRIVAL_SETTLE_SOUND = "hit_heavy";
    /** The game's engine boost, used for a ship climbing away: low at lift-off, higher as it goes. */
    static final String DEPARTURE_SOUND = "engine_accelerate";
    /** The pass's own roar when a ship is sent on a low pass: the engine boost, low. */
    static final String LOW_PASS_ROAR_SOUND = "engine_accelerate";
    /** The report of a ship coming onto its line at speed: the same deep boom as a breaking of the deck. */
    static final String LOW_PASS_REPORT_SOUND = "gate_explosion";
    /** Seconds after lift-off at which the climbing roar is given a second, higher voice. */
    static final float DEPARTURE_CLIMB_CUE_SECONDS = 2.5f;
    /** At most one of each atmosphere sound per this many seconds, however many ships enter together. */
    private static final float ATMOSPHERE_CUE_GAP_SECONDS = 2f;

    private void atmosphereCue(WarshipSoundCue.Kind kind, String soundId, Warship ship) {
        if (cueAllowed(kind, ATMOSPHERE_CUE_GAP_SECONDS)) cue(kind, soundId, ship.x, ship.y);
    }

    /** Whether a clock that read {@code before} and now reads {@code after} passed {@code at}. */
    private static boolean crossed(float before, float after, float at) {
        return before < at && after >= at;
    }

    /**
     * Records the sounds of a ship entering and leaving through the deck. They are
     * requests only, like every other cue here: nothing reads them back, so the
     * simulation is the same with or without anybody listening.
     */
    private void entryCues(Warship ship, float entryBefore) {
        float entryAfter = ship.entrySeconds();
        if (entryBefore < 0f) return;
        if (entryBefore == 0f && entryAfter > 0f) {
            atmosphereCue(WarshipSoundCue.Kind.ARRIVAL_REPORT, ARRIVAL_REPORT_SOUND, ship);
        }
        if (crossed(entryBefore, entryAfter, WarshipAtmosphere.DESCENT_SECONDS * 0.82f)) {
            atmosphereCue(WarshipSoundCue.Kind.ARRIVAL_SETTLE, ARRIVAL_SETTLE_SOUND, ship);
        }
    }

    private void departureCues(Warship ship, float departingBefore) {
        if (!ship.departing()) return;
        float after = ship.departingSeconds();
        if (departingBefore < 0f) {
            atmosphereCue(WarshipSoundCue.Kind.DEPARTURE_ROAR, DEPARTURE_SOUND, ship);
        } else if (crossed(departingBefore, after, DEPARTURE_CLIMB_CUE_SECONDS)) {
            atmosphereCue(WarshipSoundCue.Kind.DEPARTURE_CLIMB, DEPARTURE_SOUND, ship);
        }
    }

    /** Puts a ship over the battle. */
    public void add(Warship ship) {
        // Doctrine passes are staggered by add order, so a fleet does not all look at once.
        ship.doctrineClock = DOCTRINE_PERIOD_SECONDS - 0.1f * (ships.size() % 10);
        if (ship.serial() < 0) ship.serial(nextSerial++);
        ships.add(ship);
        ledger.register(ship);
        if (ship.supportSource() != null && !called.contains(ship)) called.add(ship);
    }

    private final List<Warship> called = new ArrayList<>();
    private final WarshipDuelLedger ledger = new WarshipDuelLedger();

    /** What became of every hull over the battle and what the fight between hulls cost the ground; read-only. */
    public WarshipDuelLedger duelLedger() {
        return ledger;
    }
    private final List<Warship> calledView = Collections.unmodifiableList(called);

    /**
     * Every hull a ship-support call put over this battle, kept after it is destroyed or has
     * left (unlike {@link #ships()}), so settlement can read how each one ended.
     */
    public List<Warship> called() {
        return calledView;
    }

    /** Seconds between two doctrine passes for one ship. */
    static final float DOCTRINE_PERIOD_SECONDS = 1f;
    /** Cells beyond a hazard's radius, and beyond half the hull, a standing-off ship keeps. */
    private static final float STANDOFF_MARGIN_CELLS = 5f;
    /** Seconds ahead of touchdown a denial round should land, so the first one is already falling when the boat arrives. */
    private static final float DENIAL_LEAD_SECONDS = 0.5f;
    /** Seconds before the first gun may fire at which a zone is taken, so the hull can swing and the turrets traverse onto it. */
    private static final float DENIAL_PREPARE_SECONDS = 10f;

    /**
     * Ground a planetary defence (or any mission-declared threat) makes dangerous
     * to a capital of the factions hostile to {@code owner}. It does no damage:
     * nothing in the ground model hurts a capital yet. It is only a declared
     * input to a posture that keeps its distance.
     */
    public record CapitalHazard(Faction owner, float x, float y, float radius) {}

    private final List<CapitalHazard> hazards = new ArrayList<>();

    public void declareCapitalHazard(Faction owner, float x, float y, float radius) {
        if (!(radius > 0f)) throw new IllegalArgumentException("a hazard needs a radius");
        hazards.add(new CapitalHazard(owner, x, y, radius));
    }

    public void clearCapitalHazards() {
        hazards.clear();
    }

    /** The hazards a mission declared; the live envelopes of ground batteries are {@link #batteryHazards()}. */
    public List<CapitalHazard> capitalHazards() {
        return Collections.unmodifiableList(hazards);
    }

    private final Map<Object, CapitalHazard> batteryEnvelopes = new LinkedHashMap<>();

    /**
     * Registers (or moves) the live firing envelope of a ground battery under {@code key}. Unlike a declared hazard it
     * belongs to the battery's fire control, which withdraws it when the battery falls silent, so a mission clearing
     * its own hazards never removes it.
     */
    public void setBatteryHazard(Object key, Faction owner, float x, float y, float radius) {
        if (!(radius > 0f)) throw new IllegalArgumentException("an envelope needs a radius");
        batteryEnvelopes.put(key, new CapitalHazard(owner, x, y, radius));
    }

    /** Withdraws the envelope registered under {@code key}; a no-op when there is none. */
    public void clearBatteryHazard(Object key) {
        batteryEnvelopes.remove(key);
    }

    /** The live envelopes of ground batteries that can fire now. */
    public List<CapitalHazard> batteryHazards() {
        return List.copyOf(batteryEnvelopes.values());
    }

    /**
     * Whether a hull of {@code length} cells hovering at {@code (x, y)} would sit inside a live battery envelope
     * hostile to {@code side}. The same test the standoff pushes a station out by, for whoever chooses a station.
     */
    public boolean insideBatteryEnvelope(Faction side, float x, float y) {
        for (CapitalHazard h : batteryEnvelopes.values()) {
            if (h.owner().hostileTo(side) && Math.hypot(x - h.x(), y - h.y()) < h.radius()) return true;
        }
        return false;
    }

    public List<Warship> ships() {
        return shipsView;
    }

    /** The ships' point defence, for presentation and evidence. */
    public WarshipPointDefence pointDefence() {
        return pointDefence;
    }

    /** The ships' fire at aircraft, for evidence. */
    /** Ship-versus-ship fire, for evidence. */
    public WarshipShipFire shipFire() {
        return shipFire;
    }

    /** The group layer over hull fights, for a scene's control loop to switch its parts. */
    WarshipTactics tactics() {
        return tactics;
    }

    /** Whether rounds between hulls meet the first hull on their way ({@link WarshipRoundSweep}). */
    private boolean inFlightCollision = WarshipRoundSweep.ENABLED;
    /** Whether a mount holds a round that would cross a friendly hull ({@link WarshipRoundSweep#clearLine}). */
    private boolean clearLineFire = WarshipRoundSweep.CLEAR_LINE;

    /** Whether rounds between hulls meet the first hull on their way, rather than resolving where they land. */
    public boolean inFlightCollision() {
        return inFlightCollision;
    }

    /** Switches in-flight collision, for a scene's control loop. */
    public void inFlightCollision(boolean on) {
        this.inFlightCollision = on;
    }

    /** Whether a mount keeps a friend out of its line of fire; only meaningful while rounds can meet a friend. */
    public boolean clearLineFire() {
        return inFlightCollision && clearLineFire;
    }

    /** Switches the clear-line check, for a scene's control loop. */
    public void clearLineFire(boolean on) {
        this.clearLineFire = on;
    }

    private final List<HullFireHold> hullFireHolds = new ArrayList<>();

    /** Each side's approved edge, the one way its warships come on and go off; a side absent has none. */
    private final EnumMap<Faction, MapEdge> sideEdges = new EnumMap<>(Faction.class);

    /**
     * Holds hull fire for this battle until {@code hold} is released: no ship under {@link ShipFireOrder#AUTO} fires
     * at a hull while any hold is pending. Returns the hold, so the mission can release it by hand.
     */
    public HullFireHold holdHullFire(HullFireHold hold) {
        if (hold == null) throw new IllegalArgumentException("a hull-fire hold is needed");
        hullFireHolds.add(hold);
        return hold;
    }

    /**
     * Gives {@code side} an approved map edge: from now on its warships come on by flying in across it
     * ({@link #enterAcrossEdge}) and every way they leave (a mission's withdrawal, a visit's end, spent station time,
     * an escort following its lead out) flies out across it ({@link #leaveAcrossEdge}). Nothing of that side warps in
     * or out. {@code null} takes the edge away again, and its ships leave as ships with no edge do.
     */
    public void assignEdge(Faction side, MapEdge edge) {
        if (side == null) throw new IllegalArgumentException("an edge belongs to a side");
        if (edge == null) sideEdges.remove(side);
        else sideEdges.put(side, edge);
    }

    /** The approved edge of {@code side}, or {@code null} when the mission gave it none. */
    public MapEdge edgeFor(Faction side) {
        return side == null ? null : sideEdges.get(side);
    }

    /**
     * Brings {@code ship} on across its side's approved edge, {@code along} cells along it and already moving on
     * {@code headingDegrees}, which must carry it onto the map ({@link Warship#enterAcrossEdge}). The caller adds the
     * ship to the battle.
     *
     * @throws IllegalStateException when the ship's side has no approved edge
     */
    public void enterAcrossEdge(BattleSimulation sim, Warship ship, float along, float headingDegrees) {
        MapEdge edge = requiredEdge(ship);
        ship.enterAcrossEdge(edge, along, headingDegrees, sim.getGrid().getWidth(), sim.getGrid().getHeight());
    }

    /**
     * Sends {@code ship} out across its side's approved edge as an escape: it flies straight for the edge and off the
     * map, a hull that can be hit and fires at hulls in reach until it is gone ({@link Warship#leaveAcrossEdge}).
     *
     * @throws IllegalStateException when the ship's side has no approved edge
     */
    public void leaveAcrossEdge(BattleSimulation sim, Warship ship) {
        ship.leaveAcrossEdge(requiredEdge(ship), sim.getGrid().getWidth(), sim.getGrid().getHeight());
    }

    private MapEdge requiredEdge(Warship ship) {
        MapEdge edge = edgeFor(ship.faction);
        if (edge == null) throw new IllegalStateException(ship.faction + " has no approved map edge");
        return edge;
    }

    /** Releases every pending hold named {@code name}; a name nothing holds by is ignored. */
    public void releaseHullFire(String name) {
        for (HullFireHold hold : hullFireHolds) if (hold.name().equals(name)) hold.release();
    }

    /** The mission's holds on hull fire, released or not, in the order they were set. */
    public List<HullFireHold> hullFireHolds() {
        return Collections.unmodifiableList(hullFireHolds);
    }

    /** Whether a mission hold is keeping {@link ShipFireOrder#AUTO} ships off hulls, as of the last tick. */
    public boolean hullFireHeld() {
        return shipFire.held();
    }

    private boolean hullFireHeld(BattleSimulation sim) {
        boolean held = false;
        for (HullFireHold hold : hullFireHolds) held |= hold.holds(sim);
        return held;
    }

    /** Seconds this system has ticked: the clock a hull's plating dates its breaches on. */
    public float clock() {
        return clock;
    }

    /** Hulls taken to nothing this battle, falling, skidding or down, in the order they were lost. */
    public List<WarshipWreck> wrecks() {
        return wrecksView;
    }

    /**
     * The battle may not end until {@code ship}'s part in it has concluded ({@link #concluded}): a mission whose story
     * overhead has an ending (a hull that must get away or be brought down) holds the battle open for it. The
     * simulation reads {@link #awaitingConclusion()} beside its other completion holds; nothing else changes about
     * how the ship flies or fights.
     */
    public void concludeBeforeBattleEnds(Warship ship) {
        if (ship == null) throw new IllegalArgumentException("a ship is needed");
        for (Warship s : mustConclude) if (s == ship) return;
        mustConclude.add(ship);
    }

    /**
     * Whether {@code ship}'s part in the battle is over: it has left the map, or it has been destroyed and its wreck
     * has fallen, struck and come to rest. A hull still falling or skidding has not concluded.
     */
    public boolean concluded(Warship ship) {
        return concluded(ship, wrecks);
    }

    /** {@link #concluded(Warship)} against these wrecks. */
    static boolean concluded(Warship ship, List<WarshipWreck> wrecks) {
        if (ship.departed()) return true;
        if (!ship.destroyed()) return false;
        for (WarshipWreck wreck : wrecks) if (wreck.ship == ship) return wreck.phase() == WarshipWreck.Phase.DOWN;
        return false;
    }

    /** Whether a ship the mission holds the battle open for ({@link #concludeBeforeBattleEnds}) has not concluded. */
    public boolean awaitingConclusion() {
        for (Warship ship : mustConclude) if (!concluded(ship)) return true;
        return false;
    }

    public WarshipAirFire airFire() {
        return airFire;
    }

    /** Rounds in flight, for presentation and evidence. */
    public List<WarshipShell> shells() {
        return shellsView;
    }

    /** Warheads a carrier has released that have not yet detonated, for presentation and evidence. */
    public List<WarheadStrike> strikes() {
        return strikesView;
    }

    /** What the hull fight's hits look like: flares on shields, blasts on hulls, breach smoke. Presentation only. */
    public WarshipHitFx hitFx() {
        return hitFx;
    }

    /** Splits the carriers have just made, newest last, for presentation: one flash per sheaf. */
    public List<SplitEvent> splits() {
        return splitsView;
    }

    /**
     * Every body in flight that point defence may shoot down (rounds, carriers and warheads),
     * rebuilt once a tick, for the ground defences that read a battle's ordnance through one seam.
     */
    public List<Interceptable> interceptables() {
        return interceptablesView;
    }

    /** Rounds that were folded into another instead of flying, because a cap was reached; for evidence. */
    public int fusedRounds() {
        return fusedRounds;
    }

    /** Live damage-bearing bodies in the air: from one hull, or from all of them when {@code ship} is null. */
    public int liveBodies(Warship ship) {
        int n = 0;
        for (WarshipShell s : shells) if (!s.intercepted && (ship == null || s.shooter == ship)) n += s.bodyCount();
        for (WarheadStrike s : strikes) if (!s.intercepted && (ship == null || s.shooter == ship)) n++;
        return n;
    }

    /** A turret fires once its barrel is this close to the bearing of what it has chosen. */
    private static final float ALIGNED_DEGREES = 2f;
    /** A beam this strong, in damage per second, is a lance that cuts ground; weaker beams are anti-ship and silent here. */
    static final float LANCE_MIN_DPS = 500f;
    private static final float LANCE_TELEGRAPH_SECONDS = 1.5f;
    private static final float LANCE_PENETRATION = 10f;
    private static final int LANCE_WALL_DAMAGE = 100;
    private static final float LANCE_MIN_HALF_WIDTH = 1.2f;
    private static final float LANCE_MIN_SWEEP_RATE = 8f;
    private static final float LANCE_MAX_HALF_ANGLE = 1.2f;
    private static final float LANCE_REST_SECONDS = 2f;

    public void tick(float dt, BattleSimulation sim) {
        clock += dt;
        landShells(dt, sim);
        landStrikes(dt, sim);
        splits.removeIf(s -> s.advance(dt));
        airFire.advance(sim, ships);
        for (Warship ship : ships) if (ship.destroyed() && wrecked.add(ship)) startWreck(sim, ship);
        ships.removeIf(Warship::destroyed);
        tickWrecks(dt, sim);
        shipFire.held(hullFireHeld(sim));
        shipFire.advance(dt);
        tactics.plan(clock, ships, shipFire.held());
        WarshipEdgeHelm edges = new WarshipEdgeHelm(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        for (Warship ship : ships) {
            // A ship fighting hulls spends on the hull fight's clock (D1).
            float fightDt = ship.hullTarget() != null ? dt / WarshipScale.HULL_TEMPO : dt;
            ship.tickMissileGate(fightDt);
            ship.tickReloads(fightDt);
            ship.tickMountRepairs(clock);
            doctrine(dt, sim, ship);
            shipFire.chooseTarget(ship, ships);
            ship.tickEdgeEntry(dt, edges);
            duelHelm(sim, ship, tactics, clock, edges);
            float entryBefore = ship.entrySeconds();
            ship.addEntrySeconds(dt);
            ship.addLiftSeconds(dt, 0.5f * sim.getGrid().getWidth(), 0.5f * sim.getGrid().getHeight());
            entryCues(ship, entryBefore);
            if (ship.tickVisit(dt, hostileHullOver(ship))) {
                // The visit is over: nothing hostile in the air for the dwell, so it leaves the ordinary way.
                ship.stayFor(0f);
            }
            float departingBefore = ship.departing() ? ship.departingSeconds() : -1f;
            depart(dt, sim, ship, edgeFor(ship.faction));
            if (ship.departing()) {
                ledger.leaving(ship, ship.posture() == WarshipPosture.WITHDRAW,
                        ship.climbingOut() || ship.escapingAcrossEdge());
            }
            if (ship.departed()) ledger.gone(ship, clock, ship.physicallyDeparted());
            departureCues(ship, departingBefore);
            direct(dt, sim, ship);
            tickShield(fightDt, sim, ship);
            ship.fluxPool().update(fightDt);
            if (!lowPass(dt, sim, ship)) moveHull(dt, sim, ship, edges);
            turnHull(dt, ship);
            // After the turn as well as the move: a hull turning about its pivot swings its centre too.
            edges.contain(ship);
            for (Warship.Mount mount : ship.mounts()) {
                if (mount.knockedOut()) continue;
                if (ship.descending()) {
                    // Still in the cloud deck: nothing fires, point defence included, and the guns ride at rest.
                    traverse(dt, ship, mount, ship.facingDegrees + mount.slot.angleDegrees());
                    continue;
                }
                if (mount.pointDefence()) {
                    boolean ordnance = WarshipAirFire.scope(ship) != WarshipAirFire.Scope.NONE
                            && pointDefence.hasTarget(sim, ship, mount, shells, strikes);
                    if (!airFire.tickMount(dt, sim, ship, mount, ordnance)) {
                        pointDefence.tickMount(dt, sim, ship, mount, shells, strikes);
                    }
                } else if (!airFire.tickMount(dt, sim, ship, mount, false)
                        && !shipFire.tickMount(dt, this, ship, mount, ships)) {
                    tickMount(dt, sim, ship, mount);
                }
            }
        }
        // Hulls do not pass through one another, friend or foe.
        WarshipSpacing.separate(ships, dt);
        ships.removeIf(Warship::departed);
        pointDefence.ageMarks(dt);
        hitFx.tick(clock, dt, ships);
        interceptables.clear();
        for (WarshipShell s : shells) if (!s.intercepted && s.canBeIntercepted()) interceptables.add(s);
        for (WarheadStrike s : strikes) if (!s.intercepted) interceptables.add(s);
    }

    /**
     * Counts a bounded ship's station time down and, when it is spent, sends it
     * away: it hovers off the map along the line from the map's centre through
     * where it stands (its nose direction if it is at the centre) at its own speed,
     * and is removed once it has cleared the edge or, failing that, after
     * {@link Warship#DEPARTURE_LIMIT_SECONDS}. A ship whose side has an approved edge ({@code edge}) flies out across
     * that instead, and is never timed off the map short of {@link Warship#EDGE_EXIT_LIMIT_SECONDS}.
     */
    private static void depart(float dt, BattleSimulation sim, Warship ship, MapEdge edge) {
        boolean due = ship.spendStationTime(dt) && !ship.departing();
        if (due && edge != null) {
            ship.leaveAcross(edge, sim.getGrid().getWidth(), sim.getGrid().getHeight(), false);
        } else if (due) {
            ship.beginDeparture();
            float w = sim.getGrid().getWidth();
            float h = sim.getGrid().getHeight();
            float dx = ship.x - w * 0.5f;
            float dy = ship.y - h * 0.5f;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.01f) {
                double nose = Math.toRadians(ship.facingDegrees + 90f);
                dx = (float) Math.cos(nose);
                dy = (float) Math.sin(nose);
                len = 1f;
            }
            float reach = (float) Math.hypot(w, h) + ship.lengthCells;
            ship.stationTo(ship.x + dx / len * reach, ship.y + dy / len * reach);
        }
        if (!ship.departing()) return;
        ship.addDepartingSeconds(dt);
        if (ship.throughDeck() && ship.departingSeconds() >= WarshipAtmosphere.CLIMB_SECONDS) {
            // Into the cloud deck: gone from the battle without crossing an edge.
            ship.markPhysicallyDeparted();
            return;
        }
        float margin = Math.max(ship.lengthCells, Math.max(ship.lowHoverWidth(), ship.lowHoverHeight())) * 0.5f;
        boolean offMap = ship.x < -margin || ship.y < -margin
                || ship.x > sim.getGrid().getWidth() + margin || ship.y > sim.getGrid().getHeight() + margin;
        if (offMap) ship.markPhysicallyDeparted();
        else if (ship.departingSeconds() > (ship.leavingAcrossEdge() ? Warship.EDGE_EXIT_LIMIT_SECONDS
                : Warship.DEPARTURE_LIMIT_SECONDS)) {
            ship.markDeparted();
        }
    }

    /** How long a directed ship fires on ground that holds no hostile before it chooses again. */
    private static final float EMPTY_AREA_SECONDS = 2f;
    private static final float RETRY_SECONDS = 1f;
    /** A ship that has moved this far since choosing its heading chooses it again. */
    private static final float REHEADING_DISTANCE = 3f;
    /** A scripted flight counts a waypoint as passed within this many cells, and flies on without braking. */
    private static final float ROUTE_PASS_RADIUS = 3f;

    /**
     * A directed ship holds up to its limit of areas at once. Each is dropped once
     * it has held no hostile for a few seconds; a free slot is filled, densest
     * ground first, as soon as one is dropped and otherwise looked for once a
     * second. A ship under a manual order holds only that ground until it is empty.
     */
    private void direct(float dt, BattleSimulation sim, Warship ship) {
        if (!ship.isDirected()) return;
        boolean weighted = ship.profile().weighted() && !ship.manualOrder();
        WarshipDirector.Friendlies friendlies = weighted ? WarshipDirector.friendliesOf(sim, ship) : null;
        List<Warship.Held> current = ship.held();
        List<Warship.Held> kept = new ArrayList<>(current.size());
        boolean droppedForFriendlies = false;
        for (Warship.Held h : current) {
            if (h.denial) {
                // A denied landing zone is managed below from the inbound drops, not from who stands on it.
                kept.add(h);
                continue;
            }
            int count = hostilesWithin(sim, ship, h.area);
            if (count > 0) {
                h.emptySeconds = 0f;
                h.weight = count;
            } else {
                h.emptySeconds += dt;
            }
            if (h.emptySeconds >= EMPTY_AREA_SECONDS) continue;
            if (weighted && !ship.dangerClose() && endangersFriendlies(sim, ship, h.area, friendlies)) {
                // Friendlies walked into ground that was clean when it was chosen.
                droppedForFriendlies = true;
                continue;
            }
            kept.add(h);
        }
        boolean denialsChanged = ship.profile().denyLandings() && !ship.manualOrder()
                && updateDenials(sim, ship, kept, friendlies);
        boolean dropped = kept.size() != current.size() || denialsChanged;
        if (dropped) ship.setDirectedAreas(kept);
        if (ship.manualOrder() || kept.size() >= ship.directorMaxAreas()) {
            ship.resortByWeight();
            return;
        }
        // With ground to spare it looks again once a second, not every tick.
        ship.retrySeconds += dt;
        if (!dropped && ship.retrySeconds < RETRY_SECONDS) {
            ship.resortByWeight();
            return;
        }
        ship.retrySeconds = 0f;
        List<WarshipOrder.FireOnArea> avoid = new ArrayList<>(ship.areas());
        int slots = ship.directorMaxAreas() - kept.size();
        List<Warship.Held> fresh;
        if (weighted) {
            WarshipDirector.Selection picked = WarshipDirector.fillWeighted(sim, ship, ships, avoid, slots,
                    friendlies, ObjectiveBias.focus(sim, ship));
            fresh = picked.areas();
            ship.noteFriendlyPass(picked.refusedForFriendlies()
                    || (droppedForFriendlies && fresh.isEmpty() && kept.isEmpty()));
        } else {
            fresh = WarshipDirector.fill(sim, ship, ships, avoid, slots);
            ship.noteFriendlyPass(false);
        }
        if (!fresh.isEmpty()) {
            kept.addAll(fresh);
            ship.setDirectedAreas(kept);
        }
        ship.resortByWeight();
    }

    /** Whether a round aimed at any hostile in {@code area} would reach a friendly within the blast and its margin. */
    static boolean endangersFriendlies(BattleSimulation sim, Warship ship, WarshipOrder.FireOnArea area,
                                               WarshipDirector.Friendlies friendlies) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        float[] xs = new float[n];
        float[] ys = new float[n];
        int hostiles = 0;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            if (roster.isRiding(u)) continue;
            if (!ship.faction.hostileTo(roster.identity().faction(u))) continue;
            float ux = world.x(u);
            float uy = world.y(u);
            if (!area.contains(ux, uy)) continue;
            xs[hostiles] = ux;
            ys[hostiles] = uy;
            hostiles++;
        }
        float danger = WarshipDirector.blastCells(ship, area.radius()) + WarshipDirector.SAFETY_MARGIN_CELLS;
        return WarshipDirector.exposure(xs, ys, hostiles, area.x(), area.y(), area.radius(), friendlies, danger) > 0f;
    }

    /** Radius of a denied landing zone, as a share of the director's area radius, never under {@link #DENIAL_MIN_RADIUS}. */
    private static final float DENIAL_RADIUS_SHARE = 0.6f;
    private static final float DENIAL_MIN_RADIUS = 3f;
    private static final int DENIAL_AIM_ATTEMPTS = 6;

    /**
     * Keeps the ship's denied landing zones equal to the inbound hostile drops
     * worth firing on: the most valuable ones first (people and machines per
     * second to touchdown), each from the moment a round fired now would land at
     * touchdown until the boat has put everyone on the ground, and none that is
     * out of reach or has a friendly in the blast and its margin. {@code kept}
     * is rewritten in place; returns whether the set of denied zones changed.
     */
    private boolean updateDenials(BattleSimulation sim, Warship ship, List<Warship.Held> kept,
                                  WarshipDirector.Friendlies friendlies) {
        float reach = WarshipDirector.reach(ship);
        List<AirThreatPicture.InboundDrop> drops = new ArrayList<>(
                AirThreatPicture.take(sim, ship, ships).drops());
        drops.sort((a, b) -> {
            int byValue = Float.compare(b.value(), a.value());
            return byValue != 0 ? byValue : Long.compare(a.craftId(), b.craftId());
        });
        float radius = Math.max(DENIAL_MIN_RADIUS, ship.directorRadius() * DENIAL_RADIUS_SHARE);
        float danger = WarshipDirector.blastCells(ship, radius) + WarshipDirector.SAFETY_MARGIN_CELLS;
        List<Warship.Held> wanted = new ArrayList<>();
        int room = ship.directorMaxAreas();
        for (AirThreatPicture.InboundDrop drop : drops) {
            if (wanted.size() >= room) break;
            float gap = (float) Math.hypot(drop.lzX() - ship.x, drop.lzY() - ship.y);
            if (gap > reach) continue;
            float slowest = slowestFall(ship, drop.lzX(), drop.lzY());
            if (slowest < 0f || drop.etaSeconds() > slowest + DENIAL_LEAD_SECONDS + DENIAL_PREPARE_SECONDS) continue;
            if (!ship.dangerClose() && friendlies != null && WarshipDirector.exposure(
                    new float[]{drop.lzX()}, new float[]{drop.lzY()}, 1, drop.lzX(), drop.lzY(), radius,
                    friendlies, danger) > 0f) {
                continue;
            }
            Warship.Held existing = null;
            for (Warship.Held h : kept) {
                if (h.denial && h.area.x() == drop.lzX() && h.area.y() == drop.lzY()) existing = h;
            }
            if (existing == null) {
                existing = new Warship.Held(new WarshipOrder.FireOnArea(drop.lzX(), drop.lzY(), radius,
                        ship.directorBringsGunsToBear()), Math.max(1, drop.marines()));
                existing.denial = true;
            }
            existing.weight = Math.max(1, drop.marines());
            existing.denialEta = drop.etaSeconds();
            wanted.add(existing);
        }
        List<Warship.Held> before = new ArrayList<>();
        for (Warship.Held h : kept) if (h.denial) before.add(h);
        boolean same = before.size() == wanted.size() && before.containsAll(wanted);
        kept.removeIf(h -> h.denial);
        kept.addAll(0, wanted);
        return !same;
    }

    /**
     * The longest fall time, in seconds, among the shell mounts that can reach
     * {@code (lx, ly)}: the lead a zone's window must open with so every gun's
     * first round can be in the air by touchdown. {@code -1} when none reaches.
     */
    private static float slowestFall(Warship ship, float lx, float ly) {
        float slowest = -1f;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence() || m.weapon.beam()) continue;
            float dist = (float) Math.hypot(lx - ship.mountX(m), ly - ship.mountY(m));
            if (dist > WarshipScale.cells(m.weapon.rangePx())) continue;
            slowest = Math.max(slowest, dist / Math.max(1f, WarshipScale.cells(m.weapon.projectileSpeedPx())));
        }
        return slowest;
    }

    /**
     * Runs the ship's doctrine once a second: reads the threat picture, asks the
     * doctrine for a posture (unless a mission pinned one), takes it, and keeps
     * the station clear of declared capital hazards while the posture asks for
     * standoff.
     */
    private void doctrine(float dt, BattleSimulation sim, Warship ship) {
        ship.ageDoctrine(dt);
        if (ship.piloted()) {
            ship.doctrineClock = 0f;
            return;
        }
        ship.doctrineClock += dt;
        if (ship.doctrineClock < DOCTRINE_PERIOD_SECONDS) return;
        float elapsed = ship.doctrineClock;
        ship.doctrineClock = 0f;
        Warship escaped = ship.escortLead();
        if (escaped != null && escaped.physicallyDeparted() && !ship.departing()
                && ship.remoteOrder() == Warship.RemoteOrder.NONE) {
            // The lead it was escorting has escaped: the escort follows it out the way it went.
            MapEdge own = edgeFor(ship.faction);
            MapEdge out = own != null ? own : escaped.exitEdge();
            if (out != null) ship.leaveAcross(out, sim.getGrid().getWidth(), sim.getGrid().getHeight(), true);
            else if (escaped.throughDeck()) ship.climbThroughDeck(sim.getGrid().getWidth(), sim.getGrid().getHeight());
            else ship.climbOut(new float[][]{{escaped.x, escaped.y}}, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        }
        if (ship.remoteOrder() == Warship.RemoteOrder.NONE) escortUpkeep(ship);
        Warship lead = WarshipEscort.liveLead(ship);
        WarshipDoctrine doctrine = ship.doctrine();
        AirThreatPicture picture = null;
        if (lead != null || (ship.pinnedPosture() == null && doctrine != null)) {
            picture = AirThreatPicture.take(sim, ship, ships);
        }
        if (ship.pinnedPosture() == null && doctrine != null) {
            PostureInputs inputs = new PostureInputs(ship.hullFraction(), ship.fluxFraction(), ship.overloaded(),
                    ship.hasShield() && !ship.shieldUp(), picture.score(), picture.describe(),
                    picture.ownAir(), ship.stance(), foeHull(ship, ships));
            WarshipDoctrine.Decision decision = doctrine.assess(inputs, elapsed);
            ship.setPosture(decision.posture(), decision.reason());
            ship.fluxFooting(foeHull(ship, ships) >= 0f);
        }
        if (ship.remoteOrder() != Warship.RemoteOrder.NONE) return;
        if (lead != null) {
            keepStation(ship, lead, picture, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        } else if (ship.profile().standoff()) {
            float[] station = standoffStation(ship, ship.profile().standoffScale(), true, true);
            if (station != null) ship.standoffTo(station[0], station[1]);
        } else if (respectsBatteries(ship)) {
            // Any other posture still keeps clear of a battery that can reach it. The pushed station is worked out
            // from the station the mission gave, so it neither flaps nor strands the ship once the battery is silent.
            float[] station = standoffStation(ship, 1f, false, true);
            if (station != null) ship.standoffTo(station[0], station[1]);
            else ship.endStandoff();
        } else {
            ship.endStandoff();
        }
    }

    /** Whether a live battery envelope is a hazard this ship's doctrine keeps its station out of. */
    private boolean respectsBatteries(Warship ship) {
        return !batteryEnvelopes.isEmpty() && ship.hasDoctrine() && !ship.departing()
                && ship.posture() != WarshipPosture.WITHDRAW;
    }

    /**
     * Keeps the escort relation honest once a second: drops one whose lead has been lost (the escort goes back to its
     * own station and posture), and, when automatic escorts are on, gives a free small combat hull the flagship
     * as its lead. An explicit relation is never replaced by an automatic one.
     */
    private void escortUpkeep(Warship ship) {
        if (ship.departing()) return;
        if (ship.escortLead() != null && WarshipEscort.liveLead(ship) == null) ship.releaseEscort();
        if (autoEscort && ship.escortLead() == null) {
            Warship lead = WarshipEscort.autoLead(ship, ships);
            if (lead != null) {
                ship.useDoctrine();
                ship.setEscortLead(lead, false);
            }
        }
    }

    /** Stations an escort in line beside its lead, on the beam the lead's threats come from, beside its fellow escorts. */
    private void keepStation(Warship ship, Warship lead, AirThreatPicture picture, float mapWidth, float mapHeight) {
        if (ship.departing()) return;
        if (picture.hasThreatAxis()) {
            ship.escortAngle(WarshipEscort.bearingDegrees(picture.threatX() - lead.centerX(),
                    picture.threatY() - lead.centerY()));
        } else if (Float.isNaN(ship.escortAngle())) {
            ship.escortAngle(WarshipEscort.noseBearingDegrees(lead));
        }
        List<Warship> mates = WarshipEscort.escortsOf(lead, ships);
        int slot = Math.max(0, mates.indexOf(ship));
        float[] station = WarshipEscort.station(lead.centerX(), lead.centerY(), WarshipEscort.noseBearingDegrees(lead),
                WarshipEscort.ringRadius(lead, ship), ship.escortAngle(), slot, Math.max(1, mates.size()),
                WarshipEscort.lineSpacing(ship));
        // The screen stays over the battle: a lead flying off the edge is followed out only once it has gone.
        float margin = 0.5f * ship.lengthCells + WarshipDuelHelm.EDGE_MARGIN_CELLS;
        ship.escortTo(Math.max(margin, Math.min(mapWidth - margin, station[0])),
                Math.max(margin, Math.min(mapHeight - margin, station[1])));
    }

    private boolean autoEscort;

    /**
     * Whether free small combat hulls pick up the side's flagship as their lead on their own ({@code false}, the
     * default). Off by default because a station is the mission's: an automatic follow would override the stations
     * existing missions and scenes give their ships. A mission that wants a fleet to screen its flagship turns it on,
     * or names pairs with {@link Warship#escort}.
     */
    public void autoEscort(boolean on) {
        this.autoEscort = on;
    }

    public boolean autoEscort() {
        return autoEscort;
    }

    /** Distance beyond a hazard's radius a standing-off ship keeps: the standard margin times its scale, plus half the hull. */
    static float standoffClearance(float marginScale, float lengthCells) {
        return STANDOFF_MARGIN_CELLS * marginScale + lengthCells * 0.5f;
    }

    /**
     * Where to hover to be clear of every hazard hostile to the ship, or {@code null} when the station already is.
     * {@code declared} and {@code batteries} choose which hazards count; with batteries alone the search starts
     * from the station the mission gave, not from one a standoff already moved.
     */
    private float[] standoffStation(Warship ship, float marginScale, boolean declared, boolean batteries) {
        float[] origin = batteries && !declared ? ship.missionStation() : null;
        float sx = origin != null ? origin[0] : ship.hasStation() ? ship.stationX() : ship.x;
        float sy = origin != null ? origin[1] : ship.hasStation() ? ship.stationY() : ship.y;
        boolean moved = false;
        // The station is where the hull's pivot hovers; its bow or stern may sit a pivot's offset further out.
        float clear = standoffClearance(marginScale,
                ship.lengthCells + 2f * (float) Math.hypot(ship.pivotX, ship.pivotY));
        List<CapitalHazard> all = new ArrayList<>();
        if (declared) all.addAll(hazards);
        if (batteries) all.addAll(batteryEnvelopes.values());
        for (int pass = 0; pass < 3; pass++) {
            boolean pushed = false;
            for (CapitalHazard hazard : all) {
                if (!hazard.owner().hostileTo(ship.faction)) continue;
                float dx = sx - hazard.x();
                float dy = sy - hazard.y();
                float len = (float) Math.hypot(dx, dy);
                float need = hazard.radius() + clear;
                if (len >= need) continue;
                if (len < 0.01f) {
                    dx = 1f;
                    dy = 0f;
                    len = 1f;
                }
                sx = hazard.x() + dx / len * need;
                sy = hazard.y() + dy / len * need;
                pushed = true;
                moved = true;
            }
            if (!pushed) break;
        }
        return moved ? new float[]{sx, sy} : null;
    }

    /**
     * A ground installation fires one round of {@code weapon} at {@code target}, from {@code (mx, my)}. The round
     * is an ordinary shell in flight, so it is drawn, can be shot down by the hull's point defence and is met by the
     * shield first and the armour second; the only difference from a ship's own shot is that it lands on hulls
     * alone. The aim leads the hull by its drift over the flight, and the weapon's own cone is the miss.
     *
     * @return the flight time of the round in seconds, or {@code -1} when the weapon cannot be fired this way
     *         (a beam, a missile or point defence)
     */
    public float fireFromGround(VanillaWeapon weapon, Faction side, float mx, float my, Warship target) {
        if (weapon == null || weapon.beam() || weapon.missile() || weapon.pointDefenceOnly() || target.destroyed()) return -1f;
        float speed = Math.max(1f, WarshipScale.cells(weapon.projectileSpeedPx()));
        float cx = target.centerX();
        float cy = target.centerY();
        float flight = (float) Math.hypot(cx - mx, cy - my) / speed;
        float ax = cx + target.velocityX * flight;
        float ay = cy + target.velocityY * flight;
        float dx = ax - mx;
        float dy = ay - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float miss = dist * (float) Math.tan(Math.toRadians(weapon.spreadDegrees() * 0.5f))
                * (random.nextFloat() - 0.5f) * 2f;
        float lx = ax + (dist > 0f ? -dy / dist : 0f) * miss;
        float ly = ay + (dist > 0f ? dx / dist : 0f) * miss;
        float visual = Math.max(1f, WarshipScale.cells(weapon.explosionRadiusPx()));
        float blast = Math.max(1f, WarshipScale.cells(weapon.damageRadiusPx()));
        MissilePath path = MissilePath.straight(mx, my, lx, ly, speed);
        shells.add(new WarshipShell(weapon, side, null, mx, my, lx, ly, path, visual,
                weapon.damagePerShot(), penetration(weapon.damageType()), blast, null, null, 0f, null, 0f)
                .againstHullsOnly());
        if (cueAllowed(weapon, FIRE_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.FIRE, weapon.fireSoundId(), mx, my);
        return path.totalSeconds();
    }

    private static int hostilesWithin(BattleSimulation sim, Warship ship, WarshipOrder.FireOnArea area) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int count = 0;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (roster.isRiding(u)) continue;
            if (ship.faction.hostileTo(roster.identity().faction(u)) && area.contains(world.x(u), world.y(u))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Orders {@code ship} on a low pass from {@code (ax, ay)} to {@code (bx, by)}: the public seam for a
     * mission script, a scene or the debug panel (the pilot has {@link WarshipPilot#lowPass}). The ship runs
     * in to A at the pass speed first when it is not there, flies the line firing on whatever is near it,
     * and returns to its station or route. Returns false when the line is too short or the ship cannot
     * (see {@link Warship#canLowPass}); a refusal changes nothing.
     */
    public boolean orderLowPass(BattleSimulation sim, Warship ship, float ax, float ay, float bx, float by) {
        LowPass plan = LowPass.plan(ship.x, ship.y, ax, ay, bx, by, sim.getGrid().getWidth(), sim.getGrid().getHeight());
        return plan != null && ship.beginLowPass(plan);
    }

    /**
     * Flies a ship's low pass one step, when it has one. The hull runs in to A at the pass speed, flies the
     * line without braking, and fires through a circle laid ahead of it on the line (a hand order, so no
     * posture or friendly check refuses it); at B the pass ends, the guns' ground is given back, and the
     * ordinary movement (the station, the route or the pilot) resumes from the speed the hull has. Returns
     * whether the pass moved the hull this tick.
     */
    private boolean lowPass(float dt, BattleSimulation sim, Warship ship) {
        LowPass pass = ship.lowPass();
        if (pass == null) return false;
        if (!pass.active()) {
            pass.advance(dt, ship.x, ship.y);
            if (pass.spent()) ship.clearLowPass();
            return false;
        }
        if (pass.seconds() == 0f) {
            atmosphereCue(WarshipSoundCue.Kind.LOW_PASS_ROAR, LOW_PASS_ROAR_SOUND, ship);
        }
        if (pass.onLine() && !ship.lowPassSilenced()) {
            float[] centre = pass.fireCentre(ship.x, ship.y);
            ship.layPassFire(centre[0], centre[1], pass.fireRadius());
        }
        float[] to = pass.steerPoint(ship.x, ship.y);
        float dx = to[0] - ship.x;
        float dy = to[1] - ship.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = LowPass.passSpeed(ship.maxSpeed);
        float wantX = dist > 0f ? dx / dist * speed : 0f;
        float wantY = dist > 0f ? dy / dist * speed : 0f;
        float ax = wantX - ship.velocityX;
        float ay = wantY - ship.velocityY;
        float change = (float) Math.sqrt(ax * ax + ay * ay);
        float allowed = LowPass.passAcceleration(ship.acceleration) * dt;
        if (change > allowed && change > 0f) {
            ax *= allowed / change;
            ay *= allowed / change;
        }
        ship.velocityX += ax;
        ship.velocityY += ay;
        float width = sim.getGrid().getWidth();
        float height = sim.getGrid().getHeight();
        ship.x = Math.max(0f, Math.min(width, ship.x + ship.velocityX * dt));
        ship.y = Math.max(0f, Math.min(height, ship.y + ship.velocityY * dt));
        pass.advance(dt, ship.x, ship.y);
        if (pass.onLine() && pass.takeStartSound()) {
            atmosphereCue(WarshipSoundCue.Kind.LOW_PASS_REPORT, LOW_PASS_REPORT_SOUND, ship);
        }
        if (!pass.active()) {
            ship.finishLowPass();
            if (ship.climbsAfterPass()) ship.climbThroughDeck(width, height);
        }
        return true;
    }

    /** The heading a ship holds on a pass: along the line once on it, toward A while running in. */
    private static float passHeading(LowPass pass, Warship ship) {
        if (pass.onLine()) return pass.headingDegrees();
        return AirBody.facingToward(pass.startX() - ship.x, pass.startY() - ship.y);
    }

    /**
     * Hovers the hull toward its station at its own speed: it accelerates, cruises
     * and brakes in time to stop on the spot rather than drifting through it. Near a map edge its wanted course
     * carries the edge pressure ({@link WarshipEdgeHelm#steer}).
     */
    private static void moveHull(float dt, BattleSimulation sim, Warship ship, WarshipEdgeHelm edges) {
        float beforeX = ship.x, beforeY = ship.y;
        if (ship.piloted()) {
            WarshipHelm.drive(ship, dt, sim.getGrid().getWidth(), sim.getGrid().getHeight());
            enforceLowHoverClearance(sim, ship, beforeX, beforeY);
            return;
        }
        if (!ship.hasStation()) return;
        ship.advanceRoute(ROUTE_PASS_RADIUS);
        float tx = ship.stationX();
        float ty = ship.stationY();
        List<Warship> fleet = sim.warships().ships();
        boolean keepsClear = keepsClear(ship);
        if (keepsClear) {
            // A ship fighting hulls or screening keeps its own beam of open air from every friendly hull.
            float[] clear = WarshipSpacing.clearStation(ship, fleet, tx, ty);
            // A station the mission or helm kept on the map stays on it once cleared.
            float w = sim.getGrid().getWidth();
            float h = sim.getGrid().getHeight();
            float margin = 0.5f * ship.lengthCells;
            if (tx >= margin && tx <= w - margin) clear[0] = Math.max(margin, Math.min(w - margin, clear[0]));
            if (ty >= margin && ty <= h - margin) clear[1] = Math.max(margin, Math.min(h - margin, clear[1]));
            tx = clear[0];
            ty = clear[1];
        }
        Warship screened = keepsClear && WarshipSpacing.ENABLED && WarshipEscort.CLEARS_CHARGE
                ? WarshipEscort.liveLead(ship) : null;
        if (screened != null && WarshipSpacing.obstructs(screened)) {
            // A screen changing sides comes round its charge rather than across it.
            float[] round = WarshipEscort.roundCharge(ship, screened, tx, ty);
            tx = round[0];
            ty = round[1];
        }
        float dx = tx - ship.x;
        float dy = ty - ship.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        boolean flying = keepsClear && ship.manoeuvre().kind() != null
                && ship.manoeuvre().role() != WarshipManoeuvre.Role.NONE;
        float top = flying ? ship.maxSpeed * ship.manoeuvre().speedShare : ship.maxSpeed;
        float speed = ship.isPassingThrough() ? ship.maxSpeed
                : Math.min(top, (float) Math.sqrt(2f * ship.deceleration * dist));
        float wantX = dist > 0f ? dx / dist * speed : 0f;
        float wantY = dist > 0f ? dy / dist * speed : 0f;
        Warship charge = keepsClear ? WarshipEscort.liveLead(ship) : null;
        if (charge != null && !charge.isPassingThrough()) {
            // A screen's station moves with its charge: the escort keeps its charge's way on as well as closing on
            // its place, so it holds the place instead of trailing it into the charge's side.
            wantX += charge.velocityX;
            wantY += charge.velocityY;
            float s = (float) Math.hypot(wantX, wantY);
            if (s > top && s > 0f) {
                wantX *= top / s;
                wantY *= top / s;
            }
        }
        if (keepsClear) {
            if (flying) {
                // A ship flying a manoeuvre travels along its nose, as a ship does.
                float[] held = WarshipTactics.alongNose(ship, wantX, wantY);
                wantX = held[0];
                wantY = held[1];
            }
            // Keeping clear of another hull is the one thing that may move a ship off its nose.
            // Looking no further ahead than the station: a course that stops short of a hull is not closing on it.
            float wantSpeed = (float) Math.hypot(wantX, wantY);
            float horizon = wantSpeed > 1e-3f ? dist / wantSpeed : 0f;
            float[] bent = WarshipSpacing.avoid(ship, fleet, wantX, wantY, horizon);
            if (flying && !Float.isNaN(ship.manoeuvre().heading)
                    && (bent[0] != wantX || bent[1] != wantY) && Math.hypot(bent[0], bent[1]) > 0.2f * ship.maxSpeed) {
                // and the helm turns the bow onto the clear course rather than crabbing along it.
                ship.manoeuvre().heading = WarshipFacing.bearing(0f, 0f, bent[0], bent[1]);
            }
            wantX = bent[0];
            wantY = bent[1];
        }
        // The map's edges come last: an edge is not something a hull is shouldered past, as another hull may be.
        float[] pressed = edges.steer(ship, wantX, wantY);
        if (pressed[0] != wantX || pressed[1] != wantY) {
            // A manoeuvre's bow comes round onto the course the pressure leaves, so the ship turns away from the edge
            // rather than crabbing along it.
            if (flying && !Float.isNaN(ship.manoeuvre().heading)
                    && Math.hypot(pressed[0], pressed[1]) > 0.2f * ship.maxSpeed) {
                ship.manoeuvre().heading = WarshipFacing.bearing(0f, 0f, pressed[0], pressed[1]);
            }
            wantX = pressed[0];
            wantY = pressed[1];
        }
        float ax = wantX - ship.velocityX;
        float ay = wantY - ship.velocityY;
        float change = (float) Math.sqrt(ax * ax + ay * ay);
        // A hull still carrying a low pass's speed sheds it at the pass's own handling, not at a hover's.
        boolean burning = ship.lowPass() != null && ship.speed() > ship.maxSpeed * 1.05f;
        float allowed = (burning ? LowPass.passAcceleration(ship.acceleration) : ship.acceleration) * dt;
        if (change > allowed && change > 0f) {
            ax *= allowed / change;
            ay *= allowed / change;
        }
        ship.velocityX += ax;
        ship.velocityY += ay;
        float stepX = ship.velocityX * dt;
        float stepY = ship.velocityY * dt;
        if (dist <= (float) Math.sqrt(stepX * stepX + stepY * stepY) && speed < allowed * 2f) {
            ship.x = tx;
            ship.y = ty;
            ship.velocityX = 0f;
            ship.velocityY = 0f;
        } else {
            ship.x += stepX;
            ship.y += stepY;
        }
        enforceLowHoverClearance(sim, ship, beforeX, beforeY);
    }

    /**
     * Whether a ship's helm keeps clear of other hulls: one fighting a hull with its duel helm, escorting, or leaving. A
     * ship on a mission station, a route or a pass is placed where the mission wants it.
     */
    private static boolean keepsClear(Warship ship) {
        if (ship.fighterSize() || ship.piloted() || ship.isPassingThrough() || ship.lowPass() != null
                || ship.lowHovering()) {
            return false;
        }
        return ((ship.hullTarget() != null || WarshipTactics.overloadedInFight(ship)) && ship.duelHelm())
                || WarshipEscort.liveLead(ship) != null || ship.departing();
    }

    private static void enforceLowHoverClearance(BattleSimulation sim, Warship ship, float x, float y) {
        if (ship.lowHovering() && !LowHoverClearance.sweptClear(sim.getGrid(), x, y, ship.x, ship.y,
                ship.lowHoverWidth(), ship.lowHoverHeight())) {
            ship.x = x;
            ship.y = y;
            ship.velocityX = 0f;
            ship.velocityY = 0f;
        }
    }

    /**
     * Turns the hull toward the heading its order wants, at the hull's own rate:
     * it accelerates toward the turn, cruises at its maximum and brakes in time
     * to stop on the heading rather than swinging through it.
     */
    /** A wanted heading moving more than this in one tick is a fresh choice, not a drift a helm leads. */
    static final float MAX_LED_DRIFT_DEGREES_PER_TICK = 1f;

    private static void turnHull(float dt, Warship ship) {
        if (ship.lowHovering()) return;
        if (ship.piloted()) {
            WarshipHelm.steer(ship);
        } else {
            chooseHeading(ship);
        }
        LowPass pass = ship.lowPass();
        if (pass != null && pass.active()) ship.desiredHeading = passHeading(pass, ship);
        float error = WarshipFacing.wrap(ship.desiredHeading - ship.facingDegrees);
        // A heavy hull settles onto a heading slowly, so a heading that drifts steadily (a target sliding across the
        // bow) is led: the helm adds the drift's own rate, or the hull would trail it by more than a hardpoint's arc.
        // A heading that jumps (a fresh choice) is not a drift and is not led.
        float drift = 0f;
        if (!Float.isNaN(ship.previousDesiredHeading)) {
            float moved = WarshipFacing.wrap(ship.desiredHeading - ship.previousDesiredHeading);
            if (Math.abs(moved) <= MAX_LED_DRIFT_DEGREES_PER_TICK) drift = moved / dt;
        }
        ship.previousDesiredHeading = ship.desiredHeading;
        float wanted = Math.max(-ship.maxTurnDegPerSec, Math.min(ship.maxTurnDegPerSec, drift + Math.signum(error)
                * (float) Math.sqrt(2f * ship.turnAccelDegPerSec2 * Math.abs(error))));
        float step = ship.turnAccelDegPerSec2 * dt;
        ship.turnVelocity += Math.max(-step, Math.min(step, wanted - ship.turnVelocity));
        float move = ship.turnVelocity * dt;
        if (Math.abs(error) <= Math.abs(move) && Math.abs(ship.turnVelocity - drift) < step * 2f) {
            ship.facingDegrees = ship.desiredHeading;
            ship.turnVelocity = Math.max(-ship.maxTurnDegPerSec, Math.min(ship.maxTurnDegPerSec, drift));
        } else {
            ship.facingDegrees = WarshipFacing.wrap(ship.facingDegrees + move);
        }
    }

    /** Chooses the heading an unpiloted ship wants: the one that brings its guns to bear, or along its route. */
    private static void chooseHeading(Warship ship) {
        if (ship.liftRunning() && Math.hypot(ship.velocityX, ship.velocityY) > 0.05f) {
            // Off the berth and round the climbing turn, the nose follows the run before anything else claims it.
            ship.desiredHeading = AirBody.facingToward(ship.velocityX, ship.velocityY);
            return;
        }
        if (ship.leavingAcrossEdge() && Math.hypot(ship.velocityX, ship.velocityY) > 0.05f) {
            // Flying out across its edge, a ship goes nose first; what bears on a pursuer from there still fires.
            ship.desiredHeading = AirBody.facingToward(ship.velocityX, ship.velocityY);
            return;
        }
        List<Warship.Held> held = ship.held();
        boolean bring = !held.isEmpty() && held.get(0).area.bringGunsToBear();
        Warship hull = ship.hullTarget();
        if (hull == null && WarshipTactics.overloadedInFight(ship)) hull = ship.manoeuvre().target();
        if (hull != null && !bring) {
            // Fighting a hull: the manoeuvre's course where it sets one, else the heading that brings the guns to bear.
            float course = ship.manoeuvre().heading;
            if (!Float.isNaN(course)) {
                ship.desiredHeading = course;
                return;
            }
            float[] aim = WarshipShipRules.aimPoint(ship, hull);
            ship.desiredHeading = WarshipShipRules.bestHeading(ship, aim[0], aim[1]);
            return;
        }
        if (bring && (!ship.headingChosen() || Math.hypot(ship.x - ship.headingChosenAtX,
                        ship.y - ship.headingChosenAtY) > REHEADING_DISTANCE)) {
            int k = held.size();
            float[] tx = new float[k];
            float[] ty = new float[k];
            float[] weight = new float[k];
            for (int i = 0; i < k; i++) {
                tx[i] = held.get(i).area.x();
                ty[i] = held.get(i).area.y();
                weight[i] = Math.max(1, held.get(i).weight);
            }
            ship.desiredHeading = WarshipFacing.bestHeading(ship, tx, ty, weight);
            ship.markHeadingChosen();
        } else if (ship.isPassingThrough() && !bring
                && Math.hypot(ship.velocityX, ship.velocityY) > 0.05f) {
            // Flying a route with no instruction to bring guns to bear, it points along its travel.
            ship.desiredHeading = AirBody.facingToward(ship.velocityX, ship.velocityY);
        } else if (!bring && WarshipEscort.liveLead(ship) != null) {
            // A screen with nothing to fight keeps its charge's course, in line beside it.
            ship.desiredHeading = WarshipEscort.liveLead(ship).facingDegrees;
        }
    }

    /**
     * Lets a landing detonation hurt any hostile hull whose footprint its blast
     * reaches, through the hull's armour rule. A ship's own side never hurts it.
     * A hull taken to nothing goes up in a heavy blast and is removed from the
     * battle on the next tick, so it stops firing at once.
     *
     * <p>A ground event (a wreck striking and skidding, an airframe or a
     * magazine going up: {@link PendingDetonation#isGroundEvent}) happens on the
     * ground, not in the air above it, and never reaches a hull. Without this a
     * falling hull's crash and skid ground into whatever hostile hull hovered
     * over the place it came down.
     */
    public void onDetonation(PendingDetonation det, BattleSimulation sim) {
        if (det.isGroundEvent()) return;
        strikeHulls(det);
    }

    /**
     * Lands {@code det} on every hostile hull its blast reaches, shield first and then the armour rule, and says
     * whether it reached any (a shield's absorption counts). A hull it takes to nothing falls on the next tick.
     */
    boolean strikeHulls(PendingDetonation det) {
        return strikeHulls(det, Float.NaN, Float.NaN);
    }

    /**
     * {@link #strikeHulls(PendingDetonation)} for a round fired from {@code (fromX, fromY)}: it enters each hull it
     * reaches from that side, so the shield is asked of that side and the plate on that side is what it meets
     * ({@link WarshipPlating}). NaN for a round with no source.
     */
    boolean strikeHulls(PendingDetonation det, float fromX, float fromY) {
        return strikeHulls(det, fromX, fromY, null);
    }

    /**
     * {@link #strikeHulls(PendingDetonation, float, float)} for a round one hull fired at another, of the game's
     * {@code damageType}: it meets the shield and the armour as the base game's damage types do
     * ({@link #shieldMultiplier}, {@link #armourMultiplier}), with the armour rule at the base game's own reduction
     * rather than the ground's penetration scale. {@code null} is any other round, which keeps that scale.
     */
    boolean strikeHulls(PendingDetonation det, float fromX, float fromY, String damageType) {
        return strikeHulls(det, fromX, fromY, damageType, null);
    }

    /**
     * {@link #strikeHulls(PendingDetonation, float, float, String)} for a round of {@code weapon}, whose look the hit
     * is drawn with ({@link WarshipHitFx}); null for a round with none.
     */
    boolean strikeHulls(PendingDetonation det, float fromX, float fromY, String damageType, VanillaWeapon weapon) {
        return strikeHulls(det, fromX, fromY, damageType, weapon, null);
    }

    /**
     * {@link #strikeHulls(PendingDetonation, float, float, String, VanillaWeapon)} whose blast reaches every hull a
     * round can meet but {@code shooter}, friend or foe, as a round between hulls does once it can meet the first hull
     * on its way ({@link WarshipRoundSweep}); {@code null} keeps it to hulls hostile to the round's side.
     */
    boolean strikeHulls(PendingDetonation det, float fromX, float fromY, String damageType, VanillaWeapon weapon,
                        Warship shooter) {
        boolean reached = false;
        strikeX = Float.NaN;
        strikeY = Float.NaN;
        strikeShieldOnly = true;
        struck.clear();
        for (Warship ship : ships) {
            if (ship.destroyed()) continue;
            if (shooter == null ? !ship.faction.hostileTo(det.shooterFaction)
                    : ship == shooter || !WarshipRoundSweep.meets(ship)) continue;
            float gap = ship.distanceTo(det.endpointX, det.endpointY);
            if (ship.hasShield() && gap <= det.aoeRadius + WarshipShield.THREAT_MARGIN_CELLS
                    && threatens(ship, det.damage, det.penetration, fromX, fromY, det.endpointX, det.endpointY)) {
                // A landing this close is itself the warning: the shield rises for the next one.
                ship.shieldModel().noteThreat();
            }
            if (gap > det.aoeRadius) continue;
            reached = true;
            boolean shield = ship.shieldCoversFrom(fromX, fromY, det.endpointX, det.endpointY);
            float[] at = shield ? shieldPoint(ship, fromX, fromY, det.endpointX, det.endpointY) : null;
            strikeOne(ship, det, fromX, fromY, det.endpointX, det.endpointY, damageType, weapon, shield, at);
        }
        return reached;
    }

    /**
     * Lands a round between hulls on the hull it met on its way ({@link WarshipRoundSweep.Contact}): on the shield's
     * bubble where it crossed it, or on the plate cell its line enters. {@code (fromX, fromY)} is behind the contact
     * along the round's travel, the side it came from.
     */
    void strikeContact(WarshipRoundSweep.Contact c, PendingDetonation det, float fromX, float fromY,
                       String damageType, VanillaWeapon weapon) {
        strikeX = Float.NaN;
        strikeY = Float.NaN;
        strikeShieldOnly = true;
        struck.clear();
        Warship ship = c.ship();
        if (ship.hasShield()) ship.shieldModel().noteThreat();
        strikeOne(ship, det, fromX, fromY, c.x(), c.y(), damageType, weapon, c.shield(), new float[]{c.x(), c.y()});
    }

    /** One hull takes {@code det} at {@code (px, py)}: on its shield at {@code shieldAt}, or on the plate it enters. */
    private void strikeOne(Warship ship, PendingDetonation det, float fromX, float fromY, float px, float py,
                           String damageType, VanillaWeapon weapon, boolean shield, float[] shieldAt) {
        struck.add(ship);
        if (shield) {
            // The shield takes the hit as flux; the hull takes nothing.
            ship.shieldModel().absorb(damageType == null ? det.damage
                    : det.damage * shieldMultiplier(damageType), ship.fluxPool());
            if (Float.isNaN(strikeX) && shieldAt != null && ship.plating() != null) {
                strikeX = shieldAt[0];
                strikeY = shieldAt[1];
            }
            if (shieldAt != null) {
                hitFx.shieldHit(ship, shieldAt[0], shieldAt[1], fromX, fromY, damageType, weapon, det.damage, clock);
            }
            return;
        }
        strikeShieldOnly = false;
        int cell = ship.entryCell(fromX, fromY, px, py);
        int breachesBefore = ship.plating() == null ? 0 : ship.plating().breachCount();
        if (damageType == null) ship.takeHit(cell, det.damage, det.penetration, clock);
        else ship.takeHullFightHit(cell, det.damage * armourMultiplier(damageType), clock);
        boolean breached = ship.plating() != null && ship.plating().breachCount() != breachesBefore;
        float hx = cell >= 0 ? ship.cellX(cell) : px;
        float hy = cell >= 0 ? ship.cellY(cell) : py;
        hitFx.hullHit(ship, hx, hy, fromX, fromY, damageType, weapon, det.damage, breached, clock);
        // A gun stands on the plate it was bolted to: a slot holed through takes its mount with it.
        if (breached) knockOutMounts(ship);
        if (Float.isNaN(strikeX)) {
            strikeX = hx;
            strikeY = hy;
        }
    }

    /** The hulls the last {@link #strikeHulls} or {@link #strikeContact} call reached, in the order it reached them. */
    private final List<Warship> struck = new ArrayList<>();

    /** Where the last {@link #strikeHulls} call met a hull or a shield, for its blast; NaN when it met none or has no place. */
    private float strikeX = Float.NaN;
    private float strikeY = Float.NaN;

    /** Whether every hull the last {@link #strikeHulls} call reached took it on a shield, so its blast is the shield's. */
    private boolean strikeShieldOnly;

    /** Silences the mounts standing on newly breached plate, and marks each one that went. */
    private void knockOutMounts(Warship ship) {
        List<Warship.Mount> standing = new ArrayList<>();
        for (Warship.Mount m : ship.mounts()) if (!m.knockedOut()) standing.add(m);
        if (ship.knockOutBreachedMounts(clock) == 0) return;
        for (Warship.Mount m : standing) {
            if (m.knockedOut()) hitFx.mountLost(ship, ship.mountX(m), ship.mountY(m), clock);
        }
    }

    /** The point on a shield's bubble toward where a round came from (where it landed, with no source); null for none. */
    private static float[] shieldPoint(Warship ship, float fromX, float fromY, float px, float py) {
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float tx = Float.isFinite(fromX) ? fromX : px;
        float ty = Float.isFinite(fromY) ? fromY : py;
        float dx = tx - cx;
        float dy = ty - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return null;
        float r = Math.min(len, ship.shieldRadiusCells());
        return new float[]{cx + dx / len * r, cy + dy / len * r};
    }

    /** X of where the last hull strike landed on a hull or shield, or {@code fallback} when it had no place. */
    float strikeX(float fallback) {
        return Float.isNaN(strikeX) ? fallback : strikeX;
    }

    /** Y of where the last hull strike landed on a hull or shield, or {@code fallback} when it had no place. */
    float strikeY(float fallback) {
        return Float.isNaN(strikeY) ? fallback : strikeY;
    }

    /** Plays a weapon's fire sound for a ship-versus-ship round, thinned as every other gun's is. */
    void fireCue(Warship.Mount m, VanillaWeapon w, float x, float y) {
        if (cueAllowed(m, FIRE_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.FIRE, w.fireSoundId(), x, y);
    }

    /**
     * A hull has been taken to nothing: it bursts where it is (the heavy blast it has always made, drawn on the
     * ground beneath it) and starts to fall. Its guns, shield and point defence are gone with it.
     */
    private void startWreck(BattleSimulation sim, Warship ship) {
        ledger.destroyed(ship, clock, ship.x, ship.y);
        sim.spawnHeavyImpact(ship.x, ship.y, Math.max(6f, ship.lengthCells * 0.5f));
        wrecks.add(new WarshipWreck(ship, WRECK_KEY_BASE - ship.serial()));
    }

    /** Falls, strikes and skids every wreck; the strike and the skid are the aircraft crash's, at the hull's size. */
    private void tickWrecks(float dt, BattleSimulation sim) {
        for (WarshipWreck wreck : wrecks) {
            float before = wreck.seconds();
            WarshipWreck.Step step = wreck.advance(dt);
            Warship ship = wreck.ship;
            float tally = wreck.crushRadius() + GROUND_TALLY_MARGIN_CELLS;
            switch (step) {
                case FALLING -> {
                    if ((int) (before / WRECK_FIRE_PERIOD_SECONDS) != (int) (wreck.seconds() / WRECK_FIRE_PERIOD_SECONDS)) {
                        float drawn = 0.5f * Math.max(ship.lengthCells, ship.widthCells) * wreck.bodyScale();
                        sim.spawnFallingWreckFire(ship.x, ship.y, Math.max(2f, drawn * 0.6f));
                    }
                }
                case STRUCK -> {
                    ledger.struck(ship, ship.x, ship.y);
                    if (onMap(sim, ship.x, ship.y)) {
                        Map<Long, Float> under = groundNear(sim, ship.x, ship.y, tally);
                        sim.crashHullWreck(wreck.key, ship.x, ship.y, ship.faction, wreck.crushRadius(), wreck.skidRadius(),
                                ship.velocityX, ship.velocityY);
                        tallyGround(sim, under, WarshipDuelLedger.Cause.WRECKAGE);
                        cue(WarshipSoundCue.Kind.IMPACT, ARRIVAL_SETTLE_SOUND, ship.x, ship.y);
                    }
                }
                case SKIDDING, CAME_TO_REST -> {
                    if (onMap(sim, ship.x, ship.y)) {
                        Map<Long, Float> under = groundNear(sim, ship.x, ship.y, tally);
                        sim.skidWreck(wreck.key, ship.x, ship.y, step == WarshipWreck.Step.CAME_TO_REST);
                        tallyGround(sim, under, WarshipDuelLedger.Cause.WRECKAGE);
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Flies a ship fighting a hull: its station moves alongside the target, on its beam at the range its guns want
     * ({@link WarshipDuelHelm}). The mission's station is kept and given back when the fight ends (no hull
     * to fight, the order withdrawn) or the ship is taken over (piloted, departing, on a route or a low pass).
     */
    private static void duelHelm(BattleSimulation sim, Warship ship, WarshipTactics tactics, float now,
                                 WarshipEdgeHelm edges) {
        ship.manoeuvre().heading = Float.NaN;
        ship.manoeuvre().speedShare = 1f;
        Warship target = ship.hullTarget();
        // Overloaded, a ship holds no fire target, but it still flies the fight it is in.
        if (target == null && WarshipTactics.overloadedInFight(ship)) target = ship.manoeuvre().target();
        Warship lead = WarshipEscort.liveLead(ship);
        boolean free = !ship.piloted() && !ship.departing() && !ship.isPassingThrough() && ship.lowPass() == null
                && !ship.descending()
                && ship.remoteOrder() == Warship.RemoteOrder.NONE;
        if (lead != null) {
            // An escort screens by fighting: with a hull to fight it takes its own duel station, held on a leash to
            // its charge; with none, its place in the screen line (kept by the posture tick) stands.
            if (target != null && ship.duelHelm() && free) {
                float[] at = duelStation(sim, ship, target, lead, edges);
                ship.stationTo(at[0], at[1]);
            }
            return;
        }
        if (target == null || !ship.duelHelm() || !free) {
            float[] home = ship.duelHome();
            if (home != null) {
                ship.duelHome(null);
                if (free && !Float.isNaN(home[0])) ship.stationTo(home[0], home[1]);
            }
            return;
        }
        if (!ship.manoeuvring()) {
            ship.duelHome(ship.hasStation() ? new float[]{ship.stationX(), ship.stationY()}
                    : new float[]{Float.NaN, Float.NaN});
        }
        // A hunter flies its group's manoeuvre; a ship with none flies the single-ship duel station.
        float[] at = tactics.fly(ship, target, now, edges, sim.warships().ships());
        if (at == null) at = duelStation(sim, ship, target, null, edges);
        ship.stationTo(at[0], at[1]);
    }

    /** The duel station: a bow ship's quiet quarter or a broadside ship's beam, at the standoff law's range. */
    private static float[] duelStation(BattleSimulation sim, Warship ship, Warship target, Warship lead,
                                       WarshipEdgeHelm edges) {
        float mine = WarshipDuelHelm.batteryReach(ship);
        float theirs = WarshipDuelHelm.batteryReach(target);
        float range = WarshipDuelHelm.preferredRange(mine, theirs, 0.5f * ship.lengthCells, 0.5f * target.lengthCells);
        // Which beam a ship dead ahead or astern takes is the hull's own, by its serial, so a pair does not mirror.
        float[] at = WarshipDuelHelm.bowFighter(ship)
                ? WarshipDuelHelm.bowStation(ship, target, range, edges)
                : WarshipDuelHelm.station(ship.x, ship.y, target.centerX(), target.centerY(),
                        WarshipEscort.noseBearingDegrees(target), range, ship.serial() % 2 == 0, ship, edges);
        if (lead == null) return at;
        if (WarshipTactics.ENABLED) {
            // A screen interposes: in line between its charge and what it is fighting.
            float[] p = WarshipTactics.interpose(ship, lead, target, WarshipEscort.escortsOf(lead, sim.warships().ships()));
            return edges.settle(ship, p[0], p[1]);
        }
        float[] held = WarshipDuelHelm.leashed(at, lead.centerX(), lead.centerY(), 0.5f * lead.lengthCells + mine * WarshipDuelHelm.ESCORT_LEASH_REACH_SHARE);
        return edges.settle(ship, held[0], held[1]);
    }

    /** Whether a hostile hull is over the battle for {@code ship}: alive and not yet gone, a climbing one included. */
    private boolean hostileHullOver(Warship ship) {
        for (Warship other : ships) {
            if (other != ship && !other.destroyed() && !other.departed() && ship.faction.hostileTo(other.faction)) {
                return true;
            }
        }
        return false;
    }

    /** Cells beyond a blast's own radius within which the duel ledger looks for whom it hurt. */
    private static final float GROUND_TALLY_MARGIN_CELLS = 1.5f;

    /** The live ground units within {@code radius} of {@code (x, y)}, with their hit points, for the duel ledger. */
    private static Map<Long, Float> groundNear(BattleSimulation sim, float x, float y, float radius) {
        Map<Long, Float> out = new LinkedHashMap<>();
        World world = sim.world();
        float r2 = radius * radius;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.isRiding(id)) continue;
            float dx = world.x(id) - x;
            float dy = world.y(id) - y;
            if (dx * dx + dy * dy <= r2) out.put(id, world.hp(id));
        }
        return out;
    }

    /** Counts into the duel ledger whom a landing just hurt or killed, by comparing with {@link #groundNear}. */
    private void tallyGround(BattleSimulation sim, Map<Long, Float> before, WarshipDuelLedger.Cause cause) {
        for (Map.Entry<Long, Float> e : before.entrySet()) {
            long id = e.getKey();
            boolean alive = sim.getRoster().isAliveById(id);
            if (alive && sim.world().hp(id) >= e.getValue()) continue;
            Faction side = sim.identity().faction(id);
            if (side != null) ledger.ground(cause, side, !alive);
        }
    }

    private static boolean onMap(BattleSimulation sim, float x, float y) {
        return x >= 1f && y >= 1f && x < sim.getGrid().getWidth() - 1 && y < sim.getGrid().getHeight() - 1;
    }

    /** Whether a round of this strength landing at {@code (px, py)} would hurt the hull and comes from inside the shield's arc. */
    private static boolean threatens(Warship ship, float damage, float penetration, float px, float py) {
        return threatens(ship, damage, penetration, Float.NaN, Float.NaN, px, py);
    }

    /**
     * Whether a round fired from {@code (fromX, fromY)} (NaN when it has no source) that lands at {@code (px, py)}
     * would hurt the hull from inside the shield's arc. Asked of the side it came from, as absorption is: a round
     * aimed at a hull's sprite centre lands ahead of a capital's shield centre whichever side it was fired from,
     * and judged by the landing every round read as head-on and kept a front shield raised against fire from astern.
     */
    private static boolean threatens(Warship ship, float damage, float penetration, float fromX, float fromY,
                                     float px, float py) {
        return Warship.effectiveDamage(damage, penetration, ship.armour) > 0f
                && ship.arcCoversFrom(fromX, fromY, px, py);
    }

    /**
     * Advances the ship's shield. An automatic shield rises when a hostile round
     * that would hurt the hull is about to land near it from inside the arc: a
     * detonation in the queue, a projectile in flight or a hostile warship's shell.
     */
    private void tickShield(float dt, BattleSimulation sim, Warship ship) {
        boolean threat = ship.hasShield() && ship.shieldAuto() && sensesThreat(sim, ship);
        ship.shieldModel().update(dt, ship.fluxPool(), threat);
    }

    private boolean sensesThreat(BattleSimulation sim, Warship ship) {
        for (PendingDetonation d : sim.getInflightDetonations()) {
            if (d == null || !ship.faction.hostileTo(d.shooterFaction)) continue;
            if (nearHull(ship, d.endpointX, d.endpointY, d.aoeRadius)
                    && threatens(ship, d.damage, d.penetration, d.endpointX, d.endpointY)) return true;
        }
        for (Projectile p : sim.getActiveProjectiles()) {
            PendingDetonation d = p.onArrival;
            if (p.intercepted || d == null || !ship.faction.hostileTo(p.shooterFaction)) continue;
            if (nearHull(ship, p.toX, p.toY, d.aoeRadius)
                    && threatens(ship, d.damage, d.penetration, p.toX, p.toY)) return true;
        }
        for (WarshipShell s : shells) {
            if (s.intercepted || !ship.faction.hostileTo(s.faction)) continue;
            PendingDetonation d = s.landing();
            if (d == null) continue;
            if (nearHull(ship, s.toX, s.toY, s.blastCells())
                    && threatens(ship, d.damage, d.penetration, s.fromX, s.fromY, s.toX, s.toY)) return true;
        }
        for (WarheadStrike s : strikes) {
            if (s.intercepted || !ship.faction.hostileTo(s.faction)) continue;
            PendingDetonation d = s.detonation();
            if (nearHull(ship, s.aimX, s.aimY, d.aoeRadius)
                    && threatens(ship, d.damage, d.penetration, s.aimX, s.aimY)) return true;
        }
        return false;
    }

    private static boolean nearHull(Warship ship, float px, float py, float blast) {
        return ship.distanceTo(px, py) <= blast + WarshipShield.THREAT_MARGIN_CELLS;
    }

    /** Advances every round in flight; one that arrives lands as a detonation and, if heavy, a heavy blast. */
    private void landShells(float dt, BattleSimulation sim) {
        for (int i = shells.size() - 1; i >= 0; i--) {
            WarshipShell shell = shells.get(i);
            if (shell.intercepted) {
                if (shell.hullTarget() != null) shipFire.noteShotDown();
                shells.remove(i);
                continue;
            }
            boolean sweeps = inFlightCollision && sweeps(shell);
            float sx0 = sweeps ? shell.path().x(Math.min(shell.elapsed(), shell.flightSeconds)) : 0f;
            float sy0 = sweeps ? shell.path().y(Math.min(shell.elapsed(), shell.flightSeconds)) : 0f;
            boolean spent = shell.advance(dt);
            if (sweeps) {
                // Between hulls a round stops at the first hull on its way, friend or foe, not only where it lands.
                float t1 = Math.min(shell.elapsed(), shell.flightSeconds);
                WarshipRoundSweep.Contact c = WarshipRoundSweep.first(ships, shell.shooter, sx0, sy0,
                        shell.path().x(t1), shell.path().y(t1));
                if (c != null) {
                    shells.remove(i);
                    landInFlight(sim, shell, c, sx0, sy0);
                    continue;
                }
            }
            // A standoff missile's beam is loose the tick its painting ends, and nothing can stop it after that.
            PendingDetonation beam = shell.takeBeamRelease();
            if (beam != null) {
                sim.detonateNow(beam);
                if (!beam.wasAbsorbed() && cueAllowed(shell.weapon, IMPACT_CUE_GAP_SECONDS)) {
                    cue(WarshipSoundCue.Kind.IMPACT, impactSound(shell.weapon), shell.toX, shell.toY);
                }
                if (!beam.wasAbsorbed() && beam.damage >= HEAVY_IMPACT_DAMAGE) {
                    sim.spawnHeavyImpact(shell.toX, shell.toY, shell.blastCells(), shell.weapon.explosionColor());
                }
            }
            if (!spent) continue;
            shells.remove(i);
            if (shell.carrier()) {
                release(shell);
                continue;
            }
            PendingDetonation landing = shell.landing();
            if (landing == null) continue;
            if (shell.hullOnly()) {
                boolean struck = strikeHulls(landing, shell.fromX, shell.fromY, hullDamageType(shell.weapon), shell.weapon,
                        sweeps ? shell.shooter : null);
                if (struck && (sweeps || shell.hullTarget() != null)) {
                    shipFire.noteStruck(shell.faction, shell.hullTarget(), struck(), false);
                }
                if (shell.hullTarget() != null) {
                    shipFire.noteLanding(struck);
                    if (shell.weapon.missile()) shipFire.noteMissileLanding(struck);
                }
                if (!struck && shell.straysOnMiss()) {
                    // It passed the hull it was fired at: it carries on and comes down on the ground.
                    shells.add(shell.carryOn());
                    continue;
                }
                if (struck) {
                    // The blast is where the round met the hull (or its shield), not the middle it was aimed at.
                    float bx = strikeX(shell.toX);
                    float by = strikeY(shell.toY);
                    if (cueAllowed(shell.weapon, IMPACT_CUE_GAP_SECONDS)) {
                        cue(WarshipSoundCue.Kind.IMPACT, impactSound(shell.weapon), bx, by);
                    }
                    // A shield's hit is its own flare (WarshipHitFx); the fireball is for a round that met metal.
                    if (landing.damage >= HEAVY_IMPACT_DAMAGE && !strikeShieldOnly) {
                        sim.spawnHeavyImpact(bx, by, shell.blastCells(), shell.weapon.explosionColor());
                    }
                    continue;
                }
            } else if (shell.stray()) {
                // A round that missed the hulls it was fired at: what it does to the ground goes in the duel ledger.
                Map<Long, Float> before = groundNear(sim, landing.endpointX, landing.endpointY,
                        landing.aoeRadius + GROUND_TALLY_MARGIN_CELLS);
                sim.detonateNow(landing);
                tallyGround(sim, before, WarshipDuelLedger.Cause.STRAY_ROUND);
            } else {
                sim.detonateNow(landing);
            }
            if (!landing.wasAbsorbed() && cueAllowed(shell.weapon, IMPACT_CUE_GAP_SECONDS)) {
                cue(WarshipSoundCue.Kind.IMPACT, impactSound(shell.weapon), shell.toX, shell.toY);
            }
            if (!landing.wasAbsorbed() && landing.damage >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(shell.toX, shell.toY, shell.blastCells(), shell.weapon.explosionColor());
            }
        }
    }

    /**
     * Whether a body in flight meets hulls on its way: a ship's round or missile at a hull, and a round carrying on
     * past the hull it missed. A multi-warhead carrier flies to its split and its warheads meet hulls instead
     * ({@link #landStrikes}); a standoff missile's beam and every round at the ground or at aircraft are not met.
     */
    private static boolean sweeps(WarshipShell shell) {
        return shell.shooter != null && !shell.carrier() && !shell.hasBeam()
                && (shell.hullTarget() != null || shell.stray());
    }

    /** A round that met a hull on its way lands on it there, with its impact cue and, if heavy, a heavy blast. */
    private void landInFlight(BattleSimulation sim, WarshipShell shell, WarshipRoundSweep.Contact c, float x0, float y0) {
        float[] from = behind(c, x0, y0, shell.fromX, shell.fromY);
        strikeContact(c, detonation(shell.faction, c.x(), c.y(), shell.damage(), shell.penetration(),
                shell.damageRadius()), from[0], from[1], hullDamageType(shell.weapon), shell.weapon);
        shipFire.noteStruck(shell.faction, shell.hullTarget(), struck(), true);
        if (shell.hullTarget() != null) {
            shipFire.noteLanding(true);
            if (shell.weapon.missile()) shipFire.noteMissileLanding(true);
        }
        hullImpact(sim, shell.weapon, shell.damage(), shell.blastCells(), c.x(), c.y());
    }

    /** The impact cue and, for a heavy round that met metal, the heavy blast, where the last strike met the hull. */
    private void hullImpact(BattleSimulation sim, VanillaWeapon weapon, float damage, float visualCells,
                            float fallbackX, float fallbackY) {
        float bx = strikeX(fallbackX);
        float by = strikeY(fallbackY);
        if (cueAllowed(weapon, IMPACT_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.IMPACT, impactSound(weapon), bx, by);
        if (damage >= HEAVY_IMPACT_DAMAGE && !strikeShieldOnly) {
            sim.spawnHeavyImpact(bx, by, visualCells, weapon.explosionColor());
        }
    }

    /**
     * A point behind a contact along the round's travel this tick, the side it came from: a few cells back from where
     * it met the hull, or {@code (fallbackX, fallbackY)} (its muzzle or split) when it moved too little to say.
     */
    static float[] behind(WarshipRoundSweep.Contact c, float x0, float y0, float fallbackX, float fallbackY) {
        float dx = c.x() - x0;
        float dy = c.y() - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            dx = c.x() - fallbackX;
            dy = c.y() - fallbackY;
            len = (float) Math.hypot(dx, dy);
            if (len < 1e-3f) return new float[]{fallbackX, fallbackY};
        }
        float back = Math.max(len, BEHIND_CELLS);
        return new float[]{c.x() - dx / len * back, c.y() - dy / len * back};
    }

    /** How far behind a contact the side a round came from is read, at least, in cells. */
    private static final float BEHIND_CELLS = 4f;

    /** The hulls the last strike reached, for the tally of who took rounds between hulls. */
    List<Warship> struck() {
        return struck;
    }

    /**
     * A carrier has reached its split point: a flash and a split sound, once, and its warheads
     * leave it, each already committed to its own landing and now a body of its own.
     */
    private void release(WarshipShell shell) {
        float rx = shell.path().landX();
        float ry = shell.path().landY();
        MissileFlightSpec flight = shell.weapon.flight();
        MissileFlightSpec.Mirv mirv = flight == null ? null : flight.mirv();
        splits.add(new SplitEvent(shell.weapon, rx, ry, shell.splitScale(), mirv == null ? null : mirv.smokeColor()));
        cue(WarshipSoundCue.Kind.SPLIT, shell.splitSoundId(), rx, ry);
        if (shell.hullTarget() != null && mirv != null) {
            // A carrier fired at a hull: its warheads are aimed at the hull now, not where it was at launch.
            strikes.addAll(shipFire.release(shell, rx, ry));
            return;
        }
        for (WarshipShell.Warhead warhead : shell.warheads()) {
            strikes.add(new WarheadStrike(shell.weapon, shell.faction, shell.shooter, rx, ry, warhead,
                    mirv == null ? null : mirv.sprite()));
        }
    }

    /** Counts released warheads down; one that arrives detonates as an ordinary detonation and, if heavy, a heavy blast. */
    private void landStrikes(float dt, BattleSimulation sim) {
        for (int i = strikes.size() - 1; i >= 0; i--) {
            WarheadStrike strike = strikes.get(i);
            if (strike.intercepted) {
                if (strike.hullTarget() != null) shipFire.noteWarheadShotDown();
                strikes.remove(i);
                continue;
            }
            boolean sweeps = inFlightCollision && strike.hullTarget() != null;
            float sx0 = sweeps ? strike.x() : 0f;
            float sy0 = sweeps ? strike.y() : 0f;
            boolean spent = strike.advance(dt);
            if (sweeps) {
                float t1 = Math.min(strike.age(), strike.delaySeconds);
                WarshipRoundSweep.Contact c = WarshipRoundSweep.first(ships, strike.shooter, sx0, sy0,
                        strike.path().x(t1), strike.path().y(t1));
                if (c != null) {
                    strikes.remove(i);
                    PendingDetonation d = strike.detonation();
                    float[] from = behind(c, sx0, sy0, strike.releaseX, strike.releaseY);
                    strikeContact(c, detonation(strike.faction, c.x(), c.y(), d.damage, d.penetration, d.aoeRadius),
                            from[0], from[1], hullDamageType(strike.weapon), strike.weapon);
                    shipFire.noteStruck(strike.faction, strike.hullTarget(), struck(), true);
                    shipFire.noteWarhead(true);
                    hullImpact(sim, strike.weapon, d.damage, strike.visualCells, c.x(), c.y());
                    continue;
                }
            }
            if (!spent) continue;
            strikes.remove(i);
            PendingDetonation det = strike.detonation();
            if (strike.hullTarget() != null) {
                // A warhead aimed at a hull lands on hulls alone; one that reached none carries on to the ground.
                boolean struck = strikeHulls(det, strike.releaseX, strike.releaseY, hullDamageType(strike.weapon), strike.weapon,
                        sweeps ? strike.shooter : null);
                if (struck) shipFire.noteStruck(strike.faction, strike.hullTarget(), struck(), false);
                shipFire.noteWarhead(struck);
                if (!struck) {
                    shells.add(WarshipShipFire.carryOn(strike));
                    continue;
                }
                float bx = strikeX(strike.aimX);
                float by = strikeY(strike.aimY);
                if (cueAllowed(strike.weapon, IMPACT_CUE_GAP_SECONDS)) {
                    cue(WarshipSoundCue.Kind.IMPACT, impactSound(strike.weapon), bx, by);
                }
                if (det.damage >= HEAVY_IMPACT_DAMAGE && !strikeShieldOnly) {
                    sim.spawnHeavyImpact(bx, by, strike.visualCells, strike.weapon.explosionColor());
                }
                continue;
            } else {
                sim.detonateNow(det);
            }
            if (!det.wasAbsorbed() && cueAllowed(strike.weapon, IMPACT_CUE_GAP_SECONDS)) {
                cue(WarshipSoundCue.Kind.IMPACT, impactSound(strike.weapon), strike.aimX, strike.aimY);
            }
            if (!det.wasAbsorbed() && det.damage >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(strike.aimX, strike.aimY, strike.visualCells, strike.weapon.explosionColor());
            }
        }
    }

    /** The hull share left to the hull {@code ship} is fighting, or {@link PostureInputs#NO_FOE}. */
    static float foeHull(Warship ship, List<Warship> ships) {
        Warship foe = ship.hullTarget();
        boolean fighting = foe != null || ship.manoeuvre().target() != null;
        // An overloaded ship holds no fire target, but it is still in the fight its manoeuvre is about.
        if (foe == null || foe.destroyed()) foe = ship.manoeuvre().target();
        // The hull it was fighting has just gone down, before it has chosen the next one; or its anti-ship guns are all
        // silenced under repair, so it holds no target (a silenced mount is not a lost one); or it is overloaded, which
        // drops its target and its manoeuvre with it. Either way it is still in the fight while a hostile hull lives,
        // and the nearest is its foe.
        if ((foe == null || foe.destroyed() || foe.departed())
                && (fighting || ship.awaitingAntiShipRepair() || ship.overloaded())) {
            foe = nearestHostileHull(ship, ships);
        }
        return foe == null || foe.destroyed() || foe.departed() ? PostureInputs.NO_FOE : Math.max(0f, foe.hullFraction());
    }

    private static Warship nearestHostileHull(Warship ship, List<Warship> ships) {
        Warship best = null;
        float bestD = Float.MAX_VALUE;
        for (Warship o : ships) {
            if (!WarshipShipFire.hostileHull(ship, o)) continue;
            float d = (float) Math.hypot(o.centerX() - ship.centerX(), o.centerY() - ship.centerY());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    private void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        VanillaWeapon w = m.weapon;
        if (w.beam()) {
            tickBeam(dt, sim, ship, m);
            return;
        }
        if (ship.overloaded()) {
            // An overloaded ship is silent: the burst under way is abandoned where it stands.
            m.burstRemaining = 0;
            m.aimed = false;
            traverse(dt, ship, m, ship.facingDegrees + m.slot.angleDegrees());
            return;
        }
        if (m.burstRemaining > 0 && beyondOpportunity(ship, m)) {
            // The hull has drawn away from what this burst was aimed at: it falls silent rather than fire past the posture's range.
            m.burstRemaining = 0;
            m.aimed = false;
        }
        if (m.burstRemaining > 0) {
            traverse(dt, ship, m, AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m)));
            m.burstTimer -= dt;
            if (m.burstTimer <= 0f) {
                if (!ship.hasOrdnance(m)) {
                    // Out of rounds mid-burst: the mount falls silent where it stands.
                    m.burstRemaining = 0;
                    m.aimed = false;
                    return;
                }
                fire(sim, ship, m);
                m.burstRemaining--;
                m.burstTimer = strafeRocket(ship, m) ? STRAFE_ROCKET_INTERVAL_SECONDS : burstDelay(ship, w);
                if (m.burstRemaining == 0) {
                    m.cooldown = Math.max(w.cooldownSeconds(), 0.05f);
                    m.aimed = false;
                }
            }
            return;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        // A choice is kept until the mount has fired on it, so a turret can finish
        // swinging; it is dropped if its area was dropped or the hull has turned it out of arc.
        if (m.aimed && (!ship.holdsGround(m, m.aimX, m.aimY) || !Warship.bears(
                m, ship.facingDegrees, ship.mountX(m), ship.mountY(m), m.aimX, m.aimY,
                ship.groundRangeMultiplier(m.aimX, m.aimY)) || beyondOpportunity(ship, m))) {
            m.aimed = false;
        }
        if (!m.aimed && !ship.areasFor(m).isEmpty()) m.aimed = pickTarget(sim, ship, m) != null;
        if (!m.aimed) {
            traverse(dt, ship, m, idleBearing(m, rest));
            return;
        }
        float bearing = AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m));
        traverse(dt, ship, m, bearing);
        if (!ship.groundFireAllowed(m)) return;
        if (m.cooldown > 0f) return;
        if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) > ALIGNED_DEGREES) return;
        if (denialNotYet(ship, m)) return;
        if (!ship.hasOrdnance(m)) {
            m.aimed = false;
            return;
        }
        // Venting: the barrel stays on its choice and waits for the pool to drain.
        float cost = w.fluxPerShot() * w.burstSize();
        if (!ship.fluxPool().canStart(cost)) return;
        ship.fluxPool().spend(cost);
        m.burstRemaining = w.burstSize();
        // A strafe's second rocket pod fires on the off-beat, so the pair alternate down the line.
        m.burstTimer = strafeRocket(ship, m) && strafePodIndex(ship, m) % 2 == 1 ? STRAFE_ROCKET_INTERVAL_SECONDS * 0.5f : 0f;
    }

    /**
     * Whether the mount is aimed at a denied landing zone whose boat is still too
     * far from touchdown for a round fired now to land with it: the barrel stays
     * on the zone and waits, so the first round falls as the boat settles.
     */
    private static boolean denialNotYet(Warship ship, Warship.Mount m) {
        Warship.Held zone = ship.denialHeldAt(m.aimX, m.aimY);
        if (zone == null) return false;
        float fall = (float) Math.hypot(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m))
                / Math.max(1f, WarshipScale.cells(m.weapon.projectileSpeedPx()));
        return zone.denialEta > fall + DENIAL_LEAD_SECONDS;
    }

    /**
     * Whether the mount's aim has drifted past the posture's opportunity range
     * (the hull moved, or the posture changed). Never true for a manual order
     * or when the posture sets no range.
     */
    private static boolean beyondOpportunity(Warship ship, Warship.Mount m) {
        float range = ship.opportunityRange();
        if (range == Float.POSITIVE_INFINITY || ship.exempt(m, m.aimX, m.aimY)) return false;
        return Math.hypot(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m)) > range;
    }

    /**
     * A beam mount with an area to cut lays one sweep across the area's width as
     * seen from the mount, walked at the weapon's own traverse rate and kept inside
     * the slot's arc. Once laid the sweep is committed, like any lance: the
     * barrel follows it but does not re-aim it.
     */
    private void tickBeam(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        if (m.lance != null) {
            if (sim.lanceSweeps().active().contains(m.lance)) {
                m.headingDegrees = (float) Math.toDegrees(m.lance.angle()) - 90f;
                if (m.lance.isBurning()) {
                    m.secondsSinceFired = 0f;
                    if (litBeams.add(m)) {
                        cue(WarshipSoundCue.Kind.BEAM_LIT, w.fireSoundId(), ship.mountX(m), ship.mountY(m));
                    }
                    LanceSweep s = m.lance.sweep;
                    float angle = m.lance.angle();
                    cue(WarshipSoundCue.Kind.BEAM_BURNING, w.loopSoundId(),
                            s.originX() + (float) Math.cos(angle) * s.reach(),
                            s.originY() + (float) Math.sin(angle) * s.reach());
                }
                return;
            }
            litBeams.remove(m);
            m.lance = null;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        WarshipOrder.FireOnArea area = null;
        if (!ship.groundFireAllowed(m) || ship.areasFor(m).isEmpty() || m.cooldown > 0f || w.damagePerSecond() < LANCE_MIN_DPS
                || !ship.hasOrdnance(m) || !ship.fluxPool().canStartFire() || (area = pickTarget(sim, ship, m)) == null) {
            traverse(dt, ship, m, idleBearing(m, rest));
            return;
        }
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float dx = area.x() - mx;
        float dy = area.y() - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float centre = (float) Math.atan2(dy, dx);
        float half = dist > area.radius()
                ? Math.min(LANCE_MAX_HALF_ANGLE, (float) Math.asin(area.radius() / dist)) : LANCE_MAX_HALF_ANGLE;
        // Kept inside the slot's own arc, measured about the slot's direction.
        float slotWorld = (float) Math.toRadians(rest + 90f);
        float halfArc = (float) Math.toRadians(Math.max(m.slot.arcDegrees(), 1f) * 0.5f);
        float start = Math.max(-halfArc, wrapRadians(centre - half - slotWorld));
        float end = Math.min(halfArc, wrapRadians(centre + half - slotWorld));
        if (end - start < 0.05f) {
            traverse(dt, ship, m, idleBearing(m, rest));
            return;
        }
        float rate = Math.max(LANCE_MIN_SWEEP_RATE, w.turnRateDegPerSec());
        float burn = Math.max(1f, (float) Math.toDegrees(end - start) / rate);
        if (!ship.fluxPool().canStart(w.fluxPerSecond() * burn)) {
            traverse(dt, ship, m, idleBearing(m, rest));
            return;
        }
        LanceSweep sweep = new LanceSweep(0L, ship.faction, mx, my,
                slotWorld + start, slotWorld + end,
                WarshipScale.cells(w.rangePx()),
                Math.max(LANCE_MIN_HALF_WIDTH, WarshipScale.cells(w.beamWidthPx())),
                LANCE_TELEGRAPH_SECONDS, burn, w.damagePerSecond(),
                LANCE_PENETRATION, LANCE_WALL_DAMAGE, w.id());
        m.lance = sim.startLanceSweep(sweep);
        ship.fluxPool().spend(w.fluxPerSecond() * burn);
        m.shotsFired++;
        ship.noteGroundShot();
        ship.spendOrdnance(m);
        m.cooldown = sweep.totalSeconds() + Math.max(w.cooldownSeconds(), LANCE_REST_SECONDS);
    }

    private static float wrapRadians(float r) {
        float d = (float) (r % (2 * Math.PI));
        if (d > Math.PI) d -= (float) (2 * Math.PI);
        if (d < -Math.PI) d += (float) (2 * Math.PI);
        return d;
    }

    /**
     * Where a mount with nothing of its own to swing to points: on the hull it is holding its next round for, when the
     * hull fire laid it ({@link WarshipShipFire}), else at rest along its slot.
     */
    private static float idleBearing(Warship.Mount m, float rest) {
        return m.hullLaid ? m.hullLayBearing : rest;
    }

    /**
     * Swings the barrel toward {@code bearing} at the weapon's own traverse rate,
     * inside its slot's arc. A fixed mount, a hardpoint or a weapon with no
     * traverse, points where the hull points it. A bearing outside the arc is
     * met at the arc's nearer stop, and the barrel swings the way round that
     * stays inside the arc rather than through the hull; a barrel the hull's own
     * turn has carried past a stop is held at it.
     */
    static void traverse(float dt, Warship ship, Warship.Mount m, float bearing) {
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        float rate = m.weapon.turnRateDegPerSec();
        if (m.slot.hardpoint() || rate <= 0f) {
            m.headingDegrees = rest;
            return;
        }
        float step = rate * dt;
        float half = Math.max(m.slot.arcDegrees(), 1f) * 0.5f;
        if (half >= 180f) {
            float error = WarshipFacing.wrap(bearing - m.headingDegrees);
            m.headingDegrees = Math.abs(error) <= step
                    ? bearing : WarshipFacing.wrap(m.headingDegrees + Math.signum(error) * step);
            return;
        }
        // Measured about the slot, so the swing never takes the short way round through the dead arc.
        float now = clamp(WarshipFacing.wrap(m.headingDegrees - rest), -half, half);
        float want = clamp(WarshipFacing.wrap(bearing - rest), -half, half);
        float error = want - now;
        float offset = Math.abs(error) <= step ? want : now + Math.signum(error) * step;
        m.headingDegrees = WarshipFacing.wrap(rest + offset);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Deals the mount one of the ship's areas and a target in it. Areas are dealt
     * in rotation across every mount, the densest twice per round, so a battery
     * spreads over all the ground it holds rather than all hammering one cluster;
     * an area the mount cannot reach is skipped for the next. Returns the area
     * chosen, with the mount's aim set, or {@code null} when none holds a target it
     * can bear on.
     */
    private WarshipOrder.FireOnArea pickTarget(BattleSimulation sim, Warship ship, Warship.Mount m) {
        List<WarshipOrder.FireOnArea> areas = ship.areasFor(m);
        int k = areas.size();
        int cycle = k + 1;
        int start = ship.dealCounter % cycle;
        boolean[] tried = new boolean[k];
        for (int step = 0; step < cycle; step++) {
            int position = (start + step) % cycle;
            int index = position < k ? position : 0;
            if (tried[index]) continue;
            tried[index] = true;
            if (acquire(sim, ship, m, areas.get(index))) {
                ship.dealCounter = start + step + 1;
                return areas.get(index);
            }
        }
        return null;
    }

    /**
     * Chooses, at random among the hostile units inside the ordered area that this
     * mount can reach, the one to fire on. Random rather than nearest so a
     * battery spreads over the ground it was given instead of piling every round
     * on the nearest man.
     */
    private boolean acquire(BattleSimulation sim, Warship ship, Warship.Mount m, WarshipOrder.FireOnArea area) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        int seen = 0;
        Warship.Held denial = ship.denialHeld(area);
        if (denial != null) {
            // A landing zone nobody stands on yet: the mount aims at open ground inside the zone; tickMount
            // holds the trigger until a round fired now would land as the boat touches down.
            if (m.weapon.beam()) return false;
            for (int attempt = 0; attempt < DENIAL_AIM_ATTEMPTS; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                float r = area.radius() * (float) Math.sqrt(random.nextDouble());
                float px = area.x() + (float) Math.cos(angle) * r;
                float py = area.y() + (float) Math.sin(angle) * r;
                if (Warship.bears(m, ship.facingDegrees, mx, my, px, py)) {
                    m.aimX = px;
                    m.aimY = py;
                    m.aimVx = 0f;
                    m.aimVy = 0f;
                    return true;
                }
            }
            return false;
        }
        // A posture's opportunity range and battery limit directed fire; a manual order is never limited.
        boolean byHand = ship.exempt(m, area.x(), area.y()) || ship.isLaid(area);
        if (!byHand && !ship.mayFireAtGround(m)) return false;
        float opportunity = byHand ? Float.POSITIVE_INFINITY : ship.opportunityRange();
        if (area.bombardStructures()) {
            int left = Math.max(0, (int) Math.floor(area.x() - area.radius()));
            int top = Math.max(0, (int) Math.floor(area.y() - area.radius()));
            int right = Math.min(sim.getGrid().getWidth() - 1, (int) Math.ceil(area.x() + area.radius()));
            int bottom = Math.min(sim.getGrid().getHeight() - 1, (int) Math.ceil(area.y() + area.radius()));
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                if (!sim.getTopology().isWall(x, y) || !area.contains(x + .5f, y + .5f)
                        || !Warship.bears(m, ship.facingDegrees, mx, my, x + .5f, y + .5f, Warship.STRUCTURAL_RANGE_MULTIPLIER)
                        || Math.hypot(x + .5f - mx, y + .5f - my) > opportunity) continue;
                if (random.nextInt(++seen) == 0) { m.aimX = x + .5f; m.aimY = y + .5f; m.aimVx = 0f; m.aimVy = 0f; }
            }
            return seen > 0;
        }
        if (rocketsTheLead(sim, ship, m, area)) {
            // A strafing run's rocket pod fires straight off its own nose, as far ahead as the lead point.
            float[] at = noseLineAt(ship, m, mx, my, area.x(), area.y());
            if (at == null || !Warship.bears(m, ship.facingDegrees, mx, my, at[0], at[1])) return false;
            m.aimX = at[0];
            m.aimY = at[1];
            m.aimVx = 0f;
            m.aimVy = 0f;
            return true;
        }
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (roster.isRiding(u)) continue;
            Faction f = roster.identity().faction(u);
            if (!ship.faction.hostileTo(f)) continue;
            float ux = world.x(u);
            float uy = world.y(u);
            if (!area.contains(ux, uy) || !Warship.bears(m, ship.facingDegrees, mx, my, ux, uy)
                    || Math.hypot(ux - mx, uy - my) > opportunity) continue;
            seen++;
            if (random.nextInt(seen) == 0) {
                m.aimX = ux;
                m.aimY = uy;
                // A turret or emplacement has no movement to read.
                boolean mover = sim.movement().has(u);
                m.aimVx = mover ? sim.movement().velX(u) : 0f;
                m.aimVy = mover ? sim.movement().velY(u) : 0f;
            }
        }
        // Ground vehicles are hulls of their own, outside the unit roster; a gun works them as it works a man.
        ConvoyService convoy = sim.convoy();
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long v = convoy.vehicleAt(i);
            if (!world.isAlive(v) || convoy.isAirborne(v) || !ship.faction.hostileTo(convoy.faction(v))) continue;
            GroundBody body = convoy.body(v);
            if (body == null || !area.contains(body.x, body.y) || !Warship.bears(m, ship.facingDegrees, mx, my, body.x, body.y)
                    || Math.hypot(body.x - mx, body.y - my) > opportunity) continue;
            seen++;
            if (random.nextInt(seen) == 0) {
                m.aimX = body.x;
                m.aimY = body.y;
                m.aimVx = convoy.velocityX(v);
                m.aimVy = convoy.velocityY(v);
            }
        }
        if (seen == 0 && area.saturate() && !m.weapon.beam()) return aimAtOpenGround(m, ship, area, mx, my, opportunity);
        return seen > 0;
    }

    /** Seconds between two rockets of one pod on a strafe: the burst spread down the run rather than loosed at once. */
    static final float STRAFE_ROCKET_INTERVAL_SECONDS = 0.5f;
    /** How far a strafe's rocket lands from its pod's aim point at most, in cells: a tight cluster. */
    static final float STRAFE_ROCKET_SCATTER_CELLS = 1.5f;
    /** How many times harder a strafe's rocket motor pushes than its spec's, for the snap of a gun-run salvo. */
    static final float STRAFE_ROCKET_ACCEL_SCALE = 2f;

    /** Whether {@code m} is a fixed missile hardpoint on a hull flying the line of a strafe ({@link LowPass#strafe}). */
    static boolean strafeRocket(Warship ship, Warship.Mount m) {
        LowPass pass = ship.lowPass();
        return pass != null && pass.rocketRun() && pass.onLine() && m.weapon.missile() && m.slot.hardpoint();
    }

    /** Which of the hull's strafe rocket pods {@code m} is, counted in mount order, for the off-beat. */
    private static int strafePodIndex(Warship ship, Warship.Mount m) {
        int index = 0;
        for (Warship.Mount other : ship.mounts()) {
            if (other == m) return index;
            if (other.weapon.missile() && other.slot.hardpoint()) index++;
        }
        return index;
    }

    /**
     * The point straight off the mount's nose as far ahead as {@code (tx, ty)}: where a fixed pod's rocket goes when
     * it is fired at that ground. Null when the ground is not ahead of it.
     */
    static float[] noseLineAt(Warship ship, Warship.Mount m, float mx, float my, float tx, float ty) {
        double nose = Math.toRadians(ship.facingDegrees + m.slot.angleDegrees() + 90f);
        float fx = (float) Math.cos(nose);
        float fy = (float) Math.sin(nose);
        float ahead = (tx - mx) * fx + (ty - my) * fy;
        return ahead > 0f ? new float[]{mx + fx * ahead, my + fy * ahead} : null;
    }

    /**
     * One rocket of a strafe: aimed afresh off the pod's nose at the run's lead point as it is now, so the stream walks
     * down the line with the hull; landing within {@link #STRAFE_ROCKET_SCATTER_CELLS} of it; and loosed with the
     * hull's way on, so it runs out ahead rather than being overflown ({@link MissilePath#carried}).
     */
    private void fireStrafeRocket(BattleSimulation sim, Warship ship, Warship.Mount m, VanillaWeapon w,
                                  MissileFlightSpec flight, float mx, float my, float visual, float blast) {
        float[] lead = ship.lowPass().fireCentre(ship.x, ship.y);
        float[] at = noseLineAt(ship, m, mx, my, lead[0], lead[1]);
        float ax = at != null ? at[0] : m.aimX;
        float ay = at != null ? at[1] : m.aimY;
        long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, ax, ay);
        SplittableRandom scatter = new SplittableRandom(seed);
        double angle = scatter.nextDouble() * Math.PI * 2.0;
        float r = STRAFE_ROCKET_SCATTER_CELLS * (float) Math.sqrt(scatter.nextDouble());
        float lx = clampX(sim, ax + (float) Math.cos(angle) * r);
        float ly = clampY(sim, ay + (float) Math.sin(angle) * r);
        float dx = lx - mx;
        float dy = ly - my;
        float dist = (float) Math.hypot(dx, dy);
        float carry = dist > 0f ? (ship.velocityX * dx + ship.velocityY * dy) / dist : 0f;
        MissilePath path = MissilePath.carried(flight, mx, my, lx, ly, seed, carry, STRAFE_ROCKET_ACCEL_SCALE);
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, path.landX(), path.landY(), path, visual,
                w.damagePerShot(), penetration(w.damageType()), blast, null, null, flight.hitpoints(), null, 0f));
    }

    /**
     * Whether {@code m} is a fixed missile hardpoint on a strafing run ({@link LowPass#strafe}) whose circle holds
     * something hostile, a man or a ground vehicle: it then rockets the lead point instead of picking a target.
     */
    private static boolean rocketsTheLead(BattleSimulation sim, Warship ship, Warship.Mount m, WarshipOrder.FireOnArea area) {
        LowPass pass = ship.lowPass();
        if (pass == null || !pass.rocketRun() || !pass.onLine() || !m.weapon.missile() || !m.slot.hardpoint()
                || !ship.isLaid(area)) {
            return false;
        }
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (!roster.isRiding(u) && ship.faction.hostileTo(roster.identity().faction(u))
                    && area.contains(world.x(u), world.y(u))) {
                return true;
            }
        }
        ConvoyService convoy = sim.convoy();
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long v = convoy.vehicleAt(i);
            GroundBody body = convoy.body(v);
            if (body != null && world.isAlive(v) && !convoy.isAirborne(v) && ship.faction.hostileTo(convoy.faction(v))
                    && area.contains(body.x, body.y)) {
                return true;
            }
        }
        return false;
    }

    /** With nobody in a saturating area, the mount works over a random bearable point of the ground itself. */
    private boolean aimAtOpenGround(Warship.Mount m, Warship ship, WarshipOrder.FireOnArea area,
                                    float mx, float my, float opportunity) {
        for (int attempt = 0; attempt < DENIAL_AIM_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            float r = area.radius() * (float) Math.sqrt(random.nextDouble());
            float px = area.x() + (float) Math.cos(angle) * r;
            float py = area.y() + (float) Math.sin(angle) * r;
            if (!area.contains(px, py) || Math.hypot(px - mx, py - my) > opportunity
                    || !Warship.bears(m, ship.facingDegrees, mx, my, px, py)) continue;
            m.aimX = px;
            m.aimY = py;
            m.aimVx = 0f;
            m.aimVy = 0f;
            return true;
        }
        return false;
    }

    /** The seconds between two rounds of a burst: the weapon's own, held to the preset's rolling-barrage window for a rack. */
    static float burstDelay(Warship ship, VanillaWeapon w) {
        float spec = w.burstDelaySeconds();
        if (!w.missile() || w.flight() == null || w.flight().mirv() != null || w.burstSize() < 3) return spec;
        float window = ship.missilePreset().rackVolleySeconds();
        if (Float.isInfinite(window)) return spec;
        return Math.min(spec, window / w.burstSize());
    }

    /**
     * One pull of a rack's trigger: the seed that scatters its bodies, the points they are
     * aimed at (in arrival order), the ground they cover for the telegraph, and the bodies
     * it has in the air so a body over the volley cap can be folded into one of them.
     */
    private static final class Volley {
        final float[] aimX;
        final float[] aimY;
        final WarshipShell.Footprint footprint;
        final List<WarshipShell> bodies = new ArrayList<>();

        Volley(float[] aimX, float[] aimY, WarshipShell.Footprint footprint) {
            this.aimX = aimX;
            this.aimY = aimY;
            this.footprint = footprint;
        }
    }

    private void fire(BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        ship.noteGroundShot();
        ship.spendOrdnance(m);
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        if (cueAllowed(m, FIRE_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.FIRE, w.fireSoundId(), mx, my);
        float dx = m.aimX - mx;
        float dy = m.aimY - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // The weapon's own cone, as a lateral miss at the target's range.
        float miss = dist * (float) Math.tan(Math.toRadians(w.spreadDegrees() * 0.5f))
                * (random.nextFloat() - 0.5f) * 2f;
        float nx = dist > 0f ? -dy / dist : 0f;
        float ny = dist > 0f ? dx / dist : 0f;
        float lx = m.aimX + nx * miss;
        float ly = m.aimY + ny * miss;
        // The fireball drawn is the spec's visual figure; what the blast hurts is its damage footprint.
        float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        float blast = Math.max(1f, WarshipScale.cells(w.damageRadiusPx()));
        MissileFlightSpec flight = w.missile() ? w.flight() : null;
        if (flight == null) {
            float speed = Math.max(1f, WarshipScale.cells(w.projectileSpeedPx()));
            MissilePath path = MissilePath.straight(mx, my, lx, ly, speed);
            shells.add(new WarshipShell(w, ship.faction, ship, mx, my, lx, ly, path, visual,
                    w.damagePerShot(), penetration(w.damageType()), blast, null, null, 0f, null, 0f));
            return;
        }
        MissilePreset preset = ship.missilePreset();
        MissileArchetype archetype = MissileArchetype.of(w);
        MissileFlightSpec.Mirv mirv = flight.mirv();
        boolean synthetic = false;
        if (mirv == null && preset.syntheticSplit() && MissileArchetype.splittable(w)) {
            mirv = syntheticMirv(w, flight);
            synthetic = true;
        }
        if (mirv != null) {
            // The round's flight is a pure function of this seed, taken from who fired what, where and
            // when rather than from the shared stream, so a missile never shifts another gun's draws.
            long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, m.aimX, m.aimY);
            fireBloom(sim, ship, w, flight, mirv, synthetic, mx, my, m.aimX, m.aimY, seed, preset, archetype);
            return;
        }
        if (flight.standoff() != null) {
            fireStandoff(ship, m, w, flight, flight.standoff(), mx, my, lx, ly, visual, blast);
            return;
        }
        if (strafeRocket(ship, m)) {
            fireStrafeRocket(sim, ship, m, w, flight, mx, my, visual, blast);
            return;
        }
        int burst = Math.max(1, w.burstSize());
        int index = Math.max(0, burst - m.burstRemaining);
        WarshipShell.Footprint footprint = null;
        Volley volley = null;
        if (preset.scatterRacks() && burst >= 3 && archetype != MissileArchetype.HEAVY) {
            if (index == 0 || !volleys.containsKey(m)) {
                volley = openVolley(m, ship, w, archetype, preset, mx, my, blast, burst);
                volleys.put(m, volley);
            } else {
                volley = volleys.get(m);
            }
            int at = index % volley.aimX.length;
            lx = clampX(sim, volley.aimX[at]);
            ly = clampY(sim, volley.aimY[at]);
            footprint = volley.footprint;
        }
        float damage = w.damagePerShot();
        // Over a cap, the round is folded into a body already in the air: the damage and the area are kept.
        WarshipShell host = null;
        if (volley != null && index >= SwarmBudget.PER_VOLLEY) {
            host = liveHost(volley.bodies, index);
        } else if (liveBodies(ship) >= SwarmBudget.PER_SHIP || liveBodies(null) >= SwarmBudget.PER_BATTLE) {
            host = latestHost(ship, w);
        }
        if (host != null) {
            host.merge(damage, blast, visual);
            fusedRounds++;
            return;
        }
        float lead = HomingLead.seconds(flight, (float) Math.hypot(lx - mx, ly - my));
        if (lead > 0f) {
            float[] led = HomingLead.apply(lx, ly, m.aimVx, m.aimVy, lead);
            lx = clampX(sim, led[0]);
            ly = clampY(sim, led[1]);
        }
        long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, lx, ly);
        MissilePath path = MissilePath.plan(flight, mx, my, lx, ly, seed);
        WarshipShell shell = new WarshipShell(w, ship.faction, ship, mx, my, path.landX(), path.landY(), path,
                visual, damage, penetration(w.damageType()), blast, null, footprint, flight.hitpoints(), null, 0f);
        shells.add(shell);
        if (volley != null) volley.bodies.add(shell);
    }

    /**
     * A standoff missile (the Dragonfire): it flies out to the spec's standoff distance short of the ground
     * it was sent at, hovers there painting it, and then burns a beam payload that point defence cannot
     * stop. The beam's damage is the weapon's own, committed here and made on release; the missile is a
     * hard body until then, and a missile shot down while it flies or paints fires nothing.
     */
    private void fireStandoff(Warship ship, Warship.Mount m, VanillaWeapon w, MissileFlightSpec flight,
                              MissileFlightSpec.Standoff standoff, float mx, float my, float lx, float ly,
                              float visual, float blast) {
        float dx = lx - mx;
        float dy = ly - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dist > 0f ? dx / dist : 0f;
        float uy = dist > 0f ? dy / dist : 1f;
        float along = MirvSplit.along(dist, WarshipScale.cells(standoff.standoffPx()), 0f, true, a -> 0.0);
        long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, lx, ly);
        MissilePath path = MissilePath.plan(flight, mx, my, mx + ux * along, my + uy * along, seed);
        PendingDetonation burn = detonation(ship.faction, lx, ly, w.damagePerShot(), penetration(w.damageType()), blast);
        WarshipShell.BeamPayload payload = new WarshipShell.BeamPayload(standoff.paintSeconds(),
                standoff.fireSeconds(), burn, standoff.payloadWeaponId());
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, lx, ly, path, visual, w.damagePerShot(),
                penetration(w.damageType()), blast, null, null, flight.hitpoints(), null, 0f).withBeam(payload));
    }

    /** A rack's volley: its aim points scattered over a footprint about the ground the mount chose. */
    private Volley openVolley(Warship.Mount m, Warship ship, VanillaWeapon w, MissileArchetype archetype,
                              MissilePreset preset, float mx, float my, float blast, int burst) {
        int n = Math.min(burst, SwarmBudget.PER_VOLLEY);
        long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, m.aimX, m.aimY);
        float radius = Math.min(MAX_VOLLEY_RADIUS_CELLS,
                WarheadScatter.spreadRadius(burst, blast) * preset.scatterScale() * archetype.scatterFactor());
        SwarmLayout.Sheaf sheaf = SwarmLayout.sheaf(seed, n, m.aimX, m.aimY, radius, mx, my,
                archetype.ripple(), 0f, 1f);
        WarshipShell.Footprint footprint = preset.telegraph()
                ? new WarshipShell.Footprint(m.aimX, m.aimY, radius) : null;
        return new Volley(sheaf.aimX(), sheaf.aimY(), footprint);
    }

    /** The live body of a volley that a round over the volley cap folds into. */
    private static WarshipShell liveHost(List<WarshipShell> bodies, int index) {
        if (bodies.isEmpty()) return null;
        for (int k = 0; k < bodies.size(); k++) {
            WarshipShell s = bodies.get((index + k) % bodies.size());
            if (!s.intercepted) return s;
        }
        return null;
    }

    /** The live round of this ship and weapon with the longest still to fly, which a fused round disturbs least. */
    private WarshipShell latestHost(Warship ship, VanillaWeapon w) {
        WarshipShell best = null;
        float bestLeft = 0f;
        for (WarshipShell s : shells) {
            if (s.intercepted || s.carrier() || s.hasBeam() || s.shooter != ship || !s.weapon.id().equals(w.id())) continue;
            float left = s.flightSeconds - s.elapsed();
            if (left > bestLeft) {
                bestLeft = left;
                best = s;
            }
        }
        return best;
    }

    private static float clampX(BattleSimulation sim, float x) {
        return Math.max(0.5f, Math.min(sim.getGrid().getWidth() - 0.5f, x));
    }

    private static float clampY(BattleSimulation sim, float y) {
        return Math.max(0.5f, Math.min(sim.getGrid().getHeight() - 0.5f, y));
    }

    /**
     * What the cinematic preset splits a Hammer into: {@link #SYNTHETIC_SPLIT_COUNT} bodies carrying
     * the budget, damage over the count and radius over its root, so the whole covers the
     * area the one blast did.
     */
    private static MissileFlightSpec.Mirv syntheticMirv(VanillaWeapon w, MissileFlightSpec flight) {
        int n = SYNTHETIC_SPLIT_COUNT;
        return new MissileFlightSpec.Mirv(n, 0f, 0f, false,
                SwarmBudget.shareDamage(w.damagePerShot(), n), w.damageType(), flight.maxSpeedPx() * 0.6f,
                SwarmBudget.shareRadius(w.damageRadiusPx(), n), SwarmBudget.shareRadius(w.explosionRadiusPx(), n),
                w.explosionColor(), null, SYNTHETIC_ARC_DEGREES, null, null,
                Math.max(MissileFlightSpec.MIN_WARHEAD_HITPOINTS, flight.hitpoints() / n));
    }

    /**
     * A multi-warhead missile: it flies as a carrier to a split point short of the ground it was
     * aimed at (no sooner than its spec's minimum time), and its warheads, each carrying the spec's
     * own damage and blast, are spread over that ground at random. Every landing is chosen here,
     * at fire time, and so is each warhead's flight; the carrier's arrival only starts their
     * clocks. Over a cap the warheads are fused, keeping the payload.
     */
    private void fireBloom(BattleSimulation sim, Warship ship, VanillaWeapon w, MissileFlightSpec flight,
                           MissileFlightSpec.Mirv mirv, boolean synthetic, float mx, float my,
                           float lx, float ly, long seed, MissilePreset preset, MissileArchetype archetype) {
        float dx = lx - mx;
        float dy = ly - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dist > 0f ? dx / dist : 0f;
        float uy = dist > 0f ? dy / dist : 1f;
        float splitRange = WarshipScale.cells(mirv.splitRangePx());
        final float fux = ux;
        final float fuy = uy;
        float along = synthetic ? SYNTHETIC_SPLIT_SHARE * dist
                : MirvSplit.along(dist, splitRange, mirv.minTimeToSplit(), mirv.canSplitEarly(),
                a -> MissilePath.plan(flight, mx, my, mx + fux * (float) a, my + fuy * (float) a, seed).totalSeconds());
        MissilePath carrier = MissilePath.plan(flight, mx, my, mx + ux * along, my + uy * along, seed);
        float releaseX = carrier.landX();
        float releaseY = carrier.landY();
        float blast = Math.max(1f, WarshipScale.cells(mirv.damageRadiusPx()));
        float visual = Math.max(1f, WarshipScale.cells(mirv.visualRadiusPx()));
        float warheadSpeed = Math.max(1f, WarshipScale.cells(mirv.warheadSpeedPx()));
        int count = mirv.count();
        // Over a cap the payload is fused into fewer, heavier warheads: the sums are the spec's.
        int bodies = SwarmBudget.allowed(count, liveBodies(ship), liveBodies(null));
        float[] equalDamage = new float[count];
        float[] equalRadius = new float[count];
        Arrays.fill(equalDamage, mirv.damage());
        Arrays.fill(equalRadius, blast);
        float[][] fused = SwarmBudget.fuse(equalDamage, equalRadius, bodies);
        bodies = fused[0].length;
        if (bodies < count) fusedRounds += count - bodies;
        float spread = WarheadScatter.spreadRadius(count, blast) * preset.scatterScale() * archetype.scatterFactor();
        SwarmLayout.Sheaf sheaf = SwarmLayout.sheaf(seed, bodies, lx, ly, spread, releaseX, releaseY,
                archetype.ripple(), preset.arrivalMinSeconds(), preset.arrivalMaxSeconds());
        // The fan the warheads leave the split in, dealt out at random so early arrivals are not all on one flank.
        float arc = preset.curvedBloom() ? (float) Math.toRadians(Math.min(mirv.arcDegrees(), 180f)) : 0f;
        float forward = (float) Math.atan2(uy, ux);
        int[] fan = SwarmLayout.deal(seed, bodies, PERM_STREAM);
        float loop = preset.curvedBloom() ? archetype.bloomLoop() : 0f;
        boolean flank = mirv.flanks();
        float weave = preset.curvedBloom() ? BLOOM_WEAVE : 0f;
        List<WarshipShell.Warhead> warheads = new ArrayList<>(bodies);
        for (int i = 0; i < bodies; i++) {
            float ax = clampX(sim, sheaf.aimX()[i]);
            float ay = clampY(sim, sheaf.aimY()[i]);
            float chord = (float) Math.hypot(ax - releaseX, ay - releaseY);
            // Its own travel at the warhead's speed, and then the stagger the preset gives it, so the drumroll
            // is spread across the window whatever the distance. A fast warhead is not let crawl: its stagger is
            // held to what keeps it at MIN_SPEED_SHARE of its speed, so a kinetic shotgun stays a tight burst.
            float travel = Math.max(MIN_WARHEAD_SECONDS, chord / warheadSpeed);
            float seconds = travel + Math.min(sheaf.arrivalSeconds()[i], travel * (1f / MIN_SPEED_SHARE - 1f));
            float outward = forward + ((fan[i] + MissilePath.unit(seed, PERM_STREAM + bodies + i)) / bodies - 0.5f) * arc;
            float bodyLoop = loop;
            if (flank && preset.curvedBloom()) {
                // The platforms swing out abeam of the ground, alternate ones to either side, before they come in.
                outward = BloomFlank.outward(forward, fan[i]);
                bodyLoop = BloomFlank.loop(WarshipScale.cells(mirv.flankApproachPx()), chord);
            }
            BloomPath path = BloomPath.plan(seed + i * 0x9E3779B1L, releaseX, releaseY, ax, ay, seconds,
                    outward, bodyLoop, weave);
            float radius = fused[1][i];
            warheads.add(new WarshipShell.Warhead(
                    detonation(ship.faction, ax, ay, fused[0][i], penetration(mirv.damageType()), radius),
                    path.totalSeconds(), visual * radius / blast, ax, ay, path, mirv.warheadHitpoints()));
        }
        WarshipShell.Footprint footprint = preset.telegraph()
                ? new WarshipShell.Footprint(lx, ly, spread) : null;
        float splitScale = preset == MissilePreset.CINEMATIC ? 1.6f : 1f;
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, lx, ly, carrier, visual, 0f,
                penetration(mirv.damageType()), blast, warheads, footprint, flight.hitpoints(),
                mirv.splitSound(), splitScale));
    }

    /** A stateless seed for one round: the mount, how many it has fired, the side and the ground it was aimed at. */
    private static long roundSeed(String slotId, int shotNumber, Faction faction, float x, float y) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h ^ slotId.hashCode()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ shotNumber) * 0x94D049BB133111EBL;
        h = (h ^ faction.ordinal()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ Float.floatToIntBits(x)) * 0x94D049BB133111EBL;
        h = (h ^ Float.floatToIntBits(y)) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    /** A committed detonation for a round or warhead of this strength; a heavy one keeps its own authored aftermath. */
    static PendingDetonation detonation(Faction faction, float x, float y,
                                        float damage, float penetration, float blastCells) {
        // A heavy round's own blast is the authored aftermath; the generic smoke
        // plume belongs to the lighter ones that have no blast of their own.
        boolean heavy = damage >= HEAVY_IMPACT_DAMAGE;
        return new PendingDetonation(
                0L, x, y, 0f,
                blastCells, damage, penetration,
                Math.round(damage), faction,
                /*aerialDelivery*/ false,
                /*wallDamageRadius*/ blastCells,
                /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                /*authoredAftermath*/ heavy).asShipGrade();
    }

    /**
     * The base game's multiplier on a round's damage against a shield, by damage type: kinetic doubled, high
     * explosive halved, fragmentation quartered, energy as it is.
     */
    static float shieldMultiplier(String damageType) {
        return switch (damageType == null ? "" : damageType) {
            case "KINETIC" -> 2f;
            case "HIGH_EXPLOSIVE" -> 0.5f;
            case "FRAGMENTATION" -> 0.25f;
            default -> 1f;
        };
    }

    /**
     * The base game's multiplier on a round's damage against armour, by damage type: high explosive doubled,
     * kinetic halved, fragmentation quartered, energy as it is. Fed through the armour rule at a penetration of one,
     * {@code d * d / (d + armour)} is the base game's own armour reduction.
     */
    static float armourMultiplier(String damageType) {
        return switch (damageType == null ? "" : damageType) {
            case "KINETIC" -> 0.5f;
            case "HIGH_EXPLOSIVE" -> 2f;
            case "FRAGMENTATION" -> 0.25f;
            default -> 1f;
        };
    }

    /** The damage type a round of {@code w} strikes with: a carrier's warheads', or the weapon's own. */
    static String hullDamageType(VanillaWeapon w) {
        if (w == null) return null;
        MissileFlightSpec flight = w.flight();
        if (flight != null && flight.mirv() != null) return flight.mirv().damageType();
        return w.damageType();
    }

    public static float penetration(String damageType) {
        return switch (damageType) {
            case "KINETIC" -> PENETRATION_KINETIC;
            case "HIGH_EXPLOSIVE" -> PENETRATION_EXPLOSIVE;
            case "ENERGY" -> PENETRATION_ENERGY;
            default -> PENETRATION_OTHER;
        };
    }
}
