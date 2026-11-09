package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Report;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Kind;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Order;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
 * <p>With no flocks connected ({@link FlockCommandPort#NONE}), or none reporting, the network's own units stand in
 * for them: walkers come to and siege frames fire on what the zone's walkers see. That is the same knowledge the
 * flocks would relay, reached by fewer eyes.
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
    private final Map<Integer, Order> issued = new TreeMap<>();
    private final Map<Integer, Float> durability = new TreeMap<>();
    private final Map<Integer, int[]> stirredAt = new TreeMap<>();
    private final Set<Long> haul = new TreeSet<>();

    public DerelictSecurityDirector(SecurityNetwork network, List<Walker> walkers, List<int[]> egress,
                                    int vaultX, int vaultY) {
        this.network = network;
        this.walkers = List.copyOf(walkers);
        this.egress = List.copyOf(egress);
        this.vaultX = vaultX;
        this.vaultY = vaultY;
    }

    /** Connects the flock model (`derelict-drone-flocks.md`, Seam). */
    public void connectFlocks(FlockCommandPort port) {
        flocks = port == null ? FlockCommandPort.NONE : port;
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
        boolean flocksUp = !reports.isEmpty();
        List<Contact> spotted = flocksUp ? flockSeen : own;
        if (!flocksUp) pins = own;

        for (Contact contact : all) {
            SecurityZone zone = network.zoneAt(contact.cellX(), contact.cellY());
            if (zone == null) continue;
            network.stir(zone.id, tick);
            stirredAt.put(zone.id, new int[] {contact.cellX(), contact.cellY()});
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
        network.settle(tick);

        List<Long> haulIds = new ArrayList<>(haul);
        List<Contact> haulContacts = new ArrayList<>();
        for (Contact contact : all) if (haul.contains(contact.unitId())) haulContacts.add(contact);
        for (Walker walker : walkers) {
            if (sim.squadMemberCount(walker.squadId()) <= 0) continue;
            SecurityZone zone = network.zone(walker.zoneId());
            Order order = WalkerPosture.decide(walker.frame(), zone, network.contingency(),
                    walker.postX(), walker.postY(), pins, spotted, haulIds, egress, walker.frame().maxWeaponRange());
            apply(sim, walker, order, tick);
        }
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

    private void apply(BattleSimulation sim, Walker walker, Order order, int tick) {
        if (order.kind() == Kind.FIRE) {
            Squad squad = sim.getSquad(walker.squadId());
            if (squad != null) {
                squad.observeSpottedContact(order.unitId(), order.cellX(), order.cellY(), tick, SPOT_CONFIDENCE);
            }
        }
        Order last = issued.put(walker.squadId(), order);
        Order stand = standing(walker, order);
        if (last != null) {
            Order was = standing(walker, last);
            if (was.kind() == stand.kind()
                    && WalkerPosture.chebyshev(was.cellX(), was.cellY(), stand.cellX(), stand.cellY()) < REISSUE_CELLS) {
                return;
            }
        }
        ObjectiveAssignment assignment = switch (stand.kind()) {
            case CLOSE -> ObjectiveAssignment.attackMove(walker.squadId(), stand.cellX(), stand.cellY());
            case HOLD_EGRESS -> ObjectiveAssignment.defendArea(walker.squadId(), stand.cellX(), stand.cellY(),
                    EGRESS_RADIUS);
            default -> ObjectiveAssignment.defendArea(walker.squadId(), stand.cellX(), stand.cellY(), HOLD_RADIUS);
        };
        sim.assignSquadCommand(assignment, CommandAuthority.MISSION_COMMAND, ISSUER, reason(walker, order));
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
