package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.component.Crashing;
import com.dillon.starsectormarines.battle.component.DeadBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for the {@link World} entity-access facade — both faces, proven over
 * a bare {@link UnitRegistry} + a sparse {@link ComponentStore} (no full sim).
 *
 * <p><b>Hot face:</b> {@code world.hp(id)} reads/writes the same dense SoA slot
 * the registry owns, by id, with no {@link Unit} dereference; fail-loud on a
 * dead/unknown id. <b>Cold face:</b> {@code world.id(id).getOrNull(Cmp.class)}
 * is a presence lookup in the type's store — the component instance when present,
 * null when the entity lacks it or the type has no store.
 */
public class WorldTest {

    private static Unit unit(String label) {
        return new Unit(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void hotFaceReadsAndWritesTheDenseHpSlotById() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        long id = r.allocate(u);
        World w = new World(r, Map.of());

        // Seeded hp == type.maxHp, readable by id with no Unit deref.
        assertEquals(u.seedMaxHp, w.hp(id), 1e-6f);

        // setHp by id hits the same slot the registry / OO accessor see.
        w.setHp(id, 42f);
        assertEquals(42f, w.hp(id), 1e-6f);
        assertEquals(42f, r.getHp(r.indexOf(u.entityId)), 1e-6f);
        assertEquals(42f, u.getHp(), 1e-6f);
    }

    @Test
    public void hotFaceIsFailLoudOnADeadOrUnknownId() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        long id = r.allocate(u);
        World w = new World(r, Map.of());

        r.release(id);
        assertThrows(IllegalArgumentException.class, () -> w.hp(id));   // released
        assertThrows(IllegalArgumentException.class, () -> w.hp(999L)); // never allocated
    }

    @Test
    public void coldFaceProjectsComponentPresenceByType() {
        UnitRegistry r = new UnitRegistry();
        Unit u = unit("u");
        long id = r.allocate(u);
        ComponentStore<DeadBody> bodies = new ComponentStore<>();
        World w = new World(r, Map.of(DeadBody.class, bodies));

        // Absent component → null (presence is the data).
        assertNull(w.id(id).getOrNull(DeadBody.class));

        // Present → the exact instance.
        DeadBody body = new DeadBody(UnitType.MARINE_BLUE, Faction.MARINE, 2);
        bodies.add(id, body);
        assertSame(body, w.id(id).getOrNull(DeadBody.class));

        // A type with no registered store → null, not an error.
        assertNull(w.id(id).getOrNull(Crashing.class));
    }
}
