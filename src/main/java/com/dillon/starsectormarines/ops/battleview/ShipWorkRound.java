package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ship staff finish a round of their own room's work before their ordinary wider rotation. */
public final class ShipWorkRound {
    private ShipWorkRound() { }

    /** A department's own room gets its first hand before secondary benches or bay stores. */
    public static int postingPriority(CrewRole role, RoomPurpose purpose) {
        return switch (role) {
            case MACHINIST -> purpose == RoomPurpose.PARTS_CAGE ? 0 : 1;
            case STOREKEEPER -> purpose == RoomPurpose.STOCKROOM ? 0 : purpose == RoomPurpose.LOADING_BAY ? 1 : 2;
            default -> 0;
        };
    }

    public static AmbientTaskRoute expand(CrewRole role, JobSite room, List<FixtureTask> authored,
                                          boolean[] berthed, AmbientTaskRoute ordinary, int member,
                                          boolean dutyOnly) {
        if (ordinary == null || !supports(role)) return ordinary;
        List<FixtureTask> work = JobBoard.live(authored, room, berthed).stream()
                .filter(task -> task.berth() == FixtureTask.NO_BERTH
                        && task.affordance().duty() && task.affordance() != Affordance.ROUNDS
                        && task.affordance() != Affordance.WATCH && task.affordance() != Affordance.GUARD
                        && JobBoard.belongsTo(role, room, task.affordance())).toList();
        if (work.isEmpty()) return ordinary;
        List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
        Set<String> localGroups = new HashSet<>();
        // Authored ordering follows equipment placement; offset workers without
        // changing the set of stations each person will eventually visit.
        for (int i = 0; i < work.size(); i++) {
            FixtureTask task = work.get(Math.floorMod(member + i, work.size()));
            String group = JobBoard.group(room.id(), task.affordance());
            localGroups.add(group);
            stops.add(new AmbientTaskRoute.Stop(task.cellX() + .5f, task.cellY() + .5f,
                    CrewRole.dwellFor(task.affordance()), CrewRole.activityFor(task.affordance()),
                    task.fixtureX() + .5f, task.fixtureY() + .5f, group, true));
        }
        if (!dutyOnly) for (AmbientTaskRoute.Stop stop : ordinary.stops())
            if (!localGroups.contains(stop.pointGroup())) stops.add(stop);
        return new AmbientTaskRoute(ordinary.id(), ordinary.phaseOffsetSeconds(), ordinary.walkSpeedCellsPerSecond(),
                ordinary.threatRadiusCells(), ordinary.threatPolicy(), stops);
    }

    private static boolean supports(CrewRole role) {
        return switch (role) {
            case ENGINE_WATCH, MEDIC, COOK, ARMOURER, MACHINIST, STOREKEEPER -> true;
            case BRIDGE_WATCH, SECURITY, MARINE, MECH_TECH -> false;
        };
    }
}
