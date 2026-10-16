package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.MechPatternPicker;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Set;

/**
 * Selects at most one mech pattern card candidate for high-risk salvage, under the same rule
 * and recovery gate as {@link EquipmentTemplateRecoveryCatalog}'s loadout card.
 */
final class MechPatternRecoveryCatalog {

    private static final long SEED_SALT = 0x5a3c9e01d7b24f68L;
    private static final float MANIFEST_WEIGHT = 8f;

    private MechPatternRecoveryCatalog() {}

    static List<LootCandidate> candidates(LootRollRequest request,
                                          Set<String> unavailablePatternIds,
                                          int unitValue, float cargoPerUnit,
                                          String iconPath,
                                          EquipmentAcquisitionEligibility.Progress progress) {
        if (request == null || request.risk != RiskLevel.HIGH || request.entitlement <= 0
                || request.targetFactionId == null || unavailablePatternIds == null
                || unitValue <= 0) {
            return List.of();
        }
        List<String> selected = MechPatternPicker.pick(request.targetFactionId,
                FactionEquipmentSource.RECOVERY, 1,
                LootRoller.seedOf(request) ^ SEED_SALT, unavailablePatternIds, progress);
        if (selected.isEmpty()) return List.of();
        MechPattern pattern = MechPattern.parse(selected.get(0));
        return List.of(new LootCandidate(LootKind.SPECIAL, pattern.id(),
                pattern.displayName(), iconPath, unitValue, cargoPerUnit,
                MANIFEST_WEIGHT, 1, 1));
    }
}
