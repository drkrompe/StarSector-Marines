package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code TASK} component — typed by-id access to a unit's
 * objective/kit assignment in the archetype {@link EntityWorld}: the
 * {@link Objective} it is acting on and the {@link EquipmentDrop} kit a KIT_RETRIEVER
 * is heading to (both nullable OBJECT fields).
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it. A consumer reaches it
 * via {@code sim.task()} / {@code roster.task()} and calls
 * {@code task.assignedObjective(id)} directly — no {@link World} hop.
 *
 * <p>{@code TASK} is OPTIONAL, but its reads are <b>tolerant</b> (not fail-loud): a
 * unit that was never tasked carries no TASK, and {@link #assignedObjective} /
 * {@link #equipmentDropTarget} return {@code null} for it — preserving the old
 * "{@code null} = no task" semantics of the plain {@code Entity} fields, which readers
 * probe on arbitrary units. The setters <b>add the component if absent</b> (an
 * archetype row-move) and run in serial phases; the parallel-dispatch clear
 * ({@link #clearEquipmentDropTarget}, from {@code KitRetrieverBehavior}) is a plain
 * null field-write that never changes the archetype. Serial-only except that lone
 * own-unit clear.
 */
public final class TaskService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public TaskService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries a TASK (has ever been assigned an objective/kit). */
    public boolean has(long id) { return entityWorld.has(id, components.TASK); }

    /** The objective this unit is acting on, or {@code null} (tolerant — null when the unit has no TASK). */
    public Objective assignedObjective(long id) {
        return entityWorld.has(id, components.TASK)
                ? (Objective) entityWorld.getObject(id, components.TASK, BattleComponents.TASK_ASSIGNED_OBJECTIVE)
                : null;
    }

    /** The kit a KIT_RETRIEVER is heading to, or {@code null} (tolerant — null when the unit has no TASK). */
    public EquipmentDrop equipmentDropTarget(long id) {
        return entityWorld.has(id, components.TASK)
                ? (EquipmentDrop) entityWorld.getObject(id, components.TASK, BattleComponents.TASK_EQUIPMENT_DROP)
                : null;
    }

    /**
     * Sets {@code id}'s assigned objective — the runtime seam (a kit pickup promotes a
     * marine to PLANTER with the kit's objective; the deboard loadout seeds the spawn
     * case at {@code allocate} from {@code Entity.seedAssignedObjective}). Adds the TASK
     * component if the unit had none (an archetype row-move). Serial-only — never
     * mid-{@code Query} walk.
     */
    public void setAssignedObjective(long id, Objective objective) {
        if (!entityWorld.has(id, components.TASK)) entityWorld.addComponent(id, components.TASK);
        entityWorld.setObject(id, components.TASK, BattleComponents.TASK_ASSIGNED_OBJECTIVE, objective);
    }

    /**
     * Sets {@code id}'s kit target — the assignment seam ({@code EquipmentDropSystem}
     * recruits the nearest marine as a KIT_RETRIEVER). Adds the TASK component if the
     * unit had none. Serial-only.
     */
    public void setEquipmentDropTarget(long id, EquipmentDrop drop) {
        if (!entityWorld.has(id, components.TASK)) entityWorld.addComponent(id, components.TASK);
        entityWorld.setObject(id, components.TASK, BattleComponents.TASK_EQUIPMENT_DROP, drop);
    }

    /**
     * Clears {@code id}'s kit target (on pickup, or when {@code KitRetrieverBehavior}
     * demotes a retriever whose drop is gone). A plain null field-write on an existing
     * TASK — <b>parallel-safe</b> (the demote runs in the UPDATE_UNITS dispatch on the
     * unit's own row) because it never adds/removes a component. No-op if the unit has
     * no TASK.
     */
    public void clearEquipmentDropTarget(long id) {
        if (entityWorld.has(id, components.TASK)) {
            entityWorld.setObject(id, components.TASK, BattleComponents.TASK_EQUIPMENT_DROP, null);
        }
    }
}
