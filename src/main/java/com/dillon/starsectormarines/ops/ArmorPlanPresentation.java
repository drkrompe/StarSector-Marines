package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;

/** Saved intent remains readable when the company cannot currently issue its carrier class. */
final class ArmorPlanPresentation {
    private ArmorPlanPresentation() { }

    static String label(MarineArmory armory, String id) {
        SquadArmorDoctrine issued = armory.armorDoctrineById(id);
        if (issued != null) return issued.displayName();
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(id);
        if (plan != null) return Strings.format("armorPlanClassUnavailable", plan.displayName(), plan.armorClassDisplayName());
        return id == null ? Strings.get("armorPlanNone") : Strings.format("armorPlanUnavailable", id);
    }
}
