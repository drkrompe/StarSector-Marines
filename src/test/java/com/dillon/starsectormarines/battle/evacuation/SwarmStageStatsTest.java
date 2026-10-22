package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A strain's growth-stage table and what {@link SwarmStrains} issues from it. */
class SwarmStageStatsTest {

    private static final UnitType[] STRAINS = {UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE, UnitType.SWARM_SPITTER};

    @Test
    void anAdultIsTheStrainAsItShipped() {
        for (UnitType type : STRAINS) {
            EntitySpec adult = SwarmStrains.spec("a", type, 0, 0);
            EntitySpec plain = new EntitySpec("a", adult.faction, type, 0, 0);
            assertEquals(plain.maxHp, adult.maxHp, type + " health");
            assertEquals(plain.attackDamage, adult.attackDamage, type + " damage");
            assertEquals(plain.attackRange, adult.attackRange, type + " reach");
            assertEquals(plain.moveSpeed, adult.moveSpeed, type + " speed");
            assertEquals(SwarmStage.ADULT, adult.swarmStage);
        }
        EntitySpec brute = SwarmStrains.spec("b", UnitType.SWARM_BRUTE, 0, 0);
        assertEquals(SwarmStrains.BRUTE_ARMOR, brute.maxArmor);
        assertEquals(SwarmStrains.BRUTE_ARMOR_RATING, brute.armorRating);
        EntitySpec matriarch = SwarmStrains.spec("m", UnitType.SWARM_MATRIARCH, 0, 0);
        assertEquals(UnitType.SWARM_MATRIARCH.maxHp, matriarch.maxHp);
        assertEquals(SwarmStrains.MATRIARCH_ARMOR, matriarch.maxArmor);
    }

    @Test
    void theMatriarchsStageFollowsHerMothersFeeding() {
        assertEquals(SwarmStage.WHELP, SwarmStageStats.matriarchStage(0));
        assertEquals(SwarmStage.ADULT, SwarmStageStats.matriarchStage(SwarmStageStats.MATRIARCH_ADULT_FED));
        assertEquals(SwarmStage.ELDER, SwarmStageStats.matriarchStage(SwarmStageStats.MATRIARCH_ELDER_FED));
        assertTrue(SwarmStageStats.of(UnitType.SWARM_MATRIARCH, SwarmStage.ELDER).hp()
                > SwarmStageStats.of(UnitType.SWARM_MATRIARCH, SwarmStage.WHELP).hp());
    }

    @Test
    void olderIsHarderAndSlower() {
        for (UnitType type : STRAINS) {
            SwarmStageStats.Stats previous = null;
            for (SwarmStage stage : SwarmStage.values()) {
                SwarmStageStats.Stats s = SwarmStageStats.of(type, stage);
                if (previous != null) {
                    assertTrue(s.hp() > previous.hp(), type + " " + stage + " health rises");
                    assertTrue(s.damage() > previous.damage(), type + " " + stage + " damage rises");
                    assertTrue(s.reach() > previous.reach(), type + " " + stage + " reach rises");
                    assertTrue(s.speed() < previous.speed(), type + " " + stage + " speed falls");
                    assertTrue(s.armor() >= previous.armor(), type + " " + stage + " plate never thins");
                }
                previous = s;
            }
        }
    }

    @Test
    void aSpawnCarriesItsStageAndItsStagesKit() {
        EntitySpec elder = SwarmStrains.spec("e", UnitType.SWARM_RUNNER, 0, 0, SwarmStage.ELDER);
        SwarmStageStats.Stats kit = SwarmStageStats.of(UnitType.SWARM_RUNNER, SwarmStage.ELDER);
        assertEquals(SwarmStage.ELDER, elder.swarmStage);
        assertEquals(kit.hp(), elder.maxHp);
        assertEquals(kit.hp(), elder.hp);
        assertEquals(kit.armor(), elder.maxArmor, "the pack leader's light plate");
    }

    @Test
    void aBrutesMomentumFollowsItsMass() {
        assertEquals(1f, SwarmStageStats.bruteMomentumRatio(SwarmStage.ADULT));
        assertTrue(SwarmStageStats.bruteMomentumRatio(SwarmStage.WHELP) < 1f);
        assertTrue(SwarmStageStats.bruteMomentumRatio(SwarmStage.ELDER) > 1f);
    }

    @Test
    void aWhelpNeverImpalesAndAnElderMostOften() {
        assertEquals(0f, SwarmStageStats.impaleChance(SwarmStage.WHELP));
        assertEquals(SwarmHarvest.IMPALE_CHANCE, SwarmStageStats.impaleChance(SwarmStage.ADULT));
        assertTrue(SwarmStageStats.impaleChance(SwarmStage.ELDER) > SwarmHarvest.IMPALE_CHANCE);
        assertEquals(SwarmHarvest.CARRIER_SPEED, SwarmStageStats.dragPace(SwarmStage.ADULT));
        assertTrue(SwarmStageStats.dragPace(SwarmStage.WHELP) < SwarmStageStats.dragPace(SwarmStage.ELDER));
    }
}
