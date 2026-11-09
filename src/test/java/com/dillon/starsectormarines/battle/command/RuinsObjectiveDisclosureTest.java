package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsObjectiveDisclosureTest {

    private static final RuinsVaultObjective VAULT = new RuinsVaultObjective("RUINS-01", "vault haul", "The vault",
            0, 30f, 10f, List.of(
            new RuinsVaultObjective.LiftPoint("Landing area", true, new int[]{10, 10, 8, 8, 6, 6, 4, 4, 2, 2}),
            new RuinsVaultObjective.LiftPoint("East court", false, new int[]{10, 10, 12, 10, 14, 10})));

    @Test
    void theCompanySeesItsWayInEveryLiftPointAndOnlyTheCachesItHasFound() {
        SalvageCache found = new SalvageCache("found", 5, 5, SalvageResource.METALS, 40);
        found.advance(0.1f, true, false, false);
        SalvageCache hidden = new SalvageCache("hidden", 7, 7, SalvageResource.METALS, 40);

        RuinsObjectiveFacts company = RuinsObjectiveDisclosure.facts(VAULT, Faction.MARINE, List.of(found, hidden));

        assertEquals(List.of(new RuinsObjectiveFacts.Cache("found", 5, 5)), company.caches());
        assertEquals(2, company.liftPoints().size());
        assertFalse(company.liftPoints().get(1).available(), "an unheld court is not yet a lift point");
        assertEquals(2, company.approachCellX(0), "the approach starts at the landing area");
        assertEquals(10, company.approachCellX(company.approachCellCount() - 1), "and ends at the vault");
    }

    @Test
    void theOccupantSeesItsVaultButNotTheCompanysWayOut() {
        RuinsObjectiveFacts occupant = RuinsObjectiveDisclosure.facts(VAULT, Faction.DEFENDER,
                List.of(new SalvageCache("stash", 5, 5, SalvageResource.METALS, 40)));

        assertEquals(10, occupant.vaultCellX());
        assertEquals("QUIET", occupant.phase());
        assertTrue(occupant.liftPoints().isEmpty());
        assertTrue(occupant.caches().isEmpty());
        assertEquals(-1, occupant.haulCellX());
        assertEquals(0, occupant.approachCellCount());
    }
}
