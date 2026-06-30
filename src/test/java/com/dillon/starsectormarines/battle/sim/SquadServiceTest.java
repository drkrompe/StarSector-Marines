package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for {@link SquadService} — the data owner for the presence-based
 * {@code SQUAD} membership component. {@code allocate} attaches SQUAD iff the unit
 * seeds a real squad ({@code seedSquadId != NO_SQUAD}); membership <em>is</em>
 * presence, so a solo unit carries no component and {@link SquadService#hasSquad} is
 * false (the {@code NO_SQUAD} sentinel is never a stored value); the
 * {@link SquadService#squadId} read is fail-loud on a non-member; and
 * {@link SquadService#assignSquad} is the post-spawn join seam (an archetype
 * row-move). Mirrors {@link VisionServiceTest} / {@link CombatServiceTest}.
 */
public class SquadServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsSquadMembershipFromTheSeed() {
        UnitRosterService r = roster();
        Entity u = unit("u");
        u.seedSquadId = 7;
        long id = r.allocate(u);
        SquadService squad = r.squad();

        assertTrue(squad.hasSquad(id));
        assertEquals(7, squad.squadId(id));
    }

    @Test
    public void aSoloUnitCarriesNoSquadComponent() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));   // seedSquadId defaults to NO_SQUAD
        // Presence IS membership: a NO_SQUAD seed attaches no component, so the
        // sentinel is never a stored value — hasSquad just reads false.
        assertFalse(r.squad().hasSquad(id));
    }

    @Test
    public void assignSquadJoinsAFreshUnit() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));   // starts solo
        SquadService squad = r.squad();
        assertFalse(squad.hasSquad(id));

        // The post-spawn join seam — a row-move that adds the component, then sets
        // the key (the deboard-after-allocate / drone-spawn-serial path).
        squad.assignSquad(id, 9);
        assertTrue(squad.hasSquad(id));
        assertEquals(9, squad.squadId(id));
    }

    @Test
    public void squadIdIsFailLoudOnANonMemberOrUnknownId() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));   // solo — no SQUAD component
        SquadService squad = r.squad();

        assertFalse(squad.hasSquad(id));
        assertThrows(IllegalArgumentException.class, () -> squad.squadId(id));     // non-member
        assertThrows(IllegalArgumentException.class, () -> squad.squadId(999L));   // never allocated
    }
}
