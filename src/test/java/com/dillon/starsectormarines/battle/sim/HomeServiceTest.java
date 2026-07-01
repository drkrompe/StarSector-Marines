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
 * Contract for {@link HomeService} — the data owner for the presence-based
 * {@code HOME} garrison-post component. {@code allocate} attaches HOME iff the unit
 * seeds a post ({@code seedHomeCellX >= 0}); presence <em>is</em> "has a post", so a
 * roaming unit carries no component and {@link HomeService#hasHome} is false (the old
 * {@code -1} sentinel is never a stored value); the cell reads are fail-loud on a unit
 * with no post; and {@link HomeService#setHome} is the runtime reassignment seam (an
 * archetype row-move that adds HOME if absent). Mirrors {@link SquadServiceTest}.
 */
public class HomeServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.DEFENDER, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsThePostFromTheSeed() {
        UnitRosterService r = roster();
        Entity u = unit("g");
        u.seedHomeCellX = 4;
        u.seedHomeCellY = 7;
        long id = r.allocate(u);
        HomeService home = r.home();

        assertTrue(home.hasHome(id));
        assertEquals(4, home.homeCellX(id));
        assertEquals(7, home.homeCellY(id));
    }

    @Test
    public void aRoamingUnitCarriesNoHomeComponent() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // seedHomeCell defaults to -1
        // Presence IS "has a post": a -1 seed attaches no component, so the sentinel is
        // never a stored value — hasHome just reads false.
        assertFalse(r.home().hasHome(id));
    }

    @Test
    public void setHomeAssignsAPostToAFreshUnit() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // starts postless
        HomeService home = r.home();
        assertFalse(home.hasHome(id));

        // The runtime reassignment seam — a row-move that adds the component, then sets
        // the cell (the SquadFallbackSystem retreat-redistribution path).
        home.setHome(id, 9, 2);
        assertTrue(home.hasHome(id));
        assertEquals(9, home.homeCellX(id));
        assertEquals(2, home.homeCellY(id));
    }

    @Test
    public void cellReadsAreFailLoudOnAPostlessOrUnknownId() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("m"));   // postless — no HOME component
        HomeService home = r.home();

        assertFalse(home.hasHome(id));
        assertThrows(IllegalArgumentException.class, () -> home.homeCellX(id));      // postless
        assertThrows(IllegalArgumentException.class, () -> home.homeCellY(999L));    // never allocated
    }
}
