package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Puts the people who work a building into it.
 *
 * <p>The same watch bill a ship's deck is crewed from, asked of a room on the
 * ground. Nothing in the model was ever shipboard — a shift is a role's jobs
 * across the places that publish them, and a garrison's motor pool answers those
 * questions exactly as a compartment does — so this is the posting policy for a
 * surface map rather than a second implementation of the model.
 *
 * <p><b>Who is based where is not decided here.</b> A role is based at a site
 * when the site offers that role's own trade, which is a question the shift
 * answers off what generation published: put a technician in a bay with
 * machines in it and they weld, fetch and read off terminals; put the same
 * technician in a barracks and they have nothing to do there. So a caller says
 * which kinds of room it wants worked and how deep a watch to stand, and the
 * rooms decide the rest — including saying no, which is what an empty bay does.
 *
 * <p>A shift is posted with <b>its own room as the only candidate site</b>, so
 * the technicians stay in the shed. The model would happily send them further:
 * a defect round is a circuit and reaches every place that publishes one, which
 * on a fortress map is half the ward. That is a decision about a garrison's
 * routine rather than about the shift machinery, and a works crew wandering the
 * yard between the armoury and the mess is a different feature from a works crew
 * working. Widen the candidate set when the yard is what is being modelled.
 */
public final class StructureWatch {

    private StructureWatch() { }

    /**
     * Man every room of the named purposes, and put its people at their work.
     *
     * @param side who the workers belong to
     * @param sites every room on the map
     * @param authored the map's published work points
     * @param berthed whether each authored berth holds something, which decides
     *     whether a bay publishes servicing at all
     * @param purposes the kinds of room to work
     * @param watch how many people of one trade to stand in one room, before the
     *     room's own capacity is applied
     * @return the actors taken on, in the order they were hired
     */
    public static List<Long> man(BattleSimulation sim, Faction side,
                                 List<RoomSite> sites, List<FixtureTask> authored,
                                 boolean[] berthed, Set<RoomPurpose> purposes, int watch) {
        List<Long> hired = new ArrayList<>();
        if (watch <= 0 || sites.isEmpty()) return hired;

        for (RoomSite site : sites) {
            if (!purposes.contains(site.purpose())) continue;
            for (CrewRole role : CrewRole.values()) {
                if (!Shift.basedAt(role, site, authored, berthed)) continue;
                hired.addAll(stand(sim, side, site, role, authored, berthed, watch));
            }
        }
        if (!hired.isEmpty()) sim.ambientTasks().settle();
        return hired;
    }

    /** Draw up one trade's bill for one room and take on the hands it holds. */
    private static List<Long> stand(BattleSimulation sim, Faction side, RoomSite site,
                                    CrewRole role, List<FixtureTask> authored,
                                    boolean[] berthed, int watch) {
        Shift bill = Shift.postedAt(role, site, List.of(site), authored, berthed,
                AmbientThreatPolicy.HOSTILE_COMBATANT);
        // The claim groups have to exist before anybody is handed a route that
        // names one, so the board is published before the first hand is taken on.
        for (JobSite worked : bill.sites()) {
            JobBoard.publish(sim.taskPoints(), authored, worked, berthed);
        }

        List<Long> hired = new ArrayList<>();
        int hands = Math.min(watch, bill.capacity());
        for (int index = 0; index < hands; index++) {
            AmbientTaskRoute route = bill.member(index);
            if (route == null) break;
            AmbientTaskRoute.Stop start = AmbientTaskService.standingPlace(route, 0f);
            EntitySpec worker = new EntitySpec(route.id(), side, role.unit(),
                    (int) Math.floor(start.worldX()), (int) Math.floor(start.worldY()));
            long actor = sim.spawn(worker);
            sim.ambientTasks().assign(actor, route);
            hired.add(actor);
        }
        return hired;
    }
}
