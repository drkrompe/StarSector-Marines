package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.InfantryPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarineInsertionTest {

    @Test
    public void valkyrieDeliversOneCommanderVisibleEightMarineSquad() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(30, 30));
        sim.spawn(new EntitySpec("battle-keeper", Faction.DEFENDER, UnitType.MILITIA, 26, 26)
                .health(10_000f).attackDamage(0f));
        MarineInsertion power = new MarineInsertion(new PrimaryCyclingRandom());
        Stockpile stockpile = new Stockpile(10);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertSame(InfantryPayload.INSTANCE, mission.payload);
        assertEquals(8, mission.marinesRemaining);
        assertNotNull(mission.marineLoadout);
        assertEquals(8, mission.marineLoadout.length);
        Set<MarineWeapon> manifestPrimaries = EnumSet.noneOf(MarineWeapon.class);
        int manifestRocketeers = 0;
        for (MarineLoadout loadout : mission.marineLoadout) {
            assertNotNull(loadout.primary, "every dropped ally must carry a real primary");
            manifestPrimaries.add(loadout.primary);
            if (loadout.secondary == MarineSecondary.ROCKET_LAUNCHER) manifestRocketeers++;
        }
        assertEquals(Set.of(MarineWeapon.SMG, MarineWeapon.DMR, MarineWeapon.PULSE_RIFLE),
                manifestPrimaries, "the injected roll must produce a visibly mixed squad");
        assertEquals(1, manifestRocketeers, "a full drop keeps the standard launcher specialist");
        assertEquals(1f + 0.5f * BattleSimulation.TICK_DT,
                sim.getCommandPowerService().getCommandPoints(), 0.001f);
        assertEquals(0, sim.getCommandPowerService().getChargesRemaining(power.id));
        assertEquals(8, stockpile.supplies);

        for (int i = 0; i < 1200 && mission.deboardedThisSortie < 8; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(8, mission.deboardedThisSortie);
        Squad squad = sim.getSquad(mission.squadId);
        assertNotNull(squad);
        assertEquals(8, squad.originalSize);
        assertTrue(squad.leaderId != 0L);
        int members = 0;
        Set<MarineWeapon> livePrimaries = EnumSet.noneOf(MarineWeapon.class);
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.squad().hasSquad(unit)
                    && sim.squad().squadId(unit) == squad.id) {
                members++;
                MarineWeapon primary = sim.combat().primaryWeapon(unit);
                assertNotNull(primary, "deboarded allies must not fall back to generic line fire");
                livePrimaries.add(primary);
            }
        }
        assertEquals(8, members);
        assertEquals(manifestPrimaries, livePrimaries);
    }

    /** Cycles only the 1-in-4 primary-family roll; stabilizes all other doctrine rolls. */
    private static final class PrimaryCyclingRandom extends Random {
        private int primary;

        @Override
        public int nextInt(int bound) {
            if (bound == 4) return primary++ % bound;
            return bound / 2;
        }
    }

    private static final class Stockpile implements CommandPowerResources {
        int supplies;

        Stockpile(int supplies) { this.supplies = supplies; }

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }
}
