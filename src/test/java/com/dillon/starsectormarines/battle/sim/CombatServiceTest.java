package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for {@link CombatService}'s OBJECT primary-weapon column — the COMBAT
 * data owner seeds {@code primaryWeapon} from the unit's write-only
 * {@code seedPrimaryWeapon} at {@code allocate}, the by-id getter/setter hit the
 * one world slot every reader sees, an unseeded combatant reads {@code null}
 * (the fall-back-to-baked-stats signal), and COMBAT is combatant-only + live-only
 * so the accessors are fail-loud on a corpse / unknown id. Mirrors
 * {@link VisionServiceTest}.
 */
public class CombatServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsThePrimaryWeaponFromTheUnitSeed() {
        UnitRosterService r = roster();
        Entity u = unit("u");
        u.seedPrimaryWeapon = MarineWeapon.PULSE_RIFLE;
        long id = r.allocate(u);
        CombatService combat = r.combat();

        assertTrue(combat.has(id));
        // Seeded from the write-only seed (no Entity deref afterward) — the SAME
        // flyweight instance the deboard loadout handed in, not a copy.
        assertSame(MarineWeapon.PULSE_RIFLE, combat.primaryWeapon(id));
    }

    @Test
    public void primaryWeaponDefaultsToNullWhenUnseeded() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));   // no seedPrimaryWeapon set

        // A combatant with no per-weapon profile (the militia/alien/turret shape):
        // the OBJECT column appends null, so the getter reads null — the
        // fall-back-to-baked-stats signal every fire/scoring reader keys off.
        assertNull(r.combat().primaryWeapon(id));
    }

    @Test
    public void setterHitsTheSharedWorldSlot() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));
        CombatService combat = r.combat();

        // The deboard-loadout seam: setPrimaryWeapon writes the same column the
        // fire/scoring code reads back.
        combat.setPrimaryWeapon(id, MarineWeapon.SMG);
        assertSame(MarineWeapon.SMG, combat.primaryWeapon(id));
    }

    @Test
    public void isFailLoudOnceCombatIsGoneOrTheIdIsUnknown() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));
        CombatService combat = r.combat();

        // The corpse transmute removes COMBAT (a corpse does not fight) — reads are
        // fail-loud from then on, as is any never-allocated id.
        r.entityWorld().removeComponent(id, r.components().COMBAT);
        assertFalse(combat.has(id));
        assertThrows(IllegalArgumentException.class, () -> combat.primaryWeapon(id));    // corpse
        assertThrows(IllegalArgumentException.class, () -> combat.primaryWeapon(999L));  // never allocated
    }
}
