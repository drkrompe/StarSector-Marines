package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechTurretSystemTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    @Test
    void torsoTraversesAtItsConfiguredRateAndBlocksFireUntilAligned() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE, UnitType.MARINE, 7, 8));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        loadout.torsoFacingDegrees = 0f;
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        sim.world().attachMechLoadout(mech, loadout);
        sim.world().setTargetId(mech, target);
        MechTurretSystem turrets = new MechTurretSystem(
                sim.getEntityWorld(), c, sim.getRoster());

        turrets.tick(BattleSimulation.TICK_DT);

        assertEquals(-0.3333f, loadout.torsoFacingDegrees, 0.001f,
                "the torso accelerates into its traverse instead of snapping to full speed");
        assertEquals(-10f, loadout.torsoAngularVelocityDegrees, 0.001f);
        assertFalse(loadout.isAimedAt(target));
        MechCombatantBehavior.tryFireChaingun(mech, loadout, target, 3.7f, sim, true);
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        assertEquals(0f, arms.cooldown, 0.001f,
                "a weapon must wait for the torso to traverse onto its target");

        for (int i = 0; i < 60 && !loadout.isAimedAt(target); i++) {
            turrets.tick(BattleSimulation.TICK_DT);
        }

        assertTrue(loadout.isAimedAt(target));
        MechCombatantBehavior.tryFireChaingun(mech, loadout, target, 3.7f, sim, true);
        assertEquals(arms.weaponDef().cooldown, arms.cooldown, 0.001f);
    }

    @Test
    void rememberedContactOverridesRouteLookAheadWhenNoTargetIsActive() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 5, 5).squad(squadId));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(
                MechRole.ARMORED_SUPPORT);
        loadout.torsoFacingDegrees = 0f;
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        sim.world().attachMechLoadout(mech, loadout);
        sim.setPath(mech, new int[]{5, 5, 6, 5, 7, 5, 7, 6, 7, 7});
        sim.getSquad(squadId).contactPicture = rememberedContact(77L, 2, 5);

        new MechTurretSystem(sim.getEntityWorld(), c, sim.getRoster())
                .tick(BattleSimulation.TICK_DT);

        assertEquals(0.3333f, loadout.torsoFacingDegrees, 0.001f,
                "the westward remembered contact wins over the eastward route horizon");
        assertEquals(0L, loadout.torsoAimTargetId,
                "remembered intent must not masquerade as a legal fire target");
        assertFalse(loadout.torsoOnTarget);
    }

    @Test
    void routeLookAheadTurnsTheTorsoTowardAnUpcomingBend() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 5, 5));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(
                MechRole.ARMORED_SUPPORT);
        loadout.torsoFacingDegrees = 0f;
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        sim.world().attachMechLoadout(mech, loadout);
        sim.setPath(mech, new int[]{5, 5, 6, 5, 7, 5, 7, 6, 7, 7});

        new MechTurretSystem(sim.getEntityWorld(), c, sim.getRoster())
                .tick(BattleSimulation.TICK_DT);

        assertEquals(-0.3333f, loadout.torsoFacingDegrees, 0.001f,
                "the upper chassis begins looking east along the route horizon");
        assertEquals(0L, loadout.torsoAimTargetId);
        assertFalse(loadout.torsoOnTarget);
    }

    @Test
    void activeTargetOverridesRememberedContactAndRouteLookAhead() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 5, 5).squad(squadId));
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE,
                UnitType.MARINE, 5, 2));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(
                MechRole.ARMORED_SUPPORT);
        loadout.torsoFacingDegrees = 0f;
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        sim.world().attachMechLoadout(mech, loadout);
        sim.world().setTargetId(mech, target);
        sim.setPath(mech, new int[]{5, 5, 6, 5, 7, 5, 7, 6, 7, 7});
        sim.getSquad(squadId).contactPicture = rememberedContact(77L, 2, 5);

        new MechTurretSystem(sim.getEntityWorld(), c, sim.getRoster())
                .tick(BattleSimulation.TICK_DT);

        assertEquals(-0.3333f, loadout.torsoFacingDegrees, 0.001f,
                "the southward active target wins over the westward memory");
        assertEquals(target, loadout.torsoAimTargetId);
    }

    @Test
    void liveManualCursorOwnsTraverseEvenWhileDifferentMountsRetainPointBursts() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 20), null);
        long mech = roster.spawn(new EntitySpec("manual-bulwark", Faction.MARINE,
                UnitType.HEAVY_MECH, 5, 5).mechVariant(MechVariant.BULWARK));
        MechLoadoutComponent loadout = MechVariant.BULWARK.createLoadout(MechRole.BALANCED);
        roster.world().attachMechLoadout(mech, loadout);
        roster.entityWorld().setFloat(mech, roster.components().MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        loadout.torsoFacingDegrees = 0f;
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        arms.burstRemaining = 11;
        arms.burstPointAim = aimAt(-90f);
        MechWeaponMount shoulder = loadout.mount(MechMountSlot.LEFT_SHOULDER);
        shoulder.burstRemaining = 3;
        shoulder.burstPointAim = aimAt(90f);
        MechTurretSystem turrets = new MechTurretSystem(roster.entityWorld(), roster.components(), roster);

        turrets.tick(BattleSimulation.TICK_DT, mech, aimAt(30f));

        assertEquals(.3333f, loadout.torsoFacingDegrees, .001f,
                "the first turn follows the live cursor despite the older arm commitment");
        for (int tick = 0; tick < 45; tick++) turrets.tick(BattleSimulation.TICK_DT, mech, aimAt(30f));
        assertEquals(30f, loadout.torsoFacingDegrees, .001f);
        for (int tick = 0; tick < 60; tick++) turrets.tick(BattleSimulation.TICK_DT, mech, aimAt(-30f));
        assertEquals(-30f, loadout.torsoFacingDegrees, .001f,
                "a changed cursor takes ownership without waiting for either burst to finish");
        assertEquals(11, arms.burstRemaining, "traverse never edits weapon schedules");
        assertEquals(3, shoulder.burstRemaining);
        assertEquals(0L, loadout.torsoAimTargetId);
    }

    @Test
    void rearSideChangesTraverseThroughTheFrontWithBoundedSpeedAndAcceleration() {
        float dt = BattleSimulation.TICK_DT;
        for (int sign : new int[]{-1, 1}) {
            float facing = sign * 144f, velocity = 0f;
            boolean crossedFront = false;
            for (int tick = 0; tick < 150; tick++) {
                var turn = MechTurretSystem.traverseWithinHips(facing, velocity, 0f, -sign * 144f, 120f, dt);
                float applied = LayeredAppearance.wrapDegrees(turn.facingDegrees() - facing);
                assertTrue(Math.abs(turn.facingDegrees()) <= 145f);
                assertTrue(Math.abs(applied) <= 120f * dt + .0001f);
                if (turn.facingDegrees() != -sign * 144f) {
                    assertTrue(Math.abs(turn.angularVelocityDegrees() - velocity) <= 300f * dt + .0001f);
                }
                if (tick == 0) {
                    assertEquals(-sign * 10f, turn.angularVelocityDegrees(), .001f);
                    assertTrue(sign * applied < 0f, "turn inward instead of taking the rear shortcut");
                }
                crossedFront |= Math.abs(turn.facingDegrees()) < 4f;
                facing = turn.facingDegrees();
                velocity = turn.angularVelocityDegrees();
            }
            assertTrue(crossedFront);
            assertEquals(-sign * 144f, facing, .001f);
            assertEquals(0f, velocity);
        }
    }

    @Test
    void mechanicalStopsContainResidualMomentumAndFollowAMovingHipFrame() {
        var arrested = MechTurretSystem.traverseWithinHips(144f, 120f, 0f, -144f,
                120f, BattleSimulation.TICK_DT);
        assertEquals(145f, arrested.facingDegrees());
        assertEquals(0f, arrested.angularVelocityDegrees());
        var movedHips = MechTurretSystem.traverseWithinHips(145f, 0f, -10f, 145f,
                120f, BattleSimulation.TICK_DT);
        assertEquals(135f, movedHips.facingDegrees(), .001f,
                "hip rotation carries the torso stop rather than exposing an illegal rear pose");
        assertEquals(145f, LayeredAppearance.wrapDegrees(movedHips.facingDegrees() + 10f), .001f);
        for (int sign : new int[]{-1, 1}) {
            // The hips crossed from +/-170 to -/+179, carrying a torso that
            // was already at the opposite stop. Numeric angle wrap is no jump.
            var wrappedHips = MechTurretSystem.traverseWithinHips(sign * 25f, 0f,
                    -sign * 179f, sign * 25f, 120f, BattleSimulation.TICK_DT);
            assertEquals(sign * 36f, wrappedHips.facingDegrees(), .001f);
            assertEquals(-sign * 145f, LayeredAppearance.wrapDegrees(
                    wrappedHips.facingDegrees() + sign * 179f), .001f);
        }
    }

    private static PointFireAim aimAt(float facing) {
        double radians = Math.toRadians(facing);
        return new PointFireAim(5.5f - 10f * (float) Math.sin(radians),
                5.5f + 10f * (float) Math.cos(radians));
    }

    private static SquadContactPicture rememberedContact(long id, int cellX, int cellY) {
        return new SquadContactPicture(1, SquadContactPicture.Posture.UNCOMMITTED,
                0f, 0f, 1, 0, 1f, 1,
                SquadContactPicture.ForceBalance.EVEN,
                SquadContactPicture.Sector.FRONT,
                SquadContactPicture.Motion.UNKNOWN,
                id, cellX, cellY, 0.5f,
                SquadContactPicture.Doctrine.HOLD,
                0, 1, 0, 1,
                SquadContactPicture.ContactInitiative.NONE);
    }
}
