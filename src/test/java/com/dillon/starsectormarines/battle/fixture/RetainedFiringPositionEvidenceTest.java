package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration of live validation and positive execution retention, not a unit test. */
@Tag("squad-firing-evidence")
class RetainedFiringPositionEvidenceTest {
    @Test void retainsLegalPositionThroughMotionAndPeriodicReplansButRepairsInvalidGeometry() {
        String old = System.getProperty("battle.targeting.retainFiringPositions");
        String oldShared = System.getProperty("battle.targeting.squadFiringPositions");
        TickInnerProfile previous = TickInnerProfile.currentIfBound();
        System.setProperty("battle.targeting.retainFiringPositions", "true");
        System.setProperty("battle.targeting.squadFiringPositions", "false");
        NavigationGrid grid = new NavigationGrid(40, 28);
        for (int y = 0; y < 28; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(40, 28))) {
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
            long self = sim.spawn(new EntitySpec("rifle", Faction.MARINE, UnitType.MARINE, 5, 14)
                    .squad(squadId).primaryWeapon(WeaponRegistry.installed().require(WeaponRegistry.STARTER_PRIMARY_ID)));
            long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 26, 14));
            var squad = sim.getSquad(squadId);
            sim.getUnitIndex().rebuild(sim.getRoster());
            sim.getDestIndex().rebuild(sim.getRoster());
            TacticalScoring scoring = sim.getTacticalScoring();
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            int[] initial = scoring.findSquadFiringPositionWithin(self, target, squad, 1, 14, 14, 10);
            assertNotNull(initial);
            for (int tick = 2; tick <= 250; tick++) {
                if (tick % 60 == 0) squad.routingEpoch++;
                sim.world().setPos(target, 26.5f + (tick % 3) * .1f, 14.5f);
                assertArrayEquals(initial, scoring.findSquadFiringPositionWithin(
                        self, target, squad, tick, 14, 14, 10));
            }
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.FIRING_RETAIN_SEARCH));
            assertEquals(249, profile.countOf(TickInnerProfile.Bucket.FIRING_RETAIN_HIT));
            // A hypothetical query is not an execution decision and cannot overwrite retention.
            scoring.findFiringPosition(self, target, initial[0], initial[1]);
            assertArrayEquals(initial, scoring.findSquadFiringPositionWithin(self, target, squad, 251, 14, 14, 10));
            grid.setWalkable(initial[0], initial[1], false);
            int[] repaired = scoring.findSquadFiringPositionWithin(self, target, squad, 252, 14, 14, 10);
            assertNotNull(repaired);
            assertFalse(repaired[0] == initial[0] && repaired[1] == initial[1]);
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.FIRING_RETAIN_SEARCH));
            // A changing leash must be checked even though it is not an expiry trigger by itself.
            int[] confined = scoring.findSquadFiringPositionWithin(self, target, squad, 253, 18, 14, 1);
            if (confined != null) assertTrue(Math.hypot(confined[0] - 18, confined[1] - 14) <= 1);
            System.out.println("Retained firing evidence: 250 unchanged legal selections; blocked-cell repair and live leash validation passed.");
        } finally {
            if (old == null) System.clearProperty("battle.targeting.retainFiringPositions");
            else System.setProperty("battle.targeting.retainFiringPositions", old);
            if (oldShared == null) System.clearProperty("battle.targeting.squadFiringPositions");
            else System.setProperty("battle.targeting.squadFiringPositions", oldShared);
            TickInnerProfile.setCurrent(previous);
        }
    }
}
