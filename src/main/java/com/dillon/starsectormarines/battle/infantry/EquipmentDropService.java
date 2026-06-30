package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Data owner for the active equipment-drop list. {@link #emitIfApplicable(Entity)}
 * is called by the sim's death cascade ({@code DamageResolver.resolve} for all
 * damage paths — combat fire, AoE splash, and external strafing all route
 * through it) to drop a kit where a carrier fell; the per-tick retrieve / pickup
 * sweep that consumes the list lives on {@link EquipmentDropSystem}.
 *
 * <p>A <b>Service</b> (data owner) — it holds the drop list and exposes the
 * read ({@link #getEquipmentDrops()}) and mutators ({@link #emitIfApplicable},
 * {@link #removeConsumed()}) for it, per the
 * Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <p>Holds {@link UnitRosterService} only for the dying carrier's cell read in
 * {@link #emitIfApplicable}. Sibling slice to {@link UnitRosterService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 */
public final class EquipmentDropService {

    private final UnitRosterService rosterService;

    private final List<EquipmentDrop> equipmentDrops = new ArrayList<>();

    public EquipmentDropService(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDrops; }

    /**
     * Emits an {@link EquipmentDrop} at the dying unit's cell if they were
     * carrying a kit — i.e., a PLANTER (or a KIT_RETRIEVER whose pointer maps
     * to an objective). Skips drops that would point at a completed objective.
     * The drop is placed at the unit's current cell; mission code should
     * ensure the cell is walkable for normal combat, so retrieval is reachable.
     */
    public void emitIfApplicable(Entity dead) {
        Objective carried = null;
        if (dead.role == UnitRole.PLANTER) {
            carried = dead.assignedObjective;
        } else if (dead.role == UnitRole.KIT_RETRIEVER && dead.equipmentDropTarget != null
                && !dead.equipmentDropTarget.consumed) {
            // Retriever was carrying nothing in-hand, but their target kit
            // is still on the ground. We don't emit a new drop — the existing
            // one remains in the world for someone else to grab.
            return;
        }
        if (carried == null || carried.isComplete()) return;
        // Called from DamageResolver.resolve's died branch before release, so the
        // dead unit is still registered — read its cell by id.
        World world = rosterService.world();
        equipmentDrops.add(new EquipmentDrop(world.cellX(dead.entityId), world.cellY(dead.entityId), carried));
    }

    /**
     * Drops consumed entries from the active list — called by
     * {@link EquipmentDropSystem} at the end of its per-tick sweep.
     */
    public void removeConsumed() {
        for (int i = equipmentDrops.size() - 1; i >= 0; i--) {
            if (equipmentDrops.get(i).consumed) equipmentDrops.remove(i);
        }
    }
}
