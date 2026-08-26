package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.Objects;

/** Immutable campaign-owned facts for one ordered marine deployment seat. */
public record MarineSeatCommitment(
        String campaignSoldierId,
        String primaryWeaponId,
        EquipmentGrade equipmentGrade,
        SoldierProfile soldierProfile,
        String specialEquipmentId,
        LayeredArmorFamily armorFamily,
        float armorPool,
        float armorRating,
        float armorMoveSpeedMult,
        float armorIncomingAccuracyMult,
        String campaignSquadId,
        String campaignSquadLabel,
        boolean campaignSquadLeader,
        int campaignSquadStrength,
        int campaignFireTeamIndex) {

    public MarineSeatCommitment {
        if (primaryWeaponId == null || primaryWeaponId.isBlank()) {
            throw new IllegalArgumentException("A primary weapon id is required");
        }
        equipmentGrade = Objects.requireNonNull(equipmentGrade, "equipmentGrade");
        soldierProfile = Objects.requireNonNull(soldierProfile, "soldierProfile");
        requireFiniteNonNegative(armorPool, "armorPool");
        requireFiniteNonNegative(armorRating, "armorRating");
        requireFinitePositive(armorMoveSpeedMult, "armorMoveSpeedMult");
        requireFinitePositive(armorIncomingAccuracyMult,
                "armorIncomingAccuracyMult");
        if (campaignSquadId != null) {
            if (campaignSquadId.isBlank()
                    || campaignSquadLabel == null || campaignSquadLabel.isBlank()) {
                throw new IllegalArgumentException(
                        "A campaign squad id and label must travel together");
            }
            if (campaignSquadStrength < 1) {
                throw new IllegalArgumentException(
                        "A campaign squad strength must be positive");
            }
        }
        if (campaignFireTeamIndex < Squad.NO_FIRE_TEAM) {
            throw new IllegalArgumentException("Invalid fire-team index");
        }
    }

    /** Captures only campaign-owned identity and equipment, not scenario orders. */
    public static MarineSeatCommitment capture(MarineLoadout loadout) {
        Objects.requireNonNull(loadout, "loadout");
        String primaryWeaponId = loadout.primaryWeaponId;
        if (primaryWeaponId == null) {
            throw new IllegalArgumentException(
                    "A committed marine seat requires a catalog primary weapon id");
        }
        WeaponRegistry.require(primaryWeaponId);

        String specialEquipmentId = loadout.specialEquipmentId;
        if (specialEquipmentId != null) {
            SpecialEquipmentRegistry.require(specialEquipmentId);
        }

        CampaignSquadTag squad = loadout.campaignSquad;
        return new MarineSeatCommitment(
                loadout.campaignSoldierId,
                primaryWeaponId,
                loadout.equipmentGrade,
                loadout.soldierProfile,
                specialEquipmentId,
                loadout.armorFamily,
                loadout.armorPool,
                loadout.armorRating,
                loadout.armorMoveSpeedMult,
                loadout.armorIncomingAccuracyMult,
                squad != null ? squad.squadId : null,
                squad != null ? squad.label : null,
                squad != null && squad.leader,
                squad != null ? squad.strength : 0,
                squad != null ? squad.fireTeamIndex : Squad.NO_FIRE_TEAM);
    }

    /**
     * Resolves stable catalog ids into a battle loadout. The scenario-specific
     * role and objective are deliberately neutral here; deployment merges this
     * loadout into the scenario-authored seat through its existing seam.
     */
    public MarineLoadout toLoadout() {
        CampaignSquadTag squad = campaignSquadId != null
                ? new CampaignSquadTag(campaignSquadId, campaignSquadLabel,
                campaignSquadLeader, campaignSquadStrength, campaignFireTeamIndex)
                : null;
        return MarineLoadout.fromCatalog(
                UnitRole.COMBATANT,
                null,
                WeaponRegistry.require(primaryWeaponId),
                equipmentGrade,
                soldierProfile,
                specialEquipmentId != null
                        ? SpecialEquipmentRegistry.require(specialEquipmentId) : null,
                campaignSoldierId,
                armorFamily,
                armorPool,
                armorRating,
                armorMoveSpeedMult,
                armorIncomingAccuracyMult,
                squad);
    }

    private static void requireFiniteNonNegative(float value, String field) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    private static void requireFinitePositive(float value, String field) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }
}
