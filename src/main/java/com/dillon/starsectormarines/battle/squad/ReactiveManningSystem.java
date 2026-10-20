package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ReactiveWatch;
import com.dillon.starsectormarines.battle.command.ReactiveWatch.Candidate;
import com.dillon.starsectormarines.battle.command.ReactiveWatch.Keep;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Watch;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveHoldPlaceOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Acts on the player's GARRISON stance ({@link ReactiveManningService}): each squad in it, while it carries no player
 * order, watches the fighting-position runs and held buildings within {@link ReactiveWatch#WATCH_RADIUS_CELLS} of its
 * anchor and mans the one its side believes most pressed, then gives it back once the pressure has passed and walks
 * home. The decisions are {@link ReactiveWatch}'s; this class prices the places and gives the orders, through the
 * same occupy, hold and move paths a click takes ({@link SquadMoveOrderSystem}), so the stance never gives an order
 * the player could not.
 *
 * <p><b>Explicit orders win.</b> Each tick the squad's standing order is compared with the one the stance gave. Any
 * other order is the player's: the stance stands aside until it ends and then watches again from where the squad
 * stopped. Nothing here ever ends an order the stance did not give.
 *
 * <p><b>Places.</b> Both kinds come from the battle's garrisonables: a stretch of a run, manned with the occupy order a
 * click on it gives, and a building's envelope (a held place's, or a captured compound building's), held with the hold
 * order a click on the building gives, so the squad stands to at its windows and doors by the frontage laws. A
 * compound the marines have not taken offers neither.
 *
 * <p><b>Cost.</b> The reconcile is a map read per stance squad per tick. The decision runs once per
 * {@link ReactiveWatch#PULSE_TICKS} per squad, staggered by squad id, and reads only what is near the anchor: the
 * registry's block lookup at the anchor's block; each place is priced by one influence read per block it covers.
 */
public final class ReactiveManningSystem {

    /** A squad this close to its anchor is home, and needs no walk back. */
    static final int HOME_CELLS = 2;
    private static final long BUILDING_KEY = 1L << 62;

    private final ReactiveManningService service;
    private final SquadMoveOrderSystem orders;
    private final SquadMoveOrderService mailbox;

    public ReactiveManningSystem(ReactiveManningService service, SquadMoveOrderSystem orders,
                                 SquadMoveOrderService mailbox) {
        this.service = service;
        this.orders = orders;
        this.mailbox = mailbox;
    }

    /** Runs after the player's orders this tick are resolved, so a click this tick is already the standing order. */
    public void tick(BattleSimulation sim) {
        if (service.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        Pulse pulse = null;
        for (Map.Entry<Integer, Watch> entry : service.entries()) {
            int id = entry.getKey();
            Watch watch = entry.getValue();
            Squad squad = sim.getSquad(id);
            if (watch.switchedOff || squad == null || squad.aliveMembers <= 0 || squad.faction != Faction.MARINE
                    || squad.isMechSquad() || squad.isDroneSquad()) {
                if (squad != null) orders.releaseFor(squad, watch.issued, sim, "garrison stance off");
                service.remove(id);
                continue;
            }
            reconcile(watch, squad, mailbox.activeOrder(id));
            if (Math.floorMod(tick - id, ReactiveWatch.PULSE_TICKS) == 0 && watch.status != Status.UNDER_ORDERS) {
                if (pulse == null) pulse = new Pulse(sim);
                decide(watch, squad, pulse, sim, tick);
            }
            service.publish(id, watch);
        }
    }

    /** Follows what the squad actually carries: its own order, the player's, or none. */
    private static void reconcile(Watch watch, Squad squad, ActiveOrder active) {
        if (watch.issued != null) {
            if (active == watch.issued) return;
            watch.issued = null;
            watch.clearPlace();
            if (active != null) {
                watch.status = Status.UNDER_ORDERS;
            } else {
                // The walk home arrived, or the order system ended the stance's order itself.
                watch.status = Status.WATCHING;
                if (!watch.anchored()) anchorHere(watch, squad);
            }
            return;
        }
        if (active != null) {
            watch.status = Status.UNDER_ORDERS;
            watch.clearPlace();
        } else if (watch.status == Status.UNDER_ORDERS || !watch.anchored()) {
            // A player order has ended (or the stance is new): watch from where the squad stopped.
            anchorHere(watch, squad);
            watch.status = Status.WATCHING;
            watch.clearPlace();
        }
    }

    private static void anchorHere(Watch watch, Squad squad) {
        watch.anchorX = (int) Math.floor(squad.centroidX);
        watch.anchorY = (int) Math.floor(squad.centroidY);
    }

    private void decide(Watch watch, Squad squad, Pulse pulse, BattleSimulation sim, int tick) {
        watch.lockedUntil.values().removeIf(until -> until <= tick);
        int sx = (int) Math.floor(squad.centroidX);
        int sy = (int) Math.floor(squad.centroidY);
        if (watch.status == Status.MANNING_TRENCH || watch.status == Status.HOLDING_BUILDING) {
            hold(watch, squad, pulse, sim, tick, sx, sy);
            return;
        }
        if (watch.status == Status.WATCHING && ReactiveWatch.drifted(watch.anchorX, watch.anchorY, sx, sy)) {
            anchorHere(watch, squad);
        }
        if (pulse.influence == null) return;
        List<Place> places = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        gather(watch, squad, pulse, sim, sx, sy, places, candidates);
        int pick = ReactiveWatch.choose(candidates, squad.aliveMembers, squad.originalSize);
        if (pick < 0) return;
        Place place = places.get(pick);
        ActiveOrder order = place.node != null
                ? orders.holdFor(squad, place.node, sim)
                : orders.occupyFor(squad, pulse.registry.runOf(place.run), place.x, place.y, sim);
        if (order == null) return;
        watch.issued = order;
        watch.status = place.node != null ? Status.HOLDING_BUILDING : Status.MANNING_TRENCH;
        watch.placeX = place.x;
        watch.placeY = place.y;
        watch.node = place.node;
        watch.coolPulses = 0;
        pulse.taken(place);
    }

    /** A manned place: stay, give it back after the dwell, or fall back out of a breach. */
    private void hold(Watch watch, Squad squad, Pulse pulse, BattleSimulation sim, int tick, int sx, int sy) {
        Place place = watch.node != null ? pulse.building(watch.node) : pulse.runAt(watch.placeX, watch.placeY);
        if (place == null) {
            goHome(watch, squad, sim, "garrison: place gone");
            return;
        }
        float pressure = pulse.pressure(place);
        watch.coolPulses = ReactiveWatch.cooled(watch.coolPulses, pressure);
        boolean arrived = place.node != null
                ? ReactiveWatch.gap(sx, sy, place.left, place.top, place.right, place.bottom) == 0
                : Math.max(Math.abs(sx - place.x), Math.abs(sy - place.y)) <= place.radius;
        boolean breached = false;
        for (CommanderContact contact : pulse.contacts) {
            if (pulse.insideRun(place, contact.cellX(), contact.cellY())
                    && ReactiveWatch.breached(arrived, sx, sy, contact.cellX(), contact.cellY())) {
                breached = true;
                break;
            }
        }
        Keep keep = ReactiveWatch.keep(watch.coolPulses, WorldStateBuilder.hasActionableContact(squad, sim),
                squad.aliveMembers, squad.originalSize, breached);
        if (keep == Keep.STAY) return;
        if (keep == Keep.FALL_BACK) {
            watch.lockedUntil.put(place.key, tick + ReactiveWatch.LOCKOUT_PULSES * ReactiveWatch.PULSE_TICKS);
        }
        goHome(watch, squad, sim, keep == Keep.FALL_BACK ? "garrison: fall back" : "garrison: threat passed");
    }

    /** Walks the squad back to its anchor, or, already there, simply gives the place back. */
    private void goHome(Watch watch, Squad squad, BattleSimulation sim, String reason) {
        ActiveOrder previous = watch.issued;
        watch.clearPlace();
        int sx = (int) Math.floor(squad.centroidX);
        int sy = (int) Math.floor(squad.centroidY);
        ActiveOrder walk = Math.max(Math.abs(sx - watch.anchorX), Math.abs(sy - watch.anchorY)) <= HOME_CELLS
                ? null : orders.returnFor(squad, watch.anchorX, watch.anchorY, sim);
        if (walk == null) {
            orders.releaseFor(squad, previous, sim, reason);
            watch.issued = null;
            watch.status = Status.WATCHING;
            return;
        }
        watch.issued = walk;
        watch.status = Status.RETURNING;
    }

    /** Prices every place within the watch, nearest-first order not required: {@link ReactiveWatch#choose} ranks. */
    private void gather(Watch watch, Squad squad, Pulse pulse, BattleSimulation sim, int sx, int sy,
                        List<Place> places, List<Candidate> candidates) {
        int ax = watch.anchorX;
        int ay = watch.anchorY;
        GarrisonableRegistry registry = pulse.places;
        if (registry == null) return;
        int size = registry.blockSize();
        int bx = Math.min(registry.blockWidth() - 1, Math.max(0, ax / size));
        int by = Math.min(registry.blockHeight() - 1, Math.max(0, ay / size));
        for (int g : registry.near(bx, by)) {
            if (registry.isEnvelope(g)) {
                TacticalNode node = registry.building(g);
                int gap = ReactiveWatch.gap(ax, ay, node.left, node.top, node.right, node.bottom);
                if (gap > ReactiveWatch.WATCH_RADIUS_CELLS) continue;
                if (SquadMoveOrderSystem.insideUncapturedCompound(node.anchorX, node.anchorY, sim)) continue;
                add(watch, squad, pulse, pulse.building(node), gap, sx, sy, places, candidates);
                continue;
            }
            if (pulse.registry == null) continue;
            Place place = pulse.run(g);
            int gap = pulse.runGap(g, ax, ay);
            if (gap > ReactiveWatch.WATCH_RADIUS_CELLS) continue;
            if (SquadMoveOrderSystem.insideUncapturedCompound(place.x, place.y, sim)) continue;
            add(watch, squad, pulse, place, gap, sx, sy, places, candidates);
        }
    }

    private static void add(Watch watch, Squad squad, Pulse pulse, Place place, int gap, int sx, int sy,
                            List<Place> places, List<Candidate> candidates) {
        boolean enemyInside = false;
        for (CommanderContact contact : pulse.contacts) {
            if (pulse.inside(place, contact.cellX(), contact.cellY())) {
                enemyInside = true;
                break;
            }
        }
        places.add(place);
        candidates.add(new Candidate(place.key, gap, ReactiveWatch.etaTicks(sx, sy, place.x, place.y),
                pulse.pressure(place), pulse.isTaken(place, squad.id), enemyInside,
                watch.lockedUntil.containsKey(place.key)));
    }

    private static long pack(int x, int y) {
        return ((long) y << 32) | (x & 0xffffffffL);
    }

    /** A place priced this pulse: a stretch of a run (with {@code run} its current garrisonable id) or a building ({@code node}). */
    private static final class Place {
        final long key;
        final int run;
        final TacticalNode node;
        final int x, y, radius;
        final int left, top, right, bottom;

        Place(long key, int run, TacticalNode node, int x, int y, int radius,
              int left, int top, int right, int bottom) {
            this.key = key;
            this.run = run;
            this.node = node;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** What one tick's decisions share: the side's picture, its believed contacts, and who already holds what. */
    private final class Pulse {
        final CommanderInfluenceSnapshot influence;
        final List<CommanderContact> contacts = new ArrayList<>();
        /** The battle's garrisonables, when laid on the influence picture's lattice; the buildings are read from it. */
        final GarrisonableRegistry places;
        /** The same registry where its runs may be manned (fighting positions on), or {@code null}. */
        final GarrisonableRegistry registry;
        /** Squads holding each stretch (by current garrisonable id) or building, from the standing orders of the side. */
        final Map<Integer, Set<Integer>> runHolders = new HashMap<>();
        final Map<TacticalNode, Set<Integer>> buildingHolders = new IdentityHashMap<>();
        /** Stretches a believed contact is within reach of, or {@code null} when places are whole runs. */
        final Set<Integer> woken;

        Pulse(BattleSimulation sim) {
            influence = sim.getCommanderInfluence(Faction.MARINE);
            if (influence != null) {
                for (CommanderContact contact : influence.contacts()) {
                    if (contact.confidence() * contact.strength() >= ReactiveWatch.MIN_CONTACT_WEIGHT) {
                        contacts.add(contact);
                    }
                }
            }
            GarrisonableRegistry all = sim.garrisonables();
            places = all != null && all.count() > 0 && influence != null
                    && all.blockSize() == influence.blockSize()
                    && all.blockWidth() == influence.width() && all.blockHeight() == influence.height()
                    ? all : null;
            registry = places != null && DigInSystem.isEnabled() && places.stretchCount() > 0 ? places : null;
            // Over stretches a place is priced only where a believed contact is within the registry's reach of it,
            // the commander's own rule: the field's spread alone does not press a stretch nobody is near.
            if (registry != null && registry.stretched()) {
                woken = new HashSet<>();
                int size = registry.blockSize();
                for (CommanderContact contact : contacts) {
                    int bx = Math.min(registry.blockWidth() - 1, Math.max(0, contact.cellX() / size));
                    int by = Math.min(registry.blockHeight() - 1, Math.max(0, contact.cellY() / size));
                    for (int g : registry.near(bx, by)) woken.add(g);
                }
            } else {
                woken = null;
            }
            for (Map.Entry<Integer, ActiveOrder> entry : mailbox.activeEntries()) {
                ActiveOrder order = entry.getValue();
                if (order instanceof ActiveDefendAreaOrder defend && defend.isOccupy()) {
                    // An occupy holds the stretch it was clicked on (the stance clicks a stretch's centre).
                    int stretch = registry == null ? -1 : registry.stretchAt(defend.requestedX(), defend.requestedY());
                    if (stretch < 0 && registry != null) stretch = registry.stretchAt(defend.destinationX(), defend.destinationY());
                    if (stretch >= 0) runHolders.computeIfAbsent(stretch, k -> new HashSet<>()).add(entry.getKey());
                } else if (order instanceof ActiveHoldPlaceOrder hold) {
                    buildingHolders.computeIfAbsent(hold.targetNode(), k -> new HashSet<>()).add(entry.getKey());
                }
            }
        }

        Place run(int run) {
            int cx = registry.centreX(run);
            int cy = registry.centreY(run);
            return new Place(pack(cx, cy), run, null, cx, cy, registry.radius(run), cx, cy, cx, cy);
        }

        /** The run whose centre was manned, found again on the current index (shelling renumbers runs). */
        Place runAt(int cx, int cy) {
            if (registry == null) return null;
            int run = registry.stretchAt(cx, cy);
            return run < 0 ? null : run(run);
        }

        Place building(TacticalNode node) {
            return new Place(BUILDING_KEY | pack(node.anchorX, node.anchorY), -1, node, node.anchorX, node.anchorY,
                    0, node.left, node.top, node.right, node.bottom);
        }

        /** Chebyshev cells from a cell to the stretch's nearest cell. */
        int runGap(int run, int x, int y) {
            int best = Integer.MAX_VALUE;
            for (int i = 0, n = registry.size(run); i < n && best > 0; i++) {
                best = Math.min(best, Math.max(Math.abs(registry.cellX(run, i) - x),
                        Math.abs(registry.cellY(run, i) - y)));
            }
            return best;
        }

        /** Whether a cell lies inside the place: the building's footprint, or the stretch itself. */
        boolean inside(Place place, int x, int y) {
            if (place.node != null) return ReactiveWatch.gap(x, y, place.left, place.top, place.right, place.bottom) == 0;
            return registry != null && registry.idAt(x, y) == place.run;
        }

        /** As {@link #inside}, but anywhere on a stretch's whole run: what a breach is read against. */
        boolean insideRun(Place place, int x, int y) {
            if (place.node != null || registry == null) return inside(place, x, y);
            int at = registry.idAt(x, y);
            return at >= 0 && registry.runOf(at) == registry.runOf(place.run);
        }

        /** The place's believed pressure: the highest hostile reading among the influence blocks it covers. */
        float pressure(Place place) {
            if (influence == null) return 0f;
            float best = 0f;
            if (place.node == null) {
                if (woken != null && !woken.contains(place.run)) return 0f;
                int bw = registry.blockWidth();
                for (int block : registry.coveredBlocks(place.run)) {
                    best = Math.max(best, influence.hostileAt(block % bw, block / bw));
                }
                // The commander's own reading: a woken stretch is pressed by belief on its neighbours along the run too.
                if (woken != null) {
                    for (int n : registry.neighbours(place.run)) {
                        for (int block : registry.coveredBlocks(n)) {
                            best = Math.max(best, influence.hostileAt(block % bw, block / bw));
                        }
                    }
                }
                return best;
            }
            int size = influence.blockSize();
            for (int by = Math.max(0, place.top / size); by <= Math.min(influence.height() - 1, place.bottom / size); by++) {
                for (int bx = Math.max(0, place.left / size); bx <= Math.min(influence.width() - 1, place.right / size); bx++) {
                    best = Math.max(best, influence.hostileAt(bx, by));
                }
            }
            return best;
        }

        boolean isTaken(Place place, int squadId) {
            Set<Integer> holders = place.node != null ? buildingHolders.get(place.node) : runHolders.get(place.run);
            if (holders == null) return false;
            return holders.size() > (holders.contains(squadId) ? 1 : 0);
        }

        void taken(Place place) {
            if (place.node != null) {
                buildingHolders.computeIfAbsent(place.node, k -> new HashSet<>()).add(-1);
            } else {
                runHolders.computeIfAbsent(place.run, k -> new HashSet<>()).add(-1);
            }
        }
    }
}
