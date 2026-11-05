package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The company deck stands the machines the roster has, as fitted: at muster, and again whenever a
 * screen showing her berths asks, without that screen having to be the Mech Lab.
 */
class CompanyDeckMachinesTest {

    @BeforeEach
    void freshStart() {
        LaidDecks.forget();
    }

    /** A small troop hull that still has machine berths. */
    private static final CompanyShip HULL = new CompanyShip(HullClass.DESTROYER, HullRole.TROOP_TRANSPORT, 8, 60, 20, 0.4f);

    private static final MechDeploymentSpec FITTED_BULWARK = new MechDeploymentSpec(
            MechVariant.BULWARK, MechRole.ARMORED_SUPPORT, MissileReplenisherComponent.ACCELERATED_FEED,
            MechWeaponComponent.PL_2_AP, MechWeaponComponent.SRM_15, MechWeaponComponent.LL_3);

    @Test
    void sheMustersTheLanceAsFittedAndFollowsTheRosterAfterwards() {
        AtomicReference<List<MechDeploymentSpec>> roster = new AtomicReference<>(List.of(FITTED_BULWARK));
        CompanyDeck deck = CompanyDeck.home(HULL, 42L, null, null, null, null)
                .fittedLance(roster::get);

        List<Long> mechs = mechs(deck.scene().simulation());
        assertEquals(1, mechs.size());
        assertEquals(MechWeaponComponent.LL_3, deck.scene().simulation().world()
                .mechLoadout(mechs.get(0)).mount(MechMountSlot.RIGHT_SHOULDER).component,
                "the mech was mustered in its stock fit");

        // Bought and fitted after she mustered: shown once a screen asks, not only by the Mech Lab.
        roster.set(List.of(FITTED_BULWARK, MechDeploymentSpec.standard(MechVariant.HOUND)));
        deck.syncMachines();

        mechs = mechs(deck.scene().simulation());
        assertEquals(2, mechs.size());
        assertEquals(MechVariant.HOUND, deck.scene().simulation().identity().mechVariant(mechs.get(1)));
    }

    private static List<Long> mechs(BattleSimulation simulation) {
        List<Long> mechs = new ArrayList<>();
        for (int i = 0; i < simulation.liveUnitCount(); i++) {
            long unit = simulation.liveUnitAt(i);
            if (simulation.identity().mechVariant(unit) != null) mechs.add(unit);
        }
        return mechs;
    }
}
