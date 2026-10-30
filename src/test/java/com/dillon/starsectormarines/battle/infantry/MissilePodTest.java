package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shoulder pod as a carried special ({@code special.srm-3-pod}), driven through a real
 * {@link BattleSimulation} because a delivered payload has to travel. Distances are derived from
 * the loaded item's authored standoff, never hard-coded balance numbers.
 */
class MissilePodTest {

    private static final int W = 48;
    private static final int H = 16;
    private static final int ROW = 8;
    private static final int CARRIER_X = 6;
    /** Long enough for a stationary pod to acquire and spend, short enough to stay quick. */
    private static final int OBSERVATION_TICKS = 240;
    /** Far past any authored salvo total, so the target outlasts the whole rack. */
    private static final float SURVIVES_THE_WHOLE_RACK = 1_000_000f;

    private static SpecialEquipmentDef pod() {
        return SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_3_POD_ID);
    }

    /** Cells past the authored standoff: a contact the pod's moment has arrived for. */
    private static int atStandoff() {
        return (int) Math.ceil(pod().missilePodSpec().minimumStandoffCells()) + 2;
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** One rifleman carrying the pod, in a squad holding its ground: an ordinary armed billet. */
    private static long carrier(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        EntitySpec spec = new EntitySpec("gunner", Faction.MARINE, UnitType.MARINE, CARRIER_X, ROW).squad(squadId);
        new MarineLoadout(UnitRole.COMBATANT, null, WeaponRegistry.STARTER_PRIMARY_ID,
                pod().id(), pod().startingAmmo()).seedInto(spec);
        long id = sim.spawn(spec.moveSpeed(0f));
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = 1;
        squad.aliveMembers = 1;
        sim.getSquadMoveOrderService().requestDefendArea(squadId, CARRIER_X, ROW);
        return id;
    }

    private static void hostile(BattleSimulation sim, int distance) {
        sim.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MILITIA, CARRIER_X + distance, ROW)
                .moveSpeed(0f).health(SURVIVES_THE_WHOLE_RACK));
    }

    @Test
    void aPodRunsDryAndStaysDry() {
        BattleSimulation sim = arena();
        long shooter = carrier(sim);
        hostile(sim, atStandoff());

        int ticks = 0;
        while (sim.world().secondaryAmmo(shooter) > 0 && ticks < 6000) {
            sim.advance(BattleSimulation.TICK_DT);
            ticks++;
        }
        assertEquals(0, sim.world().secondaryAmmo(shooter), "the pod should have run dry");

        int salvosWhenDry = sim.telemetry().secondaryUsed(shooter);
        for (int i = 0; i < 300; i++) sim.advance(BattleSimulation.TICK_DT);
        assertEquals(0, sim.world().secondaryAmmo(shooter), "the pod stays dry for the rest of the battle");
        assertEquals(salvosWhenDry, sim.telemetry().secondaryUsed(shooter), "a dry pod fires no further salvos");
    }

    /**
     * The rule the pod exists for as a secondary: a salvo never opens the hand's channel, so the
     * carrier keeps the primary up and keeps firing it through every launch.
     */
    @Test
    void firingThePodNeverTakesThePrimaryOutOfTheHands() {
        BattleSimulation sim = arena();
        long shooter = carrier(sim);
        hostile(sim, atStandoff());
        String podWeapon = pod().weaponId();
        int start = sim.world().secondaryAmmo(shooter);
        int primaryShotsAfterSalvo = 0;
        for (int tick = 0; tick < OBSERVATION_TICKS * 2; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            assertEquals(0f, sim.world().secondaryActionTimer(shooter),
                    "a shoulder pod must never start the hand's aim-then-fire channel");
            if (sim.world().secondaryAmmo(shooter) == start) continue;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == shooter && shot.primaryWeaponDef != null
                        && !podWeapon.equals(shot.primaryWeaponDef.id)) primaryShotsAfterSalvo++;
            }
        }
        assertTrue(sim.world().secondaryAmmo(shooter) < start, "fixture: the pod fired");
        assertTrue(primaryShotsAfterSalvo > 0, "the primary keeps firing once the pod has launched");
    }

    /** A contact already on top of the carrier is past the pod's moment; one at standoff is it. */
    @Test
    void aSightedContactAtStandoffIsThePodsMoment() {
        assertTrue(podSpendsASalvo(atStandoff(), 0), "a sighted contact at standoff is the pod's moment");
        assertFalse(podSpendsASalvo(2, 0), "a contact already on top of the carrier is past it");
    }

    /**
     * Fire discipline on a delivered payload: a friend between the rail and its target is where
     * the salvo would burst, so the pod holds and spends nothing; the same friend behind changes
     * nothing.
     */
    @Test
    void aFriendInTheLaneHoldsTheSalvo() {
        int hostile = atStandoff();
        assertFalse(podSpendsASalvo(hostile, hostile / 2), "a salvo through a friend's back is never spent");
        assertTrue(podSpendsASalvo(hostile, -3), "a friend clear of the lane does not hold it");
    }

    private static boolean podSpendsASalvo(int hostileDistance, int friendDistance) {
        BattleSimulation sim = arena();
        long gunner = carrier(sim);
        int start = sim.world().secondaryAmmo(gunner);
        hostile(sim, hostileDistance);
        if (friendDistance != 0) {
            sim.spawn(new EntitySpec("friend", Faction.MARINE, UnitType.MILITIA, CARRIER_X + friendDistance, ROW)
                    .moveSpeed(0f).hp(SURVIVES_THE_WHOLE_RACK).maxHp(SURVIVES_THE_WHOLE_RACK));
        }
        for (int i = 0; i < OBSERVATION_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
        return sim.world().secondaryAmmo(gunner) < start;
    }
}
