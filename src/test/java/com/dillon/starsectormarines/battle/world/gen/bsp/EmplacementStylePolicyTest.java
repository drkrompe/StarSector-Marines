package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Who builds what: militia digs in, a prepared garrison builds properly, each in its faction's tech family; pirates hold old works. */
class EmplacementStylePolicyTest {

    private static TargetProfile world(String faction, int defenseLevel) {
        return new TargetProfile(4, 4, defenseLevel, 0, faction, EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    @Test void militiaPreparedAndPirates() {
        assertEquals(EmplacementStyle.EARTHWORK, EmplacementStylePolicy.forDefenders(world("independent", 1)));
        assertEquals(EmplacementStyle.BRUTALIST, EmplacementStylePolicy.forDefenders(world("independent", 3)));
        assertEquals(EmplacementStyle.DERELICT, EmplacementStylePolicy.forDefenders(world("pirates", 3)));
        assertEquals(EmplacementStyle.BRUTALIST, EmplacementStylePolicy.forDefenders(null), "no market behind the battle");
    }

    @Test void factionsBuildInTheirTechFamily() {
        assertEquals(EmplacementStyle.MODULAR, EmplacementStylePolicy.forDefenders(world("persean", 3)));
        assertEquals(EmplacementStyle.BARRIER, EmplacementStylePolicy.forDefenders(world("persean", 1)));
        assertEquals(EmplacementStyle.EXPORT, EmplacementStylePolicy.forDefenders(world("sindrian_diktat", 3)));
        assertEquals(EmplacementStyle.BLOCKWORK, EmplacementStylePolicy.forDefenders(world("lions_guard", 1)));
        assertEquals(EmplacementStyle.COMPOSITE, EmplacementStylePolicy.forDefenders(world("tritachyon", 3)));
        assertEquals(EmplacementStyle.DEPLOYED, EmplacementStylePolicy.forDefenders(world("tritachyon", 1)));
        assertEquals(EmplacementStyle.BRUTALIST, EmplacementStylePolicy.forDefenders(world("hegemony", 3)), "low-tech");
    }
}
