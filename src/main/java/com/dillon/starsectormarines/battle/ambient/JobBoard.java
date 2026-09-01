package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a site is offering right now: which of its authored jobs can actually be
 * done, and how they are named to whoever comes looking for one.
 *
 * <p>Generation authors what a room affords. This is the other half of that
 * sentence — the part that knows about <em>this</em> map at <em>this</em>
 * moment, which is a different kind of fact and deliberately not baked into the
 * fixture. A berth's servicing job is live while a machine is parked in it and
 * gone when the bay empties; a wrecked fixture's job is gone until the ship is
 * repaired. Neither ever reaches the claim service, so what it sees is a group
 * of interchangeable places, all of them real.
 */
public final class JobBoard {

    private JobBoard() { }

    /**
     * The claim group one kind of job at one site belongs to.
     *
     * <p>Per site, not per map. Somebody asking for somewhere to weld should be
     * offered a berth in the bay they are standing in, not the nearest one three
     * compartments forward — and the claim service resolves by distance, which
     * would happily send them there.
     */
    public static String group(int siteId, Affordance affordance) {
        return siteId + ":" + affordance.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The claim group one job on one <em>machine</em> belongs to.
     *
     * <p><b>A berth is its own group, and it has to be.</b> The rest of a
     * room's work is interchangeable — one bench is as good as the next, which
     * is exactly what a group means — but the machines standing in it are not:
     * six aircraft on an apron are six turnarounds, and an hour spent on one of
     * them is an hour the other five did not get. Filed together they behave as
     * one place to work, and the claim service hands back the claim already
     * held rather than a different aircraft, so a technician services the
     * nearest stand for the whole battle and every other machine in the room is
     * never touched. Measured on a conquest airfield: three technicians, six
     * stands, and berths 2, 3 and 5 went three hundred seconds without a single
     * visit while two of the three sheds sat on a full turnaround they never
     * worked off.
     *
     * <p>Work that stands on its own falls through to {@link #group(int,
     * Affordance)}, because a bench really is interchangeable with the bench
     * beside it.
     */
    public static String group(int siteId, Affordance affordance, int berth) {
        if (berth == FixtureTask.NO_BERTH) return group(siteId, affordance);
        return group(siteId, affordance) + "@" + berth;
    }

    /**
     * Publish a site's live jobs as claimable points.
     *
     * <p>Saying the same thing twice is not an error. A site is published by
     * every shift that reaches it, and shifts share: two berthing spaces on the
     * same deck send their watches to the same mess, so the mess is offered up
     * once by each of them. What a publish states is what is there, and a second
     * statement of it leaves the board as it was. A genuinely clashing id — two
     * sites claiming one number — still fails loudly at
     * {@link TaskPointService#register}, because that is a different mistake.
     *
     * @param berthed whether the berth at each index holds a machine, as
     *     returned by the host that parked them; an empty array means none do
     * @return the affordances that actually have somewhere to be done, in
     *     registration order
     */
    public static List<Affordance> publish(TaskPointService service,
                                           List<FixtureTask> authored,
                                           JobSite site,
                                           boolean[] berthed) {
        Map<Affordance, Integer> counts = new EnumMap<>(Affordance.class);
        // Ids are numbered within their own group, so the two flanks of one
        // stand are #0 and #1 of that stand rather than two entries in a
        // room-wide run — which is what keeps them interchangeable with each
        // other and with nothing else.
        Map<String, Integer> withinGroup = new HashMap<>();
        for (FixtureTask task : live(authored, site, berthed)) {
            String group = group(site.id(), task.affordance(), task.berth());
            counts.merge(task.affordance(), 1, Integer::sum);
            int index = withinGroup.merge(group, 1, Integer::sum) - 1;
            String id = group + "#" + index;
            if (service.isRegistered(id)) continue;
            service.register(new TaskPoint(id, group,
                    task.cellX() + 0.5f, task.cellY() + 0.5f,
                    task.fixtureX() + 0.5f, task.fixtureY() + 0.5f));
        }
        return List.copyOf(counts.keySet());
    }

    /** This site's jobs that can actually be done right now. */
    public static List<FixtureTask> live(List<FixtureTask> authored,
                                         JobSite site,
                                         boolean[] berthed) {
        List<FixtureTask> live = new ArrayList<>();
        for (FixtureTask task : authored) {
            if (!task.inService()) continue;
            if (!site.contains(task.cellX(), task.cellY())) continue;
            if (task.berth() != FixtureTask.NO_BERTH) {
                if (berthed == null
                        || task.berth() >= berthed.length
                        || !berthed[task.berth()]) {
                    continue;
                }
            }
            live.add(task);
        }
        return live;
    }

    /**
     * Whether a job at this site is this role's to do.
     *
     * <p>Berthing is somebody's, and everywhere else is somebody's workplace.
     * In a berth a role has only what it does in its own quarters, and only in
     * its own: a ship carrying two populations berths them separately, so a
     * marine turning in wherever the nearest bunkroom happened to be would be
     * sleeping in the crew's, and a technician has no business in the marines'
     * berthing at all.
     *
     * <p>Everywhere else, only the jobs the role works on watch. That second
     * half matters as much as the first, because an affordance is not a job on
     * its own: stowage means the parts run in a vehicle bay and somebody's own
     * locker in a berth, and a role that simply worked stowage was offered a
     * shift running a bay it has nothing to do with.
     */
    public static boolean belongsTo(CrewRole role, JobSite site, Affordance affordance) {
        if (CrewRole.isBerthing(site.purpose())) {
            return site.purpose() == role.quarters() && role.offWatch().contains(affordance);
        }
        return role.onWatch().contains(affordance);
    }
}
