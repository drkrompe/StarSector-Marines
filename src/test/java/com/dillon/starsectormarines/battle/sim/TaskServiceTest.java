package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link TaskService} — the data owner for the {@code TASK} component
 * (nullable {@code assignedObjective} + {@code equipmentDropTarget}). {@code allocate}
 * attaches TASK iff the unit seeds an objective; the runtime setters add TASK if absent;
 * {@link TaskService#clearEquipmentDropTarget} nulls the field without removing the
 * component (the parallel-safe kit-retriever demote); and — unlike the other Services —
 * the reads are <b>tolerant</b>: an untasked or unknown id reads {@code null}, never
 * fail-loud (preserving the old "null = no task" nullable-field semantics readers probe
 * on arbitrary units).
 */
public class TaskServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE, 0, 0);
    }

    @Test
    public void allocateSeedsTheAssignedObjectiveFromTheSeed() {
        UnitRosterService r = roster();
        Objective obj = new ChargeSiteObjective(3, 3, 5f, "site");
        Entity u = unit("p");
        u.seedAssignedObjective = obj;
        long id = r.allocate(u);
        TaskService task = r.task();

        assertTrue(task.has(id));
        assertSame(obj, task.assignedObjective(id));
        assertNull(task.equipmentDropTarget(id), "the kit-target field appends null");
    }

    @Test
    public void anUntaskedUnitReadsTolerantNull() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // seedAssignedObjective defaults to null
        TaskService task = r.task();

        assertFalse(task.has(id));
        // Tolerant, NOT fail-loud — the readers probe these on arbitrary units.
        assertNull(task.assignedObjective(id));
        assertNull(task.equipmentDropTarget(id));
        assertNull(task.assignedObjective(999L), "an unknown id also reads tolerant null");
    }

    @Test
    public void setEquipmentDropTargetAddsTaskThenClearNullsTheField() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // starts untasked
        TaskService task = r.task();
        Objective obj = new ChargeSiteObjective(4, 4, 5f, "kit");
        EquipmentDrop kit = new EquipmentDrop(4, 4, obj);

        // Recruiting a KIT_RETRIEVER adds TASK (an archetype row-move) then sets the kit.
        task.setEquipmentDropTarget(id, kit);
        assertTrue(task.has(id));
        assertSame(kit, task.equipmentDropTarget(id));

        // The parallel-safe demote clears the field but keeps the component present.
        task.clearEquipmentDropTarget(id);
        assertTrue(task.has(id));
        assertNull(task.equipmentDropTarget(id));
    }

    @Test
    public void setAssignedObjectiveAddsTaskToAFreshUnit() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // starts untasked
        TaskService task = r.task();
        Objective obj = new ChargeSiteObjective(5, 5, 5f, "promoted");

        task.setAssignedObjective(id, obj);
        assertTrue(task.has(id));
        assertSame(obj, task.assignedObjective(id));
    }
}
