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
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
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
    /** Confidence a relayed sighting is handed to a siege frame at. */
    static final float SPOT_CONFIDENCE = 0.9f;

    /** One walker lance the network commands: its frame, zone and post. */
    public record Walker(int squadId, DerelictWalkerFrame frame, int zoneId, int postX, int postY) { }

    private final SecurityNetwork network;
    private final List<Walker> walkers;
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
    private final List<Tender> tenders = new ArrayList<>();

    /** A zone's repair drone in the field: its entity, zone, home, rules and current goal. */
    private static final class Tender {
        final long id;
        final int zoneId;
        final float homeX;
        final float homeY;
        final RepairDrone rules = new RepairDrone();
        RepairDrone.Goal goal;
        float lastDurability = -1f;

        Tender(long id, int zoneId, float homeX, float homeY) {
            this.id = id;
            this.zoneId = zoneId;
            this.homeX = homeX;
            this.homeY = homeY;
            this.goal = new RepairDrone.Goal(RepairDrone.Kind.HOME, homeX, homeY, 0L);
        }
    }
    private final Map<Long, Float> postDurability = new TreeMap<>();
    private final Map<Long, Braced> braces = new TreeMap<>();

    /** A vault frame's brace in the field (`ruins-guardians.md`): its stance and what it last read. */
    private static final class Braced {
        final VaultFrameBrace brace = new VaultFrameBrace();
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        float[] cycles = new float[0];
    }

    /** How far a frame may shift in one tick and still count as standing, cells. */
    static final float STILL_CELLS_PER_TICK = 0.005f;

    /**
     * Gives each vault frame its brace, the vault guardian's stance (`ruins-guardians.md`), and has the damage
     * pipeline ask it how each hit on the frame lands.
     */
    public void braceVaultFrames(BattleSimulation sim) {
        for (Walker walker : walkers) {
            if (walker.frame() != DerelictWalkerFrame.VAULT) continue;
            for (int i = 0, n = sim.squadMemberCount(walker.squadId()); i < n; i++) {
                braces.put(sim.squadMemberAt(walker.squadId(), i), new Braced());
            }
        }
        if (!braces.isEmpty()) sim.getRoster().hitScale((target, attacker) -> braceHit(sim, target, attacker));
    }

    /** A vault frame's stance, or null for a unit that is none. */
    public VaultFrameBrace.Stance braceOf(long unitId) {
        Braced braced = braces.get(unitId);
        return braced == null ? null : braced.brace.stance();
    }

    private float braceHit(BattleSimulation sim, long target, long attacker) {
        Braced braced = braces.get(target);
        if (braced == null || attacker == target || sim.resolveUnit(attacker) == 0L) return 1f;
        World world = sim.world();
        if (!world.hasMechLoadout(target)) return 1f;
        return braced.brace.hit(VaultFrameBrace.bearing(world.mechLoadout(target).torsoFacingDegrees,
                world.x(attacker) - world.x(target), world.y(attacker) - world.y(target)));
    }

    /** Every tick: each vault frame braces or comes free, and its guns cycle at its stance's rate. */
    private void tickBraces(BattleSimulation sim) {
        World world = sim.world();
        for (Map.Entry<Long, Braced> entry : braces.entrySet()) {
            long id = entry.getKey();
            Braced braced = entry.getValue();
            if (sim.resolveUnit(id) == 0L || !world.hasMechLoadout(id)) continue;
            MechLoadoutComponent mech = world.mechLoadout(id);
            float x = world.x(id);
            float y = world.y(id);
            boolean moving = !Float.isNaN(braced.lastX)
                    && Math.hypot(x - braced.lastX, y - braced.lastY) > STILL_CELLS_PER_TICK;
            braced.lastX = x;
            braced.lastY = y;
            long target = sim.targetOf(id);
            boolean engaged = target != 0L && sim.resolveUnit(target) != 0L;
            float bearing = engaged ? VaultFrameBrace.bearing(mech.torsoFacingDegrees,
                    world.x(target) - x, world.y(target) - y) : 0f;
            braced.brace.tick(BattleSimulation.TICK_DT, moving, engaged, bearing);
            MechWeaponMount[] mounts = mech.mounts();
            if (braced.cycles.length != mounts.length) braced.cycles = new float[mounts.length];
            for (int i = 0; i < mounts.length; i++) {
                MechWeaponMount mount = mounts[i];
                if (mount == null) continue;
                mount.cycleRemaining = VaultFrameBrace.stretch(braced.cycles[i], mount.cycleRemaining,
                        braced.brace.fireScale());
                braced.cycles[i] = mount.cycleRemaining;
            }
        }
    }

    public DerelictSecurityDirector(SecurityNetwork network, List<Walker> walkers, List<int[]> egress,
                                    int vaultX, int vaultY) {
        this.network = network;
        this.walkers = List.copyOf(walkers);
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

    /** Stands a zone's node: the structure whose loss isolates the zone (slice 5). */
    public void standNode(int zoneId, long entityId) {
        nodes.put(zoneId, entityId);
    }

    /** Puts a zone's repair drone in the field (slice 6), homed at {@code (homeX, homeY)}. */
    public void standRepairDrone(int zoneId, long entityId, float homeX, float homeY) {
        tenders.add(new Tender(entityId, zoneId, homeX, homeY));
    }

    /** The repair drones' entities, in the order they were stood. */
    public List<Long> repairDrones() {
        List<Long> out = new ArrayList<>();
        for (Tender tender : tenders) out.add(tender.id);
        return out;
    }

    /** The stock a zone's repair drone has left, or -1 with none. */
    public float repairStock(int zoneId) {
        for (Tender tender : tenders) if (tender.zoneId == zoneId) return tender.rules.stock();
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
        return walkers;
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
        flyTenders(sim);
        tickBraces(sim);
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
        for (Map.Entry<Long, Integer> post : posts.entrySet()) {
            long id = post.getKey();
            if (sim.resolveUnit(id) == 0L) continue;
            if (DerelictPost.unfolded(network.zone(post.getValue()))) sim.turretState().restorePower(id);
            else sim.turretState().cutPower(id);
        }

        List<Long> haulIds = new ArrayList<>(haul);
        List<Contact> haulContacts = new ArrayList<>();
        for (Contact contact : all) if (haul.contains(contact.unitId())) haulContacts.add(contact);
        for (Walker walker : walkers) {
            if (sim.squadMemberCount(walker.squadId()) <= 0) continue;
            SecurityZone zone = network.zone(walker.zoneId());
            // A zone whose flock is gone is its walkers' own eyes: they come to and fire on what they hold.
            boolean blind = !flocked.contains(walker.zoneId());
            Order order = WalkerPosture.decide(walker.frame(), zone, network.contingency(),
                    walker.postX(), walker.postY(), blind ? merge(pins, own) : pins,
                    blind ? all : flockSeen, haulIds, egress, walker.frame().maxWeaponRange());
            apply(sim, walker, order, tick);
        }
        for (Tender tender : tenders) directTender(sim, tender, all, tick);
        for (Report report : reports) {
            SecurityZone zone = network.zone(report.homeZoneId());
            if (zone == null) continue;
            int[] stir = stirredAt.getOrDefault(zone.id, new int[] {zone.nodeCellX, zone.nodeCellY});
            FlockTasking.Order order = FlockTasking.decide(report, zone, network.contingency(), all, haulContacts,
                    stir[0], stir[1]);
            if (order != null) {
                flocks.order(report.flockId(), order.task(), order.anchorX(), order.anchorY(), order.targetUnitId());
            }
        }
    }

    /** A repair drone's goal this pulse, from what it may mend in its zone and whether it is under fire. */
    private void directTender(BattleSimulation sim, Tender tender, List<Contact> hostiles, int tick) {
        if (sim.resolveUnit(tender.id) == 0L) return;
        World world = sim.world();
        float x = world.x(tender.id);
        float y = world.y(tender.id);
        float durability = world.hp(tender.id) + (world.hasArmor(tender.id) ? world.armor(tender.id) : 0f);
        boolean hit = tender.lastDurability >= 0f && durability < tender.lastDurability - 0.01f;
        tender.lastDurability = durability;
        boolean threatened = false;
        for (Contact contact : hostiles) {
            if (Math.max(Math.abs(contact.cellX() + 0.5f - x), Math.abs(contact.cellY() + 0.5f - y))
                    <= RepairDrone.THREAT_CELLS) {
                threatened = true;
                break;
            }
        }
        SecurityZone zone = network.zone(tender.zoneId);
        tender.goal = tender.rules.decide(x, y, tender.homeX, tender.homeY, zone != null && zone.online(),
                hit || threatened, charges(sim, tender.zoneId), tick);
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
    private void flyTenders(BattleSimulation sim) {
        float dt = BattleSimulation.TICK_DT;
        World world = sim.world();
        for (Tender tender : tenders) {
            if (sim.resolveUnit(tender.id) == 0L) continue;
            RepairDrone.Goal goal = tender.goal;
            if (goal.kind() == RepairDrone.Kind.MEND) {
                if (sim.resolveUnit(goal.entityId()) == 0L) continue;
                float points = tender.rules.draw(dt);
                tender.rules.refund(points - mend(world, goal.entityId(), points));
                continue;
            }
            float x = world.x(tender.id);
            float y = world.y(tender.id);
            float dx = goal.x() - x;
            float dy = goal.y() - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < 0.05f) continue;
            float step = Math.min(distance, RepairDrone.SPEED_CELLS * dt);
            float nx = x + dx / distance * step;
            float ny = y + dy / distance * step;
            float heading = (float) Math.toDegrees(Math.atan2(-dx, dy));
            AirBody body = world.kinematics(tender.id);
            if (body != null) body.teleport(nx, ny, heading);
            world.setPos(tender.id, nx, ny);
        }
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
            assignment = ObjectiveAssignment.attackMove(walker.squadId(), stand.cellX(), stand.cellY());
        } else {
            assignment = ObjectiveAssignment.defendArea(walker.squadId(), stand.cellX(), stand.cellY(),
                    radius(stand));
        }
        sim.assignSquadCommand(assignment, CommandAuthority.MISSION_COMMAND, ISSUER, reason(walker, order));
    }

    /** How near the cell a holding order counts as there. */
    static int radius(Order stand) {
        return stand.kind() == Kind.HOLD_EGRESS ? EGRESS_RADIUS : HOLD_RADIUS;
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

    private static float durability(BattleSimulation sim, int squadId) {
        float total = 0f;
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long member = sim.squadMemberAt(squadId, i);
            total += sim.world().hp(member) + sim.world().armor(member);
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
