package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.mech.MechLoadoutState;
import com.dillon.starsectormarines.battle.mech.MechRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the {@link HitResponseService#rollFallbackOnHit} gate that
 * skips GOAP-driven targets — every squad member, infantry or mech.
 * Infantry retreats via {@code SurviveContact}/{@code BreakContact};
 * mech squads run implacable with no flinch. Civilians (no-squad units)
 * keep the legacy roll — {@code FleeBehavior} depends on it.
 *
 * <p>Uses a real {@link BattleSimulation} for world setup but exercises the
 * extracted {@link HitResponseService} directly.
 */
public class HitResponseServiceTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 2; y < 10; y++) grid.setWalkable(7, y, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void squadMemberInfantrySkipsLegacyFallback() {
        BattleSimulation sim = openSim();
        HitResponseService hitResponse = sim.getHitResponseService();
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 3, 5);
        int sid = sim.mintSquad(Faction.MARINE, marine);
        marine.squadId = sid;
        sim.addUnit(marine);

        for (int i = 0; i < 100; i++) hitResponse.rollFallbackOnHit(marine);

        assertEquals(0f, sim.world().fallbackTimer(marine.entityId), 1e-6f,
                "GOAP-driven infantry must never enter the legacy fall-back state — morale owns retreat");
    }

    @Test
    public void squadMemberMechSkipsLegacyFallback() {
        BattleSimulation sim = openSim();
        HitResponseService hitResponse = sim.getHitResponseService();
        Entity mech = new Entity("mech0", Faction.DEFENDER, UnitType.HEAVY_MECH, 9, 5);
        int sid = sim.mintSquad(Faction.DEFENDER, mech);
        mech.squadId = sid;
        sim.addUnit(mech);
        sim.getMechLoadouts().add(mech.entityId, MechLoadoutState.defaultLoadout(MechRole.ARMORED_SUPPORT));
        sim.addUnit(new Entity("opp", Faction.MARINE, UnitType.MARINE, 11, 5));

        for (int i = 0; i < 100; i++) hitResponse.rollFallbackOnHit(mech);

        assertEquals(0f, sim.world().fallbackTimer(mech.entityId), 1e-6f,
                "mech-squad members must never enter the legacy fall-back — Stage 1 mechs are implacable");
    }

    @Test
    public void civilianKeepsLegacyFallback() {
        BattleSimulation sim = openSim();
        HitResponseService hitResponse = sim.getHitResponseService();
        Entity civilian = new Entity("c0", Faction.DEFENDER, UnitType.MARINE, 3, 8);
        civilian.squadId = Entity.NO_SQUAD;
        sim.addUnit(civilian);
        sim.addUnit(new Entity("opp", Faction.MARINE, UnitType.MARINE, 5, 8));

        boolean rolledAtLeastOnce = false;
        for (int i = 0; i < 100; i++) {
            sim.world().setFallbackTimer(civilian.entityId, 0f);
            hitResponse.rollFallbackOnHit(civilian);
            if (sim.world().fallbackTimer(civilian.entityId) > 0f) {
                rolledAtLeastOnce = true;
                break;
            }
        }
        assertTrue(rolledAtLeastOnce,
                "non-squad units must still roll the legacy fall-back (FleeBehavior depends on it)");
    }
}
