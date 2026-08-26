package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentPicker;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Set;

/** Selects at most one faction-authored template candidate for high-risk salvage. */
final class EquipmentTemplateRecoveryCatalog {

    private static final long SEED_SALT = 0x1ff2a7c67b93e451L;
    private static final float MANIFEST_WEIGHT = 8f;

    private EquipmentTemplateRecoveryCatalog() {}

    static List<LootCandidate> candidates(LootRollRequest request,
                                          Set<String> unavailableTemplateIds,
                                          int unitValue, float cargoPerUnit,
                                          String iconPath,
                                          EquipmentAcquisitionEligibility.Progress progress) {
        if (request == null || request.risk != RiskLevel.HIGH || request.entitlement <= 0
                || request.targetFactionId == null || unavailableTemplateIds == null
                || unitValue <= 0) {
            return List.of();
        }
        List<String> selected = FactionEquipmentPicker.pick(request.targetFactionId,
                FactionEquipmentSource.RECOVERY, 1,
                LootRoller.seedOf(request) ^ SEED_SALT, unavailableTemplateIds,
                progress);
        if (selected.isEmpty()) return List.of();
        EquipmentTemplateCard template = EquipmentTemplateCatalog.require(selected.get(0));
        return List.of(new LootCandidate(LootKind.SPECIAL, template.id(),
                template.displayName(), iconPath, unitValue, cargoPerUnit,
                MANIFEST_WEIGHT, 1, 1));
    }
}
