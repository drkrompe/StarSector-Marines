package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealIssueTest {
    @Test void recoveredRigBelongsToOnePersonAndKeepsItsCatalogLimits() {
        var ordinary = new EntitySpec("Salt Knife rifleman", Faction.DEFENDER, UnitType.MILITIA, 10, 10).squad(7);
        var wearer = new EntitySpec("Salt Knife recovered assault rig", Faction.DEFENDER, UnitType.MILITIA, 11, 10).squad(7);
        var recovered = MarineArmorCatalogRegistry.require("armor.specter-recovered");
        WaterUnderSealIssue.recoveredAssaultRig(wearer, recovered);
        assertEquals(ordinary.squadId, wearer.squadId);
        assertEquals(ArmorClass.STANDARD, ordinary.armorClass);
        assertEquals(0, ordinary.maxArmor);
        assertEquals(ArmorClass.POWER_SUIT, wearer.armorClass);
        assertEquals(recovered.armorCapacity(), wearer.maxArmor);
        assertEquals(recovered.armorRating(), wearer.armorRating);
        assertSame(recovered.integralSystem(), wearer.integralSystem);
        var military = MarineArmorCatalogRegistry.require("armor.specter-heavy");
        assertTrue(wearer.maxArmor < military.armorCapacity());
        assertTrue(wearer.armorRating < military.armorRating());
        assertThrows(IllegalArgumentException.class, () -> WaterUnderSealIssue.recoveredAssaultRig(ordinary, military));
    }
}
