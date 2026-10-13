package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import java.util.Objects;

/** Per-person recovered issue; the distinct Boarding Jack pattern remains a later equipment task. */
public final class WaterUnderSealIssue {
    private WaterUnderSealIssue() {}
    public static EntitySpec recoveredAssaultRig(EntitySpec member, MarineArmorCatalogDef armor) {
        Objects.requireNonNull(armor, "Recovered Specter catalog must be installed");
        if (!"armor.specter-recovered".equals(armor.id())) throw new IllegalArgumentException("North Weir uses the recovered assault pattern");
        return member.armorClass(armor.armorClass()).layeredArmorFamily(armor.appearanceFamily())
                .armor(armor.armorCapacity(), armor.armorRating(), armor.moveSpeedMult(), armor.incomingAccuracyMult())
                .integralSystem(armor.integralSystem());
    }
}
