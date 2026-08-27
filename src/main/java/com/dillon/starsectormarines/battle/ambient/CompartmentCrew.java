package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * Puts a compartment's authored jobs to work: which of them are live, who may
 * claim them, and the shift a crew member of a given role walks.
 *
 * <p>This is the join the deck was missing. Generation authors what a room
 * affords, {@link CrewRole} says whose job each of those is, and the ambient
 * service already knows how to walk somebody round a loop and stand them at the
 * end of it. Nothing here is a new mechanism — it is the wiring that means a
 * generated bay is inhabited without a single authored waypoint.
 *
 * <p><b>A job only exists while it can be done.</b> A berth's servicing job is
 * live when a machine is parked in it and gone when the bay is empty, and a
 * wrecked fixture's job is gone until the ship is repaired. Both are decided
 * here, at the moment the deck is staffed, because both are facts about this
 * deck now rather than about the room — which is why they never reach the claim
 * service. What it sees is a group of interchangeable places, all of them real.
 */
public final class CompartmentCrew {

    /** Cells a second an ambient walker covers. Unhurried: this is a shift, not a sortie. */
    private static final float WALK_SPEED = 0.72f;
    /** How near an armed stranger has to be before somebody stops working. */
    private static final float THREAT_RADIUS = 14f;

    private CompartmentCrew() { }

    /**
     * The claim group one kind of job in one compartment belongs to.
     *
     * <p>Per compartment, not per deck. A technician asking for somewhere to
     * weld should be offered a berth in the bay they are standing in, not the
     * nearest one three compartments forward — and the claim service resolves by
     * distance, which would happily send them there.
     */
    public static String group(int compartmentId, Affordance affordance) {
        return compartmentId + ":" + affordance.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Publish this compartment's live jobs as claimable points.
     *
     * @param occupiedBerths whether the berth at each index holds a machine,
     *     as returned by the host that parked them; an empty array means none do
     * @return the affordances that actually have somewhere to be done, in
     *     registration order
     */
    public static List<Affordance> publish(TaskPointService service,
                                           List<FixtureTask> tasks,
                                           DeckGraph.Compartment compartment,
                                           boolean[] occupiedBerths) {
        Map<Affordance, Integer> counts = new EnumMap<>(Affordance.class);
        for (FixtureTask task : live(tasks, compartment, occupiedBerths)) {
            String group = group(compartment.id(), task.affordance());
            int index = counts.merge(task.affordance(), 1, Integer::sum) - 1;
            service.register(new TaskPoint(group + "#" + index, group,
                    task.cellX() + 0.5f, task.cellY() + 0.5f,
                    task.fixtureX() + 0.5f, task.fixtureY() + 0.5f));
        }
        return List.copyOf(counts.keySet());
    }

    /** This compartment's jobs that can actually be done right now. */
    public static List<FixtureTask> live(List<FixtureTask> tasks,
                                         DeckGraph.Compartment compartment,
                                         boolean[] occupiedBerths) {
        List<FixtureTask> live = new ArrayList<>();
        for (FixtureTask task : tasks) {
            if (!task.inService()) continue;
            if (!compartment.contains(task.cellX(), task.cellY())) continue;
            if (task.berth() != FixtureTask.NO_BERTH) {
                if (occupiedBerths == null
                        || task.berth() >= occupiedBerths.length
                        || !occupiedBerths[task.berth()]) {
                    continue;
                }
            }
            live.add(task);
        }
        return live;
    }

    /**
     * One crew member's shift: their role's jobs in this compartment, in the
     * order the role comes round to them.
     *
     * <p>The route names claim <em>groups</em> rather than coordinates, so two
     * technicians never weld on the same machine and a third simply waits for a
     * berth to come free. The coordinates carried on each stop are the
     * deterministic fallback a pose-only host samples; they are chosen per actor
     * so that even a host which ignores claims does not stack everybody on one
     * cell.
     *
     * @param index which member of the shift this is, which sets both their
     *     phase and which job they start on
     * @return the route, or null when this compartment has no work for the role
     */
    public static AmbientTaskRoute shift(CrewRole role, DeckGraph.Compartment compartment,
                                         List<FixtureTask> tasks, boolean[] occupiedBerths,
                                         int index, AmbientThreatPolicy threatPolicy) {
        Map<Affordance, List<FixtureTask>> byJob = new LinkedHashMap<>();
        for (FixtureTask task : live(tasks, compartment, occupiedBerths)) {
            if (!role.works(task.affordance())) continue;
            byJob.computeIfAbsent(task.affordance(), key -> new ArrayList<>()).add(task);
        }
        if (byJob.isEmpty()) return null;

        List<Affordance> order = new ArrayList<>();
        for (Affordance job : role.jobs()) {
            if (byJob.containsKey(job)) order.add(job);
        }
        // Start each member on a different job, so a shift coming on watch
        // spreads across the room instead of queueing at the first station.
        int start = order.isEmpty() ? 0 : Math.floorMod(index, order.size());

        List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
        for (int step = 0; step < order.size(); step++) {
            Affordance job = order.get((start + step) % order.size());
            List<FixtureTask> places = byJob.get(job);
            FixtureTask sample = places.get(Math.floorMod(index, places.size()));
            stops.add(new AmbientTaskRoute.Stop(
                    sample.cellX() + 0.5f, sample.cellY() + 0.5f,
                    CrewRole.dwellFor(job), CrewRole.activityFor(job),
                    sample.fixtureX() + 0.5f, sample.fixtureY() + 0.5f,
                    group(compartment.id(), job)));
        }
        String id = role.name().toLowerCase(Locale.ROOT)
                + "-" + compartment.id() + "-" + index;
        return new AmbientTaskRoute(id, index * 1.7f, WALK_SPEED, THREAT_RADIUS,
                threatPolicy, stops);
    }

    /**
     * How many of a role a compartment can keep busy at once.
     *
     * <p>Bounded by the scarcest job the role works, not the most plentiful. A
     * bay with eight berths and one terminal cannot occupy eight technicians on
     * a rotation that includes the terminal — seven of them would be waiting at
     * it — so the honest number is what the room can sustain, and the rest of
     * the crew are somewhere else on the ship.
     */
    public static int capacity(CrewRole role, DeckGraph.Compartment compartment,
                               List<FixtureTask> tasks, boolean[] occupiedBerths) {
        Map<Affordance, Integer> counts = new EnumMap<>(Affordance.class);
        for (FixtureTask task : live(tasks, compartment, occupiedBerths)) {
            if (!role.works(task.affordance())) continue;
            counts.merge(task.affordance(), 1, Integer::sum);
        }
        if (counts.isEmpty()) return 0;
        int fewest = Integer.MAX_VALUE;
        for (int count : counts.values()) fewest = Math.min(fewest, count);
        return fewest;
    }
}
