package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The loops a staffed building's crew walk round its own work.
 *
 * <p>Not a {@link Shift}. A shift is one stop per job, which is right for a
 * bench and wrong for stores: a storekeeper given one stowage stop stands at one
 * pallet, and the walk that carries a crate only happens between two of them
 * ({@link WorkMotions#between}). So this is the local round a ship's department
 * works ({@code ShipWorkRound}) — every point in the building, each claimed as
 * that point, members started at different places on it — with stores stops
 * kept short, because in a warehouse the carry is the work and the stop is only
 * the lift and the set-down.
 *
 * <p>Every stop names its own point, so nobody is handed back the claim they
 * already hold and walks to the pallet they just left, and two hands never stand
 * on one cell.
 */
public final class StaffedCrew {

    /**
     * How near a fight has to come before a worker yields, in cells: the same
     * distance a colonist hears gunfire at.
     */
    public static final float FIGHT_RADIUS = 14f;
    /** Seconds at a stores point: lifting a crate or setting one down. */
    static final float STORES_DWELL = 3.5f;
    /** Seconds at a machine. */
    static final float TENDING_DWELL = 14f;
    /** Seconds at the tally desk or the till, for a hand who also carries. */
    static final float TALLY_DWELL = 9f;
    /** Seconds the till is kept by the one who keeps it, before they look up. */
    static final float TILL_DWELL = 30f;
    /** Stores stops between two visits to a machine, so feed stock is carried to the line in pairs. */
    private static final int CARRIES_PER_MACHINE = 2;

    private StaffedCrew() { }

    /**
     * The loop one member of this building's crew walks, or null where there is
     * nothing in it for them to do.
     *
     * @param site the building
     * @param kind what it is, which decides who keeps the till
     * @param tasks the building's recovered work
     * @param member which hand, from zero; it turns the loop to start elsewhere
     */
    public static AmbientTaskRoute member(JobSite site, BuildingWork.Kind kind,
                                          List<FixtureTask> tasks, int member) {
        List<FixtureTask> stores = of(tasks, Affordance.STOW);
        List<FixtureTask> machines = of(tasks, Affordance.TEND);
        List<FixtureTask> desks = of(tasks, Affordance.READOUT);
        List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
        String id = idPrefix(kind) + site.id() + "-" + member;
        float drift = (member * 0.7f) % 2f;

        // A shop's first hand keeps the till and nothing else; everybody else restocks.
        if (kind == BuildingWork.Kind.GROCERY && member == 0 && !desks.isEmpty()) {
            stops.add(stop(site, desks.get(0), TILL_DWELL));
            return route(id, member, stops);
        }
        int machine = 0;
        for (int i = 0; i < stores.size(); i++) {
            FixtureTask task = stores.get(Math.floorMod(member * 3 + i, stores.size()));
            stops.add(stop(site, task, STORES_DWELL + drift));
            if (!machines.isEmpty() && (i + 1) % CARRIES_PER_MACHINE == 0) {
                stops.add(stop(site, machines.get(Math.floorMod(member + machine++, machines.size())),
                        TENDING_DWELL + drift));
            }
        }
        if (stores.isEmpty()) {
            for (int i = 0; i < machines.size(); i++) {
                stops.add(stop(site, machines.get(Math.floorMod(member + i, machines.size())),
                        TENDING_DWELL + drift));
            }
        }
        // The tally is kept between rounds of the floor, once a loop, by the first hand.
        if (member == 0 && !desks.isEmpty() && kind != BuildingWork.Kind.GROCERY) {
            stops.add(stop(site, desks.get(0), TALLY_DWELL));
        }
        return stops.isEmpty() ? null : route(id, member, stops);
    }

    /**
     * What every hand of a building of this kind is named with, which is how a
     * reader of the roster tells a warehouse's crew from a shop's.
     */
    public static String idPrefix(BuildingWork.Kind kind) {
        return "staffed-" + kind.name().toLowerCase(Locale.ROOT) + "-";
    }

    /**
     * The loop one member of a crew walks while loading a vehicle: a crate lifted at a stores point,
     * carried out and set down at the tailgate, and back for the next, members started at different
     * stock and alternating between the tailgate's places so two are not stood on one.
     *
     * <p>Every leg out is a carry, because the tailgate's group is stores work too: the walk from
     * stock to the vehicle is drawn with the crate in hand ({@link WorkMotions#between}). The walk
     * back is drawn the same way; at this distance a hand going back for the next crate reads as
     * the work, and nothing yet draws empty arms on a stores round.
     *
     * @param tailgate where a crate is set down, one place per hand that can stand there at once
     * @param tailgateGroup the claim group those places answer to
     * @param focusX what a hand at the tailgate faces: the vehicle
     */
    public static AmbientTaskRoute loading(JobSite site, BuildingWork.Kind kind, List<FixtureTask> tasks,
                                           int member, List<float[]> tailgate, String tailgateGroup,
                                           float focusX, float focusY) {
        List<FixtureTask> stores = of(tasks, Affordance.STOW);
        if (stores.isEmpty() || tailgate.isEmpty()) return null;
        List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
        float drift = (member * 0.7f) % 2f;
        for (int i = 0; i < stores.size(); i++) {
            stops.add(stop(site, stores.get(Math.floorMod(member * 3 + i, stores.size())), STORES_DWELL + drift));
            float[] place = tailgate.get(Math.floorMod(member + i, tailgate.size()));
            stops.add(new AmbientTaskRoute.Stop(place[0], place[1], STORES_DWELL,
                    CrewRole.activityFor(Affordance.STOW), focusX, focusY, tailgateGroup, true));
        }
        return route(idPrefix(kind) + site.id() + "-" + member, member, stops);
    }

    /** How many hands this building's work keeps busy, before the caller's own cap. */
    public static int hands(BuildingWork.Kind kind, List<FixtureTask> tasks) {
        int stores = of(tasks, Affordance.STOW).size();
        int machines = of(tasks, Affordance.TEND).size();
        int busy = switch (kind) {
            case WAREHOUSE -> stores / 2;
            case FACTORY -> Math.max(machines, stores / 2);
            case GROCERY -> 1 + stores / 3;
        };
        return Math.max(0, busy);
    }

    private static AmbientTaskRoute route(String id, int member, List<AmbientTaskRoute.Stop> stops) {
        return new AmbientTaskRoute(id, member * 2.3f, Shift.WALK_SPEED, FIGHT_RADIUS,
                AmbientThreatPolicy.FIGHT_NEARBY, stops);
    }

    private static AmbientTaskRoute.Stop stop(JobSite site, FixtureTask task, float dwell) {
        float[] stand = JobBoard.stand(task);
        return new AmbientTaskRoute.Stop(stand[0], stand[1], dwell, CrewRole.activityFor(task.affordance()),
                task.fixtureX() + 0.5f, task.fixtureY() + 0.5f,
                JobBoard.group(site.id(), task.affordance()), true);
    }

    private static List<FixtureTask> of(List<FixtureTask> tasks, Affordance job) {
        List<FixtureTask> matching = new ArrayList<>();
        for (FixtureTask task : tasks) if (task.affordance() == job) matching.add(task);
        return matching;
    }
}
