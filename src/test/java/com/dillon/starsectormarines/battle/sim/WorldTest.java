package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.air.components.CrashingComponent;
import com.dillon.starsectormarines.battle.unit.components.RenderPositionComponent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for the {@link World} entity-access facade — both faces, proven over
 * a bare {@link UnitRegistry} + a sparse {@link ComponentStore} (no full sim).
 *
 * <p><b>Hot face:</b> {@code world.hp(id)} reads/writes the same dense SoA slot
 * the registry owns, by id, with no {@link Entity} dereference; fail-loud on a
 * dead/unknown id. <b>Cold face:</b> {@code world.id(id).getOrNull(Cmp.class)}
 * is a presence lookup in the type's store — the component instance when present,
 * null when the entity lacks it or the type has no store.
 */
public class WorldTest {

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void hotFaceReadsAndWritesTheWorldHealthSlotById() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        long id = r.allocate(u);
        World w = new World(r, Map.of());

        // Seeded hp == type.maxHp, readable by id with no Entity deref —
        // backed by the entity world's HEALTH columns (migration step 3).
        assertEquals(u.seedMaxHp, w.hp(id), 1e-6f);

        // setHp by id hits the same world slot the registry adapters see.
        w.setHp(id, 42f);
        assertEquals(42f, w.hp(id), 1e-6f);
        assertEquals(42f, r.hpById(id), 1e-6f);
    }

    @Test
    public void hotFaceIsFailLoudOnceHealthIsGoneOrTheIdIsUnknown() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        long id = r.allocate(u);
        World w = new World(r, Map.of());

        // Registry release alone no longer makes hp unreadable — HEALTH stays
        // on the world entity until the death drain transmutes it to a corpse
        // (and every release path zeroes hp first, so liveness reads dead).
        w.setHp(id, 0f);
        r.release(id);
        assertFalse(w.isAlive(id));
        assertEquals(0f, w.hp(id), 1e-6f, "readable in the release→transmute window");

        // The corpse transmute removes HEALTH — hp is fail-loud from then on,
        // as is any never-allocated id.
        r.entityWorld().removeComponent(id, r.components().HEALTH);
        assertThrows(IllegalArgumentException.class, () -> w.hp(id));   // corpse
        assertThrows(IllegalArgumentException.class, () -> w.hp(999L)); // never allocated
    }

    @Test
    public void coldFaceProjectsComponentPresenceByType() {
        UnitRegistry r = new UnitRegistry();
        Entity u = unit("u");
        long id = r.allocate(u);
        ComponentStore<RenderPositionComponent> positions = new ComponentStore<>();
        World w = new World(r, Map.of(RenderPositionComponent.class, positions));

        // Absent component → null (presence is the data).
        assertNull(w.id(id).getOrNull(RenderPositionComponent.class));

        // Present → the exact instance.
        RenderPositionComponent pos = new RenderPositionComponent(3f, 7f);
        positions.add(id, pos);
        assertSame(pos, w.id(id).getOrNull(RenderPositionComponent.class));

        // A type with no registered store → null, not an error.
        assertNull(w.id(id).getOrNull(CrashingComponent.class));
    }
}
