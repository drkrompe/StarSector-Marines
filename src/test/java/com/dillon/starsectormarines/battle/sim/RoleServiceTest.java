package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for {@link RoleService} — the data owner for the universal {@code ROLE}
 * behavior-dispatch component. {@code allocate} seeds ROLE from {@code Entity.seedRole}
 * (default {@code COMBATANT}); {@link RoleService#role} reads it back as the enum
 * (hiding the ordinal round-trip); {@link RoleService#setRole} is the live-reassignment
 * seam (the kit-pickup promote/revert); and the read is fail-loud on an unknown /
 * released id. Mirrors {@link SquadServiceTest} / {@link VisionServiceTest}.
 */
public class RoleServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static EntitySpec unit(String label) {
        return new EntitySpec(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsRoleFromTheSeed() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u").role(UnitRole.KIT_RETRIEVER));

        assertEquals(UnitRole.KIT_RETRIEVER, r.role().role(id));
    }

    @Test
    public void defaultRoleIsCombatant() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));   // seedRole defaults to COMBATANT

        assertEquals(UnitRole.COMBATANT, r.role().role(id));
    }

    @Test
    public void setRoleReassignsALiveUnit() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        RoleService role = r.role();
        assertEquals(UnitRole.COMBATANT, role.role(id));

        // The runtime seam — a kit pickup promotes the marine, then it reverts.
        role.setRole(id, UnitRole.PLANTER);
        assertEquals(UnitRole.PLANTER, role.role(id));
        role.setRole(id, UnitRole.COMBATANT);
        assertEquals(UnitRole.COMBATANT, role.role(id));
    }

    @Test
    public void roleIsFailLoudOnAnUnknownId() {
        UnitRosterService r = roster();
        r.spawn(unit("u"));

        assertThrows(IllegalArgumentException.class, () -> r.role().role(999L));   // never allocated
    }
}
