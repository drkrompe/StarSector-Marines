package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for the {@link World} entity-access facade — the by-id accessors over
 * the archetype {@code EntityWorld}. {@code world.hp(id)} reads/writes the HEALTH
 * column by id with no {@link Entity} dereference; fail-loud on a dead/unknown id
 * (or once the corpse transmute has removed the component). The world it reads is
 * owned by a {@link UnitRosterService} (the spawn seam); this test drives that
 * roster's {@code allocate}/{@code release} directly. Optional capabilities are
 * world components reached through typed accessors ({@code world.mechLoadout(id)},
 * {@code world.hasSecondaryWeapon(id)}, …) — covered by their owning systems' tests.
 */
public class WorldTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void readsAndWritesTheWorldHealthSlotById() {
        UnitRosterService r = roster();
        Entity u = unit("u");
        long id = r.allocate(u);
        World w = new World(r.entityWorld(), r.components(), r.combat(), r.movement());

        // Seeded hp == type.maxHp, readable by id with no Entity deref —
        // backed by the entity world's HEALTH columns.
        assertEquals(u.seedMaxHp, w.hp(id), 1e-6f);

        // setHp by id hits the same world slot every reader sees.
        w.setHp(id, 42f);
        assertEquals(42f, w.hp(id), 1e-6f);
        assertEquals(42f, r.world().hp(id), 1e-6f);
    }

    @Test
    public void isFailLoudOnceHealthIsGoneOrTheIdIsUnknown() {
        UnitRosterService r = roster();
        Entity u = unit("u");
        long id = r.allocate(u);
        World w = new World(r.entityWorld(), r.components(), r.combat(), r.movement());

        // Roster release alone no longer makes hp unreadable — HEALTH stays
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
}
