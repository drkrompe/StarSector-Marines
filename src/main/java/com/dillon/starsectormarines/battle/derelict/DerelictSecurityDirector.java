package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Report;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Kind;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Order;
import com.dillon.starsectormarines.battle.drone.FlockSystem;
import com.dillon.starsectormarines.battle.drone.FlockTask;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.combat.fx.FxHash;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.ruins.RuinsDoorStates;
import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService.ChargeView;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.SeparationSystem;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The security network director of a derelict site (`derelict-security-network.md`, slice 2): a mission director, like
 * the Rav's swarm director, not a mirrored squad commander. Each pulse it learns what the network honestly knows (its
 * side's merged contacts and its flocks' reports), stirs the zones those contacts are in and the zones whose walkers
 * were hurt, calls the contingency when the vault is breached, then gives each walker its posture
 * ({@link WalkerPosture}) and each flock its task ({@link FlockTasking}).
 *
 * <p>A zone with no flock up (none connected, {@link FlockCommandPort#NONE}, or its flock destroyed) is its walkers'
 * own eyes: they come to and siege frames fire on what the network's units hold, not only what flocks pin and see.
 * That is the same knowledge the flocks would relay, reached by fewer eyes.
 */
public final class DerelictSecurityDirector {

    public static final String ISSUER = "derelict-security";
    /** Ticks between pulses (half a second). */
    static final int PULSE_TICKS = 15;
    /** How old a contact may be and still count as held. UNTUNED (3 s). */
    static final int FRESH_TICKS = 90;
    /** A standing order whose cell moved less than this is not re-issued. */
    static final int REISSUE_CELLS = 3;
    static final int HOLD_RADIUS = 3;
    static final int EGRESS_RADIUS = 4;
    /** How near a place of its round a rounds walker stands before it counts as there. */
    static final int ROUND_RADIUS = 1;
    /** How near a dead door's middle a forcing frame holds. */
    static final int FORCE_RADIUS = 1;
    /** How far off its bay, in cells, a walker is sent to stand to re-arm or reload. */
    static final float BAY_STAND_CELLS = 2f;
    /** How near a dead door's middle, in cells, a hall frame stands pressed against it. */
    static final float RAM_CELLS = 3f;
    /** Confidence a relayed sighting is handed to a siege frame at. */
    static final float SPOT_CONFIDENCE = 0.9f;

    /** One walker lance the network commands: its frame, zone and post. */
    public record Walker(int squadId, DerelictWalkerFrame frame, int zoneId, int postX, int postY) { }

    private final SecurityNetwork network;
    private final List<Walker> walkers;
    /** A fallen hull's claim this network grows from, or null at a site network (`fallen-hull-nouns.md`). */
    private FallenHull hull;
    /** Whether the walkers' bodies are hooked into the damage pipeline, the leg modifier and the straddle. */
    private boolean armed;
    private final List<int[]> egress;
    private final int vaultX;
    private final int vaultY;
    private FlockCommandPort flocks = FlockCommandPort.NONE;
    private DerelictLook look = DerelictLook.DOMAIN;
    private final Map<Integer, Order> issued = new TreeMap<>();
    private final Map<Integer, Boolean> walking = new TreeMap<>();
    private final Map<Integer, Float> durability = new TreeMap<>();
    private final Map<Integer, int[]> stirredAt = new TreeMap<>();
    private final Set<Long> haul = new TreeSet<>();
    private final Map<Integer, Long> nodes = new TreeMap<>();
    private final Map<Integer, Float> nodeDurability = new TreeMap<>();
    private final Map<Long, Integer> posts = new TreeMap<>();
    private final Map<Integer, Integer> junctions = new TreeMap<>();
    private final List<FieldDrone> drones = new ArrayList<>();
    /** Each rounds walker's round, by lance (`derelict-watch.md`, Movement). */
    private final Map<Integer, PatrolRound> rounds = new TreeMap<>();
    /** Each hall-bound frame's dead door, and its ram against it (`derelict-watch.md`, Medium and large walkers). */
    private final Map<Integer, RuinsDelve.LeafDoor> deadDoors = new TreeMap<>();
    private final Map<Integer, DerelictWalkerClass.Ram> rams = new TreeMap<>();
    /** Where each ramming frame stands against its door: the nearest cell in its hall it fits. */
    private final Map<Integer, int[]> ramStands = new TreeMap<>();
    private final List<RuinsDelve.LeafDoor> forced = new ArrayList<>();
    /** The security bulkheads, shut until the hunting stage. */
    private List<Bulkheads.Bulkhead> bulkheads = List.of();
    private boolean bulkheadsOpened;
    /** Hall frames whose way to the egress is too narrow: they keep their hall in the contingency. */
    private final Set<Integer> hallBound = new TreeSet<>();

    // ---- the facility's wake (`derelict-watch.md`, Waking) ----
    private final FacilityWake wake = new FacilityWake();
    private ServiceDucts ducts = new ServiceDucts(List.of());
    /** Each zone's sealed reserve bay, by zone. */
    private final Map<Integer, Long> reserveBays = new TreeMap<>();
    private ReserveLauncher reserveLauncher = (zoneId, x, y) -> -1;
    /** The flocks released from reserve bays, by flock id: they sweep while their zone sleeps. */
    private final Set<Integer> sweeping = new TreeSet<>();

    /** Puts a zone's second flock up where its reserve bay stood; returns its flock id, or -1 when none went up. */
    public interface ReserveLauncher {
        int launch(int zoneId, float x, float y);
    }

    // ---- repair (`derelict-watch.md`, Repair) ----
    /** A zone's service bay in the field: its entity and the stock it holds. */
    private static final class Bay {
        final long id;
        final ServiceBay rules = new ServiceBay();

        Bay(long id) {
            this.id = id;
        }
    }

    private final Map<Integer, Bay> serviceBays = new TreeMap<>();
    /** Each MG-12 tender's rules, by lance (`derelict-watch.md`, Field repair). */
    private final Map<Integer, FieldTender> fieldTenders = new TreeMap<>();
    /** Each walker lance's re-arming at its bay, by lance (`derelict-watch.md`, Re-arming). */
    private final Map<Integer, BayRearm> rearming = new TreeMap<>();
    private final Map<Integer, Long> serviceBayIds = new TreeMap<>();
    private final List<Wreck> wrecks = new ArrayList<>();
    private long nextWreck = 1L;
    /** Each walker lance's last place while it stood, for its wreck. */
    private final Map<Integer, float[]> lastStood = new TreeMap<>();
    /** Each post's cell while it stood, for its wreck. */
    private final Map<Long, int[]> postCells = new TreeMap<>();
    /** Lances and posts already raised once: their next fall leaves scrap. */
    private final Set<Integer> raisedLances = new TreeSet<>();
    private final Set<Long> raisedPosts = new TreeSet<>();
    /** Where each company charge armed at the last look stands, to hear it go off. */
    private Map<Long, float[]> armedCharges = new HashMap<>();
    /** Raised posts the network stands, by the zone they answer to; read by the install's post stander. */
    private PostStander postStander = (zoneId, x, y) -> 0L;

    /** Stands a raised post of a zone at a cell; returns it, or 0 when it cannot. */
    public interface PostStander {
        long stand(int zoneId, int x, int y);
    }

    /** How near a wreck, in cells, a unit of the watch contests the company's scrapping. */
    static final float CONTEST_CELLS = 4f;

    /** A zone's repair drone in the field: its entity, zone, home, rules and current goal. */
    private static final class FieldDrone {
        final long id;
        final int zoneId;
        final float homeX;
        final float homeY;
        final RepairDrone rules = new RepairDrone();
        RepairDrone.Goal goal;
        float lastDurability = -1f;

        FieldDrone(long id, int zoneId, float homeX, float homeY) {
            this.id = id;
            this.zoneId = zoneId;
            this.homeX = homeX;
            this.homeY = homeY;
            this.goal = new RepairDrone.Goal(RepairDrone.Kind.HOME, homeX, homeY, 0L);
        }
    }
    private final Map<Long, Float> postDurability = new TreeMap<>();
    private final Map<Long, Body> bodies = new TreeMap<>();

    /**
     * A walker's body in the field (`ruins-guardians.md`): its knees, the vault frame's brace (null on any other
     * frame), and what it last read.
     */
    private static final class Body {
        final WalkerKnees knees = new WalkerKnees();
        final VaultFrameBrace brace;
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        float[] cycles = new float[0];
        int hits;
        /** Knees whose loss has already burst, a bit a leg. */
        int burst;

        Body(VaultFrameBrace brace) {
            this.brace = brace;
        }
    }

    /** How far a frame may shift in one tick and still count as standing, cells. */
    static final float STILL_CELLS_PER_TICK = 0.005f;

    /**
     * Gives each walker its knees and its footprint, and the vault frame its brace (`ruins-guardians.md`): the damage
     * pipeline asks the walker how each hit lands, its speed follows its legs, and small bodies pass under it between
     * its feet while it stands.
     */
    public void armWalkers(BattleSimulation sim) {
        for (Walker walker : walkers) {
            for (int i = 0, n = sim.squadMemberCount(walker.squadId()); i < n; i++) {
                bodies.put(sim.squadMemberAt(walker.squadId(), i),
                        new Body(walker.frame() == DerelictWalkerFrame.VAULT ? new VaultFrameBrace() : null));
            }
        }
        if (bodies.isEmpty() && hull == null) return;
        hookBodies(sim);
    }

    private void hookBodies(BattleSimulation sim) {
        if (armed) return;
        armed = true;
        sim.getRoster().hitScale((target, attacker, damage) -> bodyHit(sim, target, attacker, damage));
        sim.getRoster().modifiers().register(new LegModifier());
        sim.straddle(new Straddle(sim));
    }

    /** A vault frame's stance, or null for a unit that is none. */
    public VaultFrameBrace.Stance braceOf(long unitId) {
        Body body = bodies.get(unitId);
        return body == null || body.brace == null ? null : body.brace.stance();
    }

    /** A walker's knees, or null for a unit that is none. */
    public WalkerKnees kneesOf(long unitId) {
        Body body = bodies.get(unitId);
        return body == null ? null : body.knees;
    }

    private float bodyHit(BattleSimulation sim, long target, long attacker, float damage) {
        Body body = bodies.get(target);
        if (body == null || attacker == target || sim.resolveUnit(attacker) == 0L) return 1f;
        World world = sim.world();
        if (!world.hasMechLoadout(target)) return 1f;
        float dx = world.x(attacker) - world.x(target);
        float dy = world.y(attacker) - world.y(target);
        // The guns' frame for the brace; the legs hang off the hips, so the knees read the hips' frame.
        float bearing = VaultFrameBrace.bearing(world.mechLoadout(target).torsoFacingDegrees, dx, dy);
        float legBearing = VaultFrameBrace.bearing(world.mechHipFacingDegrees(target), dx, dy);
        boolean downed = body.knees.downed();
        boolean under = !downed && WalkerFootprint.under(dx, dy);
        boolean braced = body.brace != null && body.brace.stance() == VaultFrameBrace.Stance.BRACED;
        if (!downed && body.knees.hit(legBearing, braced, under, roll(target, attacker, body.hits++), damage) == 0f) {
            return 0f;
        }
        if (body.knees.downed()) return WalkerKnees.DOWNED_SCALE;
        if (under) return VaultFrameBrace.REAR_SCALE;
        return body.brace != null ? body.brace.hit(bearing) : 1f;
    }

    /** A roll in [0, 1) fixed by who struck whom and the hit's count, so a replay rolls the same. */
    static float roll(long target, long attacker, int n) {
        long h = target * 0x9E3779B97F4A7C15L ^ attacker * 0xC2B2AE3D27D4EB4FL ^ (n + 1L) * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1 << 24);
    }

    /** Ticks between sparks off a lost knee (about a third of a second). */
    static final int KNEE_SPARK_TICKS = 10;

    /**
     * Every tick: a lost knee bursts once and throws sparks while the walker stands (presentation only, its cadence
     * hashed from the walker and the tick, never the battle's random stream), and each vault frame braces or comes
     * free, its guns cycling at its stance's rate.
     */
    private void tickBodies(BattleSimulation sim) {
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        for (Map.Entry<Long, Body> entry : bodies.entrySet()) {
            long id = entry.getKey();
            Body body = entry.getValue();
            if (sim.resolveUnit(id) == 0L || !world.hasMechLoadout(id)) continue;
            MechLoadoutComponent mech = world.mechLoadout(id);
            sparkKnees(sim, id, body, world.mechHipFacingDegrees(id), tick);
            if (body.brace == null) continue;
            float x = world.x(id);
            float y = world.y(id);
            boolean moving = !Float.isNaN(body.lastX)
                    && Math.hypot(x - body.lastX, y - body.lastY) > STILL_CELLS_PER_TICK;
            body.lastX = x;
            body.lastY = y;
            long target = sim.targetOf(id);
            boolean engaged = target != 0L && sim.resolveUnit(target) != 0L;
            float bearing = engaged ? VaultFrameBrace.bearing(mech.torsoFacingDegrees,
                    world.x(target) - x, world.y(target) - y) : 0f;
            // A frame missing a front leg cannot plant on the walls, and a downed one braces on nothing.
            boolean planted = body.knees.canBrace() && !body.knees.downed();
            body.brace.tick(BattleSimulation.TICK_DT, moving || !planted, engaged, bearing);
            MechWeaponMount[] mounts = mech.mounts();
            if (body.cycles.length != mounts.length) body.cycles = new float[mounts.length];
            for (int i = 0; i < mounts.length; i++) {
                MechWeaponMount mount = mounts[i];
                if (mount == null) continue;
                mount.cycleRemaining = VaultFrameBrace.stretch(body.cycles[i], mount.cycleRemaining,
                        body.brace.fireScale());
                body.cycles[i] = mount.cycleRemaining;
            }
        }
    }

    private static void sparkKnees(BattleSimulation sim, long id, Body body, float facing, int tick) {
        if (body.knees.lostCount() == 0) return;
        EffectsService effects = sim.effects();
        World world = sim.world();
        for (int leg = 0; leg < WalkerKnees.LEGS; leg++) {
            if (!body.knees.lost(leg)) continue;
            float[] knee = WalkerKnees.kneeAt(world.x(id), world.y(id), facing, leg);
            float dx = knee[0] - world.x(id);
            float dy = knee[1] - world.y(id);
            float d = Math.max(1e-3f, (float) Math.hypot(dx, dy));
            int bit = 1 << leg;
            if ((body.burst & bit) == 0) {
                body.burst |= bit;
                // The moment it goes: a flare and a puff at the knee.
                effects.emitFireBurst(knee[0], knee[1], 0.6f);
                effects.emitSmokePuff(knee[0], knee[1], 0.5f);
            } else if (FxHash.onCadence(id, tick, KNEE_SPARK_TICKS, 0x4b + leg)) {
                effects.emitClawStrike(knee[0], knee[1], dx / d, dy / d, 0f);
            }
        }
    }

    /** A walker's speed follows its legs: whole, limping on three, or downed on two. */
    private final class LegModifier implements UnitModifier {
        @Override public String name(long unit) { return "unitModifierWalkerLegs"; }

        @Override public Set<UnitStat> stats() { return EnumSet.of(UnitStat.MOVE_SPEED); }

        @Override public boolean appliesTo(long unit) {
            Body body = bodies.get(unit);
            return body != null && body.knees.lostCount() > 0;
        }

        @Override public float multiplier(long unit, UnitStat stat) {
            Body body = bodies.get(unit);
            return body == null || stat != UnitStat.MOVE_SPEED ? 1f : body.knees.speedShare();
        }
    }

    /** Standing walkers: a small body passes under one, kept off its feet; a downed one blocks its body. */
    private final class Straddle implements SeparationSystem.Straddle {
        private final BattleSimulation sim;

        Straddle(BattleSimulation sim) {
            this.sim = sim;
        }

        @Override public int feet(long unit, float[] out) {
            Body body = bodies.get(unit);
            if (body == null || body.knees.downed()) return 0;
            World world = sim.world();
            if (!world.hasMechLoadout(unit)) return 0;
            return WalkerFootprint.feet(world.x(unit), world.y(unit), world.mechHipFacingDegrees(unit), out);
        }

        @Override public boolean fitsUnder(long unit) {
            UnitType type = sim.getRoster().identity().type(unit);
            return WalkerFootprint.fitsUnder(type.radius, type.isMech() || type.isStatic());
        }

        @Override public float footRadius() { return WalkerFootprint.FOOT_RADIUS; }
    }

    public DerelictSecurityDirector(SecurityNetwork network, List<Walker> walkers, List<int[]> egress,
                                    int vaultX, int vaultY) {
        this.network = network;
        this.walkers = new ArrayList<>(walkers);
        this.egress = List.copyOf(egress);
        this.vaultX = vaultX;
        this.vaultY = vaultY;
    }

    /** Connects the flock model (`air-nouns.md`, Flocks). */
    public void connectFlocks(FlockCommandPort port) {
        flocks = port == null ? FlockCommandPort.NONE : port;
    }

    /** Draws the network's posts and nodes in its lineage's look (slice 9). Presentation only. */
    public void wearLook(DerelictLook look) {
        this.look = look == null ? DerelictLook.DOMAIN : look;
    }

    /** The lineage look the network's posts and nodes are drawn in. */
    public DerelictLook look() {
        return look;
    }

    /** Stands a zone's sealed reserve bay (`derelict-watch.md`, Waking). */
    public void standReserveBay(int zoneId, long entityId) {
        reserveBays.put(zoneId, entityId);
    }

    /** How a released reserve bay's flock goes up. */
    public void launchReserves(ReserveLauncher launcher) {
        reserveLauncher = launcher == null ? (zoneId, x, y) -> -1 : launcher;
    }

    /** Lays the site's service ducts. */
    public void layDucts(ServiceDucts ducts) {
        this.ducts = ducts == null ? new ServiceDucts(List.of()) : ducts;
    }

    public ServiceDucts ducts() {
        return ducts;
    }

    public FacilityWake wake() {
        return wake;
    }

    /** Each zone's reserve bay entity, by zone; a bay that has opened or fallen is still listed. */
    public Map<Integer, Long> reserveBays() {
        return Collections.unmodifiableMap(reserveBays);
    }

    /** Stands a zone's service bay (`derelict-watch.md`, Repair): where its repair drone takes on loads. */
    public void standServiceBay(int zoneId, long entityId) {
        serviceBays.put(zoneId, new Bay(entityId));
        serviceBayIds.put(zoneId, entityId);
    }

    /** Each zone's service bay entity, by zone. */
    public Map<Integer, Long> serviceBays() {
        return Collections.unmodifiableMap(serviceBayIds);
    }

    /** The stock a zone's service bay still holds, or -1 with none. */
    public float bayStock(int zoneId) {
        Bay bay = serviceBays.get(zoneId);
        return bay == null ? -1f : bay.rules.stock();
    }

    /** Every wreck the network's units have left, in the order they fell. */
    public List<Wreck> wrecks() {
        return Collections.unmodifiableList(wrecks);
    }

    /** How a raised post is stood again. */
    public void standPostsWith(PostStander stander) {
        postStander = stander == null ? (zoneId, x, y) -> 0L : stander;
    }

    /** Puts a rounds walker's lance on its zone's round (`derelict-watch.md`, Movement). */
    public void walkRound(int squadId, PatrolRound round) {
        rounds.put(squadId, round);
    }

    /** The round a lance walks, or null for a walker with none. */
    public PatrolRound roundOf(int squadId) {
        return rounds.get(squadId);
    }

    /** The cells along the vault's lift route the walkers hold in the contingency. */
    public List<int[]> egress() {
        return egress;
    }

    /** Keeps a hall frame on its hall in the contingency: its way to the egress does not fit it. */
    public void keepHall(int squadId) {
        hallBound.add(squadId);
    }

    /**
     * Gives a hall-bound frame the dead door it forces once awake: its leaves give way after
     * {@link DerelictWalkerClass#FORCE_SECONDS} pressed against it, and the frame is bound no longer.
     */
    public void forceDoor(int squadId, RuinsDelve.LeafDoor door, int standX, int standY) {
        deadDoors.put(squadId, door);
        ramStands.put(squadId, new int[] {standX, standY});
        rams.put(squadId, new DerelictWalkerClass.Ram());
    }

    /** The dead door a hall-bound frame is to force, or null; it stays given once forced. */
    public RuinsDelve.LeafDoor deadDoorOf(int squadId) {
        return deadDoors.get(squadId);
    }

    /** Whether a hall frame is still kept to its hall. */
    public boolean hallBound(int squadId) {
        return hallBound.contains(squadId);
    }

    /** How far a hall frame's ram is toward forcing its door, 0 to 1; 0 for a frame with none. */
    public float ramProgress(int squadId) {
        DerelictWalkerClass.Ram ram = rams.get(squadId);
        return ram == null ? 0f : ram.progress();
    }

    /** The dead doors hall frames have forced, in the order they gave way. */
    public List<RuinsDelve.LeafDoor> forcedDoors() {
        return Collections.unmodifiableList(forced);
    }

    /** Lays the site's security bulkheads, shut until the hunting stage, each drawn sealed meanwhile. */
    public void layBulkheads(BattleSimulation sim, List<Bulkheads.Bulkhead> laid) {
        bulkheads = List.copyOf(laid);
        RuinsDoorStates doors = doorStates(sim);
        if (doors == null) return;
        for (Bulkheads.Bulkhead bulkhead : bulkheads) {
            if (doors.stanceOf(bulkhead.door()) == null) doors.add(bulkhead.door(), RuinsDelve.DoorStance.SEALED);
        }
    }

    /** The site's leaf doors as drawn, or null on ground with none. */
    private static RuinsDoorStates doorStates(BattleSimulation sim) {
        RuinsWorks works = sim.getRuinsWorks();
        return works == null ? null : works.doorStates();
    }

    public List<Bulkheads.Bulkhead> bulkheads() {
        return bulkheads;
    }

    /** Whether the bulkheads stand open. */
    public boolean bulkheadsOpened() {
        return bulkheadsOpened;
    }

    /** Stands a zone's node: the structure whose loss isolates the zone (slice 5). */
    public void standNode(int zoneId, long entityId) {
        nodes.put(zoneId, entityId);
    }

    /** Puts a zone's repair drone in the field (slice 6), homed at {@code (homeX, homeY)}. */
    public void standRepairDrone(int zoneId, long entityId, float homeX, float homeY) {
        drones.add(new FieldDrone(entityId, zoneId, homeX, homeY));
    }

    /** The repair drones' entities, in the order they were stood. */
    public List<Long> repairDrones() {
        List<Long> out = new ArrayList<>();
        for (FieldDrone drone : drones) out.add(drone.id);
        return out;
    }

    /** The stock a zone's repair drone has left, or -1 with none. */
    public float repairStock(int zoneId) {
        for (FieldDrone drone : drones) if (drone.zoneId == zoneId) return drone.rules.stock();
        return -1f;
    }

    /** Feeds a zone through a ruins works junction (slice 8): thrown to its store, the zone goes dark. */
    public void feedThrough(int zoneId, int junction) {
        junctions.put(zoneId, junction);
    }

    /** Stands a folded post of a zone (slice 7): powered only while the zone is awake. */
    public void standPost(int zoneId, long entityId) {
        posts.put(entityId, zoneId);
    }

    /** Each folded post's zone, by post entity. */
    public Map<Long, Integer> posts() {
        return Collections.unmodifiableMap(posts);
    }

    /** Each zone's node entity, by zone id. */
    public Map<Integer, Long> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public SecurityNetwork network() {
        return network;
    }

    public List<Walker> walkers() {
        return Collections.unmodifiableList(walkers);
    }

    /**
     * A walker the fallen hull's forge printed (`fallen-hull-nouns.md`, Laws: every new unit is printed) joins the
     * network: its body is armed as the first walkers' were, and from the next pulse it takes its posture in its zone,
     * unless the hull commands it ({@link FallenHull#commands}).
     */
    public void addWalker(BattleSimulation sim, Walker walker) {
        if (network.zone(walker.zoneId()) == null) throw new IllegalArgumentException("No zone " + walker.zoneId());
        walkers.add(walker);
        for (int i = 0, n = sim.squadMemberCount(walker.squadId()); i < n; i++) {
            bodies.put(sim.squadMemberAt(walker.squadId(), i), new Body(null));
        }
        hookBodies(sim);
    }

    /** Posts a walker lance to another zone and post: a stake train's escort to the stake it brought up. */
    public void repost(int squadId, int zoneId, int postX, int postY) {
        if (network.zone(zoneId) == null) throw new IllegalArgumentException("No zone " + zoneId);
        for (int i = 0; i < walkers.size(); i++) {
            Walker w = walkers.get(i);
            if (w.squadId() != squadId) continue;
            walkers.set(i, new Walker(squadId, w.frame(), zoneId, postX, postY));
            issued.remove(squadId);
            walking.remove(squadId);
        }
    }

    /** Grows this network from a fallen hull's claim: the hull's rules run each pulse before the walkers' postures. */
    public void attachHull(FallenHull hull) {
        this.hull = hull;
    }

    /** The fallen hull this network grows from, or null. */
    public FallenHull hull() {
        return hull;
    }

    /** The posture last given to a walker lance, or null before its first. */
    public Order orderOf(int squadId) {
        return issued.get(squadId);
    }

    /** The units the network takes for the haul's escort since the contingency. */
    public Set<Long> haul() {
        return Collections.unmodifiableSet(haul);
    }

    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        flyDrones(sim);
        tickBodies(sim);
        if (hull != null) hull.hear(sim, tick);
        if (tick % PULSE_TICKS != 0) return;
        List<Report> reports = flocks.reports();
        List<Contact> own = ownContacts(sim, tick);
        List<Contact> flockSeen = new ArrayList<>();
        List<Contact> pins = new ArrayList<>();
        for (Report report : reports) {
            for (Contact seen : report.seen()) if (tick - seen.observedTick() <= FRESH_TICKS) flockSeen.add(seen);
            Contact pinned = report.pinned();
            if (pinned != null) pins.add(pinned);
        }
        List<Contact> all = merge(own, flockSeen);
        Set<Integer> flocked = new TreeSet<>();
        for (Report report : reports) flocked.add(report.homeZoneId());

        for (Contact contact : all) {
            SecurityZone zone = network.zoneAt(contact.cellX(), contact.cellY());
            if (zone == null) continue;
            network.stir(zone.id, tick);
            stirredAt.put(zone.id, new int[] {contact.cellX(), contact.cellY()});
        }
        RuinsWorks works = sim.getRuinsWorks();
        if (works != null && !junctions.isEmpty()) DerelictPower.apply(network, junctions, works.graph()::position);
        DerelictNode.watch(network, nodes, id -> sim.resolveUnit(id) != 0L);
        for (Map.Entry<Integer, Long> node : nodes.entrySet()) {
            SecurityZone zone = network.zone(node.getKey());
            flocks.nodeStands(node.getKey(), zone.online());
            if (!zone.nodeAlive()) continue;
            // Damage to the node is damage to a unit of the zone: it wakes.
            float now = sim.world().hp(node.getValue()) + sim.world().armor(node.getValue());
            Float before = nodeDurability.put(node.getKey(), now);
            if (before != null && now < before - 0.5f) {
                network.stir(zone.id, tick);
                stirredAt.put(zone.id, new int[] {zone.nodeCellX, zone.nodeCellY});
            }
        }
        for (Walker walker : walkers) {
            float now = durability(sim, walker.squadId());
            Float before = durability.put(walker.squadId(), now);
            if (before != null && now < before - 0.5f) {
                network.stir(walker.zoneId(), tick);
                stirredAt.put(walker.zoneId(), new int[] {walker.postX(), walker.postY()});
            }
        }
        RuinsVaultObjective vault = vault(sim);
        if (vault != null && vault.breached() && !network.contingency()) network.breachVault(tick);
        // A fallen hull's vault is its autoforge: its breach calls the contingency.
        if (hull != null && hull.breached(sim) && !network.contingency()) network.breachVault(tick);
        if (network.contingency()) {
            for (Contact contact : all) {
                if (WalkerPosture.chebyshev(contact.cellX(), contact.cellY(), vaultX, vaultY)
                        <= RuinsVaultObjective.ESCORT_RADIUS) haul.add(contact.unitId());
            }
        }
        for (Map.Entry<Long, Integer> post : posts.entrySet()) {
            long id = post.getKey();
            if (sim.resolveUnit(id) == 0L) continue;
            float now = sim.world().hp(id) + sim.world().armor(id);
            Float before = postDurability.put(id, now);
            if (before != null && now < before - 0.5f) network.stir(post.getValue(), tick);
        }
        network.settle(tick);
        tickWake(sim);
        tickWrecks(sim);
        if (hull != null) hull.pulse(sim, this, tick, all);
        for (Map.Entry<Long, Integer> post : posts.entrySet()) {
            long id = post.getKey();
            if (sim.resolveUnit(id) == 0L) continue;
            if (DerelictPost.unfolded(network.zone(post.getValue()))) sim.turretState().restorePower(id);
            else sim.turretState().cutPower(id);
        }

        List<Long> haulIds = new ArrayList<>(haul);
        List<Contact> haulContacts = new ArrayList<>();
        for (Contact contact : all) if (haul.contains(contact.unitId())) haulContacts.add(contact);
        for (Walker walker : new ArrayList<>(walkers)) {
            if (sim.squadMemberCount(walker.squadId()) <= 0) continue;
            // A fallen hull's workers, trains and escorts are walked by the hull's own rules.
            if (hull != null && hull.commands(walker.squadId())) continue;
            SecurityZone zone = network.zone(walker.zoneId());
            if (walker.frame() == DerelictWalkerFrame.TENDER) {
                tend(sim, walker, zone, tick);
                continue;
            }
            // A zone whose flock is gone is its walkers' own eyes: they come to and fire on what they hold.
            boolean blind = !flocked.contains(walker.zoneId());
            PatrolRound round = rounds.get(walker.squadId());
            int[] place = null;
            if (round != null) {
                long lead = sim.squadMemberAt(walker.squadId(), 0);
                place = round.advance(sim.world().x(lead), sim.world().y(lead), PULSE_TICKS * BattleSimulation.TICK_DT);
            }
            Order order = WalkerPosture.decide(walker.frame(), zone, network.contingency(),
                    walker.postX(), walker.postY(), place, !hallBound.contains(walker.squadId()),
                    blind ? merge(pins, own) : pins,
                    blind ? all : flockSeen, haulIds, egress, walker.frame().maxWeaponRange());
            order = ram(sim, walker, zone, order);
            Order rearm = rearm(sim, walker, zone, order);
            if (rearm != null) {
                apply(sim, walker, rearm, tick, walker.frame() + " walks to its service bay to re-arm its racks");
                continue;
            }
            apply(sim, walker, order, tick);
        }
        for (FieldDrone drone : drones) directDrone(sim, drone, all, tick);
        for (Report report : reports) {
            SecurityZone zone = network.zone(report.homeZoneId());
            if (zone == null) continue;
            int[] stir = stirredAt.getOrDefault(zone.id, new int[] {zone.nodeCellX, zone.nodeCellY});
            FlockTasking.Order order = FlockTasking.decide(report, zone, network.contingency(), all, haulContacts,
                    stir[0], stir[1]);
            if (sweeping.contains(report.flockId()) && zone.state() != SecurityZone.State.AWAKE
                    && !network.contingency() && report.strength() * 3f >= 1f) {
                // A released reserve flock is out to be seen: it sweeps its sleeping zone a corner at a time.
                int[] sweep = FacilityWake.sweepPoint(zone, tick);
                order = new FlockTasking.Order(FlockTask.PROBE, sweep[0], sweep[1], -1L);
            }
            if (hull != null && zone.state() == SecurityZone.State.AWAKE && !network.contingency()
                    && report.strength() >= FlockTasking.RETURN_STRENGTH
                    && (order == null || order.task() == FlockTask.PROBE || order.task() == FlockTask.SCREEN)) {
                // A fallen hull's awake flock with nothing to strike ranges over its claim: the town, the ways in.
                int[] range = hull.rangePoint(zone.id, report.flockId(), tick);
                if (range != null) order = new FlockTasking.Order(FlockTask.PROBE, range[0], range[1], -1L);
            }
            if (order != null && ducts.opened() && !report.docked() && !flocks.inDuct(report.flockId())) {
                // The way round a megastructure is long: a flock takes a duct where it is the shorter way.
                ServiceDucts.Transit transit = ducts.plan(network, zone.id, report.cellX() + 0.5f,
                        report.cellY() + 0.5f, order.anchorX(), order.anchorY(),
                        openWay(sim, report.cellX(), report.cellY(), order.anchorX(), order.anchorY()),
                        FlockSystem.ANCHOR_SPEED);
                if (transit != null) flocks.duct(report.flockId(), transit);
            }
            if (order != null) {
                flocks.order(report.flockId(), order.task(), order.anchorX(), order.anchorY(), order.targetUnitId());
            }
        }
    }

    /** A repair drone's goal this pulse, from what it may mend in its zone and whether it is under fire. */
    private void directDrone(BattleSimulation sim, FieldDrone drone, List<Contact> hostiles, int tick) {
        if (sim.resolveUnit(drone.id) == 0L) return;
        World world = sim.world();
        float x = world.x(drone.id);
        float y = world.y(drone.id);
        float durability = world.hp(drone.id) + (world.hasArmor(drone.id) ? world.armor(drone.id) : 0f);
        boolean hit = drone.lastDurability >= 0f && durability < drone.lastDurability - 0.01f;
        drone.lastDurability = durability;
        boolean threatened = false;
        for (Contact contact : hostiles) {
            if (Math.max(Math.abs(contact.cellX() + 0.5f - x), Math.abs(contact.cellY() + 0.5f - y))
                    <= RepairDrone.THREAT_CELLS) {
                threatened = true;
                break;
            }
        }
        SecurityZone zone = network.zone(drone.zoneId);
        List<RepairDrone.Fallen> fallen = new ArrayList<>();
        for (Wreck wreck : wrecks) {
            if (wreck.zoneId == drone.zoneId && wreck.raisable()) {
                fallen.add(new RepairDrone.Fallen(wreck.id, wreck.x, wreck.y));
            }
        }
        Bay bay = serviceBays.get(drone.zoneId);
        RepairDrone.Bay at = bay == null || sim.resolveUnit(bay.id) == 0L ? null
                : new RepairDrone.Bay(world.x(bay.id), world.y(bay.id), bay.rules.stock());
        drone.goal = drone.rules.decide(x, y, drone.homeX, drone.homeY, zone != null && zone.online(),
                hit || threatened, charges(sim, drone.zoneId), fallen, at, tick);
    }

    /** What a zone's repair drone may mend: its posts, its flock's cradle and its walkers, with how whole each is. */
    private List<RepairDrone.Charge> charges(BattleSimulation sim, int zoneId) {
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, Integer> post : posts.entrySet()) if (post.getValue() == zoneId) ids.add(post.getKey());
        ids.addAll(flocks.cradles(zoneId));
        for (Walker walker : walkers) {
            if (walker.zoneId() != zoneId) continue;
            for (int i = 0, n = sim.squadMemberCount(walker.squadId()); i < n; i++) {
                ids.add(sim.squadMemberAt(walker.squadId(), i));
            }
        }
        World world = sim.world();
        List<RepairDrone.Charge> out = new ArrayList<>();
        for (long id : ids) {
            if (sim.resolveUnit(id) == 0L) continue;
            float share;
            if (world.hasMechLoadout(id)) {
                float max = world.maxArmor(id);
                if (max <= 0f) continue;
                share = world.armor(id) / max;
            } else {
                float max = world.maxHp(id) + world.maxArmor(id);
                if (max <= 0f) continue;
                share = (world.hp(id) + world.armor(id)) / max;
            }
            out.add(new RepairDrone.Charge(id, world.x(id), world.y(id), share));
        }
        return out;
    }

    /** Every tick: each repair drone flies toward its goal, or mends what it reached. */
    private void flyDrones(BattleSimulation sim) {
        float dt = BattleSimulation.TICK_DT;
        World world = sim.world();
        for (FieldDrone drone : drones) {
            if (sim.resolveUnit(drone.id) == 0L) continue;
            RepairDrone.Goal goal = drone.goal;
            if (goal.kind() == RepairDrone.Kind.MEND) {
                if (sim.resolveUnit(goal.entityId()) == 0L) continue;
                float points = drone.rules.draw(dt);
                drone.rules.refund(points - mend(world, goal.entityId(), points));
                continue;
            }
            if (goal.kind() == RepairDrone.Kind.RAISE) {
                Wreck wreck = wreck(goal.entityId());
                if (wreck == null || !wreck.raisable()) continue;
                drone.rules.refund(wreck.work(drone.rules.drawRaise(dt)));
                if (wreck.state() == Wreck.State.RAISED) raise(sim, wreck);
                continue;
            }
            if (goal.kind() == RepairDrone.Kind.RELOAD) {
                Bay bay = serviceBays.get(drone.zoneId);
                if (bay != null && sim.resolveUnit(bay.id) != 0L) drone.rules.reload(bay.rules);
                continue;
            }
            float x = world.x(drone.id);
            float y = world.y(drone.id);
            float dx = goal.x() - x;
            float dy = goal.y() - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < 0.05f) continue;
            float step = Math.min(distance, RepairDrone.SPEED_CELLS * dt);
            float nx = x + dx / distance * step;
            float ny = y + dy / distance * step;
            float heading = (float) Math.toDegrees(Math.atan2(-dx, dy));
            AirBody body = world.kinematics(drone.id);
            if (body != null) body.teleport(nx, ny, heading);
            world.setPos(drone.id, nx, ny);
        }
    }

    /**
     * A flock's way over open ground from one cell to another, as the director reckons it: the straight way, and twice
     * every roofed cell it crosses again for the way round. A reckoning, not a route: the flock's own field flies it.
     */
    static float openWay(BattleSimulation sim, int fromX, int fromY, int toX, int toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (sim.getTopology() == null || length < 1f) return length;
        int roofed = 0;
        int steps = (int) Math.ceil(length);
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.floor(fromX + 0.5f + dx * i / steps);
            int y = (int) Math.floor(fromY + 0.5f + dy * i / steps);
            if (sim.getTopology().isRoofIntact(x, y)) roofed++;
        }
        return length + 2f * roofed;
    }

    /**
     * The facility's wake each pulse (`derelict-watch.md`, Waking): the stage follows the site's stir, or the contingency;
     * the ducts come up, the reserve bays open (each one's housing gives way to its flock's cradle) and the hunting
     * alarm sounds as their stages come.
     */
    private void tickWake(BattleSimulation sim) {
        RuinsPressure pressure = sim.ruinsPressure();
        FacilityWake.Stage seen = FacilityWake.of(pressure == null ? RuinsPressure.Level.QUIET : pressure.level(),
                network.contingency());
        wake.advance(seen);
        if (wake.stage().ventsOpen() && !ducts.opened()) ducts.open();
        List<Integer> open = wake.release(network, new ArrayList<>(reserveBays.keySet()),
                zone -> sim.resolveUnit(reserveBays.get(zone)) != 0L);
        for (int zoneId : open) {
            long bay = reserveBays.get(zoneId);
            float x = sim.world().x(bay);
            float y = sim.world().y(bay);
            sim.takeOffTheField(bay);
            int flock = reserveLauncher.launch(zoneId, x, y);
            if (flock >= 0) sweeping.add(flock);
        }
        wake.sound(network);
        if (wake.stage().bulkheadsOpen() && !bulkheadsOpened) {
            bulkheadsOpened = true;
            RuinsDoorStates doors = doorStates(sim);
            for (Bulkheads.Bulkhead bulkhead : bulkheads) {
                for (int[] c : bulkhead.cells()) sim.mapEditor().openPassage(c[0], c[1], bulkhead.likeX(), bulkhead.likeY());
                if (doors != null && doors.stanceOf(bulkhead.door()) == RuinsDelve.DoorStance.SEALED) {
                    doors.open(bulkhead.door());
                }
            }
        }
    }

    /**
     * A hall-bound frame, awake and with nothing to close on or fire at, goes to its dead door and presses against it;
     * once forced, the door's end cells open and the frame is bound no longer.
     */
    private Order ram(BattleSimulation sim, Walker walker, SecurityZone zone, Order order) {
        RuinsDelve.LeafDoor door = deadDoors.get(walker.squadId());
        if (door == null || !hallBound.contains(walker.squadId())) return order;
        boolean roused = network.contingency() || zone.state() == SecurityZone.State.AWAKE;
        if (!roused || (order.kind() != Kind.HOLD && order.kind() != Kind.HOLD_EGRESS)) return order;
        RuinsDelve.Area span = door.span();
        long lead = sim.squadMemberAt(walker.squadId(), 0);
        boolean against = Math.max(Math.abs(sim.world().x(lead) - (span.centreX() + 0.5f)),
                Math.abs(sim.world().y(lead) - (span.centreY() + 0.5f))) <= RAM_CELLS;
        if (rams.get(walker.squadId()).tick(PULSE_TICKS * BattleSimulation.TICK_DT, against)) {
            int likeX = span.centreX(), likeY = span.centreY();
            for (int[] c : DerelictWalkerClass.tornCells(door)) sim.mapEditor().openPassage(c[0], c[1], likeX, likeY);
            forced.add(door);
            RuinsDoorStates doors = doorStates(sim);
            if (doors != null && doors.stanceOf(door) != null) {
                RuinsDelve.Area widened = DerelictWalkerClass.tornSpan(door);
                if (widened != null) doors.tear(door, widened);
                else doors.force(door);
            }
            hallBound.remove(walker.squadId());
            return order;
        }
        int[] stand = ramStands.get(walker.squadId());
        return new Order(Kind.FORCE, stand[0], stand[1], -1L);
    }

    /**
     * Wrecks each pulse (`derelict-watch.md`, Repair): a walker lance or post of the network that has fallen leaves its
     * wreck where it last stood (the vault frame, a guardian, leaves none); company infantry standing on a wreck
     * uncontested scraps it; a company charge going off scraps the wrecks and collapses the vents near it.
     */
    private void tickWrecks(BattleSimulation sim) {
        World world = sim.world();
        float dt = PULSE_TICKS * BattleSimulation.TICK_DT;
        for (Walker walker : walkers) {
            if (sim.squadMemberCount(walker.squadId()) > 0) {
                long lead = sim.squadMemberAt(walker.squadId(), 0);
                lastStood.put(walker.squadId(), new float[] {world.x(lead), world.y(lead)});
                continue;
            }
            float[] at = lastStood.remove(walker.squadId());
            if (at == null || walker.frame() == DerelictWalkerFrame.VAULT) continue;
            wrecks.add(new Wreck(nextWreck++, Wreck.Kind.WALKER, walker.zoneId(), at[0], at[1], walker.frame(),
                    walker.squadId(), raisedLances.contains(walker.squadId())));
        }
        for (Map.Entry<Long, Integer> post : new ArrayList<>(posts.entrySet())) {
            long id = post.getKey();
            if (sim.resolveUnit(id) != 0L) {
                postCells.putIfAbsent(id, new int[] {world.cellX(id), world.cellY(id)});
                continue;
            }
            int[] cell = postCells.remove(id);
            if (cell == null) continue;
            posts.remove(id);
            wrecks.add(new Wreck(nextWreck++, Wreck.Kind.POST, post.getValue(), cell[0] + 0.5f, cell[1] + 0.5f, null,
                    -1, raisedPosts.contains(id)));
        }
        Map<Long, float[]> armed = new HashMap<>();
        for (ChargeView charge : sim.satchelCharges().activeCharges()) {
            if (charge.sourceFaction() == Faction.MARINE) armed.put(charge.id(), new float[] {charge.x(), charge.y()});
        }
        for (Map.Entry<Long, float[]> was : armedCharges.entrySet()) {
            if (armed.containsKey(was.getKey())) continue;
            float[] at = was.getValue();
            for (Wreck wreck : wrecks) wreck.blast(at[0], at[1]);
            ducts.charge(at[0], at[1]);
        }
        armedCharges = armed;
        for (Wreck wreck : wrecks) {
            if (!wreck.raisable()) continue;
            boolean companyOn = false;
            boolean contested = false;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                UnitType type = sim.identity().type(unit);
                if (!type.combatant) continue;
                float dx = world.x(unit) - wreck.x;
                float dy = world.y(unit) - wreck.y;
                float d2 = dx * dx + dy * dy;
                Faction side = sim.identity().faction(unit);
                if (side == Faction.MARINE && !type.isMech() && d2 <= Wreck.REACH_CELLS * Wreck.REACH_CELLS) {
                    companyOn = true;
                } else if (side == Faction.DEFENDER && d2 <= CONTEST_CELLS * CONTEST_CELLS) {
                    contested = true;
                }
            }
            wreck.scrap(dt, companyOn, contested);
        }
    }

    private Wreck wreck(long id) {
        for (Wreck wreck : wrecks) if (wreck.id == id) return wreck;
        return null;
    }

    /**
     * A raised wreck stands again: a walker in its own lance at the wreck, a post on its cell, each at
     * {@link Wreck#RAISE_SHARE} of its plate and structure, with fresh knees on a walker. Its next fall leaves scrap.
     */
    private void raise(BattleSimulation sim, Wreck wreck) {
        World world = sim.world();
        long id;
        if (wreck.kind == Wreck.Kind.WALKER) {
            id = DerelictSecurity.spawnInto(sim, wreck.frame, (int) Math.floor(wreck.x), (int) Math.floor(wreck.y),
                    wreck.squadId);
            if (id == 0L) return;
            raisedLances.add(wreck.squadId);
            bodies.put(id, new Body(null));
            durability.remove(wreck.squadId);
            issued.remove(wreck.squadId);
            walking.remove(wreck.squadId);
        } else {
            id = postStander.stand(wreck.zoneId, (int) Math.floor(wreck.x), (int) Math.floor(wreck.y));
            if (id == 0L) return;
            raisedPosts.add(id);
        }
        world.setHp(id, world.maxHp(id) * Wreck.RAISE_SHARE);
        if (world.hasArmor(id)) world.setArmor(id, world.maxArmor(id) * Wreck.RAISE_SHARE);
    }

    /**
     * An MG-12 tender's pulse (`derelict-watch.md`, Field repair): its rules pick the walker it welds, or the bay it
     * reloads at; within reach it lays the pulse's plate, under fire or not, and otherwise it walks there.
     */
    private void tend(BattleSimulation sim, Walker walker, SecurityZone zone, int tick) {
        FieldTender rules = fieldTenders.computeIfAbsent(walker.squadId(), k -> new FieldTender());
        World world = sim.world();
        long self = sim.squadMemberAt(walker.squadId(), 0);
        List<RepairDrone.Charge> charges = new ArrayList<>();
        for (Walker other : walkers) {
            if (other.zoneId() != walker.zoneId() || other.squadId() == walker.squadId()) continue;
            for (int i = 0, n = sim.squadMemberCount(other.squadId()); i < n; i++) {
                long id = sim.squadMemberAt(other.squadId(), i);
                float max = world.maxArmor(id);
                if (max > 0f) charges.add(new RepairDrone.Charge(id, world.x(id), world.y(id), world.armor(id) / max));
            }
        }
        Bay bay = serviceBays.get(walker.zoneId());
        RepairDrone.Bay at = bay == null || sim.resolveUnit(bay.id) == 0L ? null
                : new RepairDrone.Bay(world.x(bay.id), world.y(bay.id), bay.rules.stock());
        boolean awake = network.contingency() || (zone != null && zone.state() == SecurityZone.State.AWAKE);
        FieldTender.Goal goal = rules.decide(world.x(self), world.y(self), walker.postX() + 0.5f, walker.postY() + 0.5f,
                awake, charges, at);
        if (goal.kind() == FieldTender.Kind.WELD && sim.resolveUnit(goal.entityId()) != 0L) {
            float points = rules.draw(PULSE_TICKS * BattleSimulation.TICK_DT);
            rules.refund(points - mend(world, goal.entityId(), points));
        } else if (goal.kind() == FieldTender.Kind.RELOAD) {
            rules.reload(bay.rules);
        }
        Order order;
        if (goal.kind() == FieldTender.Kind.STAND_BY) order = Order.hold(walker.postX(), walker.postY());
        else if (goal.entityId() == 0L && at != null && goal.x() == at.x() && goal.y() == at.y()) {
            order = bayStand(at.x(), at.y(), world.x(self), world.y(self));
        } else order = Order.hold((int) Math.floor(goal.x()), (int) Math.floor(goal.y()));
        String why = switch (goal.kind()) {
            case STAND_BY -> "tender stands by its post";
            case FOLLOW -> "tender walks to the walker it keeps";
            case WELD -> "tender welds plate onto its charge";
            case RELOAD -> "tender takes on plate at its bay";
        };
        apply(sim, walker, order, tick, why);
    }

    /**
     * A walker's re-arming this pulse (`derelict-watch.md`, Re-arming): with every rack spent and nothing in hand it
     * walks to its zone's standing, online bay; there its racks fill a pack at a time. Returns the order that takes it
     * there, or null when it is not re-arming.
     */
    private Order rearm(BattleSimulation sim, Walker walker, SecurityZone zone, Order order) {
        // The vault frame keeps the vault, and a frame shut in its hall cannot reach the bay: both fight with what they have.
        if (walker.frame() == DerelictWalkerFrame.VAULT || hallBound.contains(walker.squadId())) return null;
        long lead = sim.squadMemberAt(walker.squadId(), 0);
        World world = sim.world();
        if (!world.hasMechLoadout(lead)) return null;
        List<MechWeaponMount> racks = racks(world.mechLoadout(lead));
        if (racks.isEmpty()) return null;
        boolean spent = true;
        boolean full = true;
        for (MechWeaponMount rack : racks) {
            if (rack.hasAmmo()) spent = false;
            if (!rack.full()) full = false;
        }
        Bay bay = serviceBays.get(walker.zoneId());
        boolean usable = bay != null && sim.resolveUnit(bay.id) != 0L && zone != null && zone.online();
        boolean engaged = order.kind() == Kind.FIRE || order.kind() == Kind.CLOSE || order.kind() == Kind.FORCE;
        float bayX = usable ? world.x(bay.id) : 0f;
        float bayY = usable ? world.y(bay.id) : 0f;
        BayRearm rules = rearming.computeIfAbsent(walker.squadId(), k -> new BayRearm());
        BayRearm.Step step = rules.decide(spent, full, engaged, usable, network.contingency(),
                usable && BayRearm.atBay(world.x(lead), world.y(lead), bayX, bayY));
        if (step == BayRearm.Step.NONE) return null;
        if (step == BayRearm.Step.LOAD) {
            int packs = rules.packs(PULSE_TICKS * BattleSimulation.TICK_DT);
            for (MechWeaponMount rack : racks) for (int k = 0; k < packs; k++) rack.resupplyOne();
        }
        return bayStand(bayX, bayY, world.x(lead), world.y(lead));
    }

    /**
     * Where a walker stands to take on stock or packs at a bay: {@link #BAY_STAND_CELLS} off it toward the walker, since
     * the bay's own cell is the housing's.
     */
    static Order bayStand(float bayX, float bayY, float fromX, float fromY) {
        float dx = fromX - bayX;
        float dy = fromY - bayY;
        float d = (float) Math.max(1e-3, Math.hypot(dx, dy));
        float k = Math.min(d, BAY_STAND_CELLS) / d;
        return Order.hold((int) Math.floor(bayX + dx * k), (int) Math.floor(bayY + dy * k));
    }

    /** A walker's missile racks: the mounts a loader would refill. */
    static List<MechWeaponMount> racks(MechLoadoutComponent loadout) {
        List<MechWeaponMount> out = new ArrayList<>();
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null || mount.capacity() < 0) continue;
            if (Float.isFinite(MissileReplenisherComponent.STANDARD.replenishmentSeconds(mount.weaponId()))) {
                out.add(mount);
            }
        }
        return out;
    }

    /** A lance's re-arming at its bay, or null before its first pulse. */
    public BayRearm rearmOf(int squadId) {
        return rearming.get(squadId);
    }

    /** An MG-12 tender's rules, or null for a lance that is none. */
    public FieldTender fieldTender(int squadId) {
        return fieldTenders.get(squadId);
    }

    /** Lays up to {@code points} on a charge: plate then structure, or plate only on a walker. Returns what was laid. */
    static float mend(World world, long id, float points) {
        if (world.hasMechLoadout(id)) {
            MechSections sections = world.mechLoadout(id).sectionsFor(world.hp(id), world.maxHp(id),
                    world.armor(id), world.maxArmor(id));
            float laid = sections.mendArmor(points);
            world.setArmor(id, sections.totalArmor());
            return laid;
        }
        float plate = Math.min(points, Math.max(0f, world.maxArmor(id) - world.armor(id)));
        if (plate > 0f) world.setArmor(id, world.armor(id) + plate);
        float frame = Math.min(points - plate, Math.max(0f, world.maxHp(id) - world.hp(id)));
        if (frame > 0f) world.setHp(id, world.hp(id) + frame);
        return plate + frame;
    }

    private void apply(BattleSimulation sim, Walker walker, Order order, int tick) {
        apply(sim, walker, order, tick, reason(walker, order));
    }

    private void apply(BattleSimulation sim, Walker walker, Order order, int tick, String why) {
        if (order.kind() == Kind.FIRE) {
            Squad squad = sim.getSquad(walker.squadId());
            if (squad != null) {
                squad.observeSpottedContact(order.unitId(), order.cellX(), order.cellY(), tick, SPOT_CONFIDENCE);
            }
        }
        Order last = issued.put(walker.squadId(), order);
        Order stand = standing(walker, order);
        boolean walk = false;
        if (sim.squadMemberCount(walker.squadId()) > 0) {
            long lead = sim.squadMemberAt(walker.squadId(), 0);
            walk = walksTo(sim.world().x(lead), sim.world().y(lead), stand);
        }
        Boolean wasWalking = walking.put(walker.squadId(), walk);
        if (last != null && wasWalking != null && wasWalking == walk) {
            Order was = standing(walker, last);
            if (was.kind() == stand.kind()
                    && WalkerPosture.chebyshev(was.cellX(), was.cellY(), stand.cellX(), stand.cellY()) < REISSUE_CELLS) {
                return;
            }
        }
        // A walker's own doctrine holds a defended area wherever it stands, so one that is off the cell it is to
        // hold walks there first, fighting on the way, and holds it once there.
        ObjectiveAssignment assignment;
        if (stand.kind() == Kind.CLOSE || walk) {
            assignment = walk(walker.squadId(), walker.frame(), stand.cellX(), stand.cellY());
        } else {
            assignment = ObjectiveAssignment.defendArea(walker.squadId(), stand.cellX(), stand.cellY(),
                    radius(stand));
        }
        sim.assignSquadCommand(assignment, CommandAuthority.MISSION_COMMAND, ISSUER, why);
    }

    /**
     * The order that walks a lance to a cell: an attack move, fighting on the way; for an unarmed frame (the tender, a
     * fallen hull's stripper and hauler) a withdrawal to it, since a lance with no gun declines every attack goal and
     * breaks contact instead (`MechDamageDoctrine.lanceDeclinesAttack`), walking away from where it was sent.
     */
    static ObjectiveAssignment walk(int squadId, DerelictWalkerFrame frame, int cellX, int cellY) {
        return frame.armed() ? ObjectiveAssignment.attackMove(squadId, cellX, cellY)
                : ObjectiveAssignment.withdraw(squadId, cellX, cellY);
    }

    /** How near the cell a holding order counts as there. */
    static int radius(Order stand) {
        return switch (stand.kind()) {
            case HOLD_EGRESS -> EGRESS_RADIUS;
            case ROUND -> ROUND_RADIUS;
            case FORCE -> FORCE_RADIUS;
            default -> HOLD_RADIUS;
        };
    }

    /** Whether a walker at {@code (x, y)} must walk to the cell its holding order names before it holds it. */
    static boolean walksTo(float x, float y, Order stand) {
        if (stand.kind() == Kind.CLOSE) return false;
        return Math.max(Math.abs(x - (stand.cellX() + 0.5f)), Math.abs(y - (stand.cellY() + 0.5f))) > radius(stand);
    }

    /** Where the order stands the lance: a fire order stands at the post as a hold does; only its target differs. */
    private static Order standing(Walker walker, Order order) {
        return order.kind() == Kind.FIRE ? new Order(Kind.HOLD, walker.postX(), walker.postY(), -1L) : order;
    }

    private static String reason(Walker walker, Order order) {
        return switch (order.kind()) {
            case HOLD -> walker.frame() + " holds its post";
            case CLOSE -> walker.frame() + " comes to a pinned contact";
            case FIRE -> walker.frame() + " fires on what the watch sees";
            case HOLD_EGRESS -> walker.frame() + " holds the egress in the contingency";
            case ROUND -> walker.frame() + " walks its zone's round";
            case FORCE -> walker.frame() + " forces the dead door shutting it in its hall";
        };
    }

    /** What the network's own units hold: the side's merged contacts, fresh, units only. */
    private static List<Contact> ownContacts(BattleSimulation sim, int tick) {
        List<Contact> out = new ArrayList<>();
        for (CommanderContact contact : sim.getCommanderInfluence(Faction.DEFENDER).contacts()) {
            if (contact.structure() || tick - contact.observedTick() > FRESH_TICKS) continue;
            out.add(new Contact(contact.unitId(), contact.cellX(), contact.cellY(), contact.observedTick()));
        }
        return out;
    }

    /** One contact per unit, the most recent sighting kept, in unit id order. */
    static List<Contact> merge(List<Contact> a, List<Contact> b) {
        Map<Long, Contact> byUnit = new TreeMap<>();
        for (List<Contact> list : List.of(a, b)) {
            for (Contact contact : list) {
                Contact old = byUnit.get(contact.unitId());
                if (old == null || contact.observedTick() > old.observedTick()) byUnit.put(contact.unitId(), contact);
            }
        }
        return new ArrayList<>(byUnit.values());
    }

    private float durability(BattleSimulation sim, int squadId) {
        float total = 0f;
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long member = sim.squadMemberAt(squadId, i);
            total += sim.world().hp(member) + sim.world().armor(member);
            WalkerKnees knees = kneesOf(member);
            if (knees != null) for (int k = 0; k < WalkerKnees.LEGS; k++) total += knees.structure(k);
        }
        return total;
    }

    private static RuinsVaultObjective vault(BattleSimulation sim) {
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective ruins) return ruins;
        }
        return null;
    }
}
