package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.List;

/**
 * Installs the complete representative rescue payload into a battle. Planning
 * happens before the first entity is spawned, so an unsuitable map leaves the
 * simulation untouched.
 */
public final class CivilianEvacuationPayload {

    private static final UnitType[] V1_TYPES = {
            UnitType.CIVILIAN,
            UnitType.CIVILIAN,
            UnitType.ENGINEER,
            UnitType.CIVILIAN,
            UnitType.CIVILIAN,
            UnitType.SCIENTIST,
            UnitType.CIVILIAN,
            UnitType.CIVILIAN
    };

    public final CivilianEvacuationPlacement placement;
    public final CivilianEvacuationObjective objective;
    private final long[] entityIds;

    private CivilianEvacuationPayload(
            CivilianEvacuationPlacement placement,
            CivilianEvacuationObjective objective,
            long[] entityIds) {
        this.placement = placement;
        this.objective = objective;
        this.entityIds = entityIds;
    }

    public static CivilianEvacuationPayload install(
            BattleSimulation sim, MapResult map, long seed) {
        return map == null ? null
                : install(sim, map.pointsOfInterest, seed);
    }

    public static CivilianEvacuationPayload install(
            BattleSimulation sim, List<PointOfInterest> pointsOfInterest,
            long seed) {
        if (sim == null) return null;
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        if (tracker.registeredCount() != 0 || tracker.isSealed()) return null;

        CivilianEvacuationPlacement placement =
                CivilianEvacuationPlacement.find(
                        sim.getGrid(), pointsOfInterest, seed);
        if (placement == null
                || placement.spawnCount() != V1_TYPES.length
                || tracker.expectedCount() != V1_TYPES.length) {
            return null;
        }

        long[] ids = new long[V1_TYPES.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = sim.spawn(new EntitySpec(
                    "Evacuee " + (i + 1), Faction.CIVILIAN, V1_TYPES[i],
                    placement.spawnX(i), placement.spawnY(i))
                    .role(UnitRole.VIP));
            if (!tracker.register(ids[i])) {
                throw new IllegalStateException(
                        "planned evacuation cohort registration failed");
            }
        }

        CivilianEvacuationObjective objective =
                new CivilianEvacuationObjective(tracker,
                        placement.liftX, placement.liftY,
                        CivilianEvacuationPlacement.LIFT_ZONE_RADIUS);
        sim.addObjective(objective);
        return new CivilianEvacuationPayload(placement, objective, ids);
    }

    public int size() {
        return entityIds.length;
    }

    public long entityId(int index) {
        return entityIds[index];
    }
}
