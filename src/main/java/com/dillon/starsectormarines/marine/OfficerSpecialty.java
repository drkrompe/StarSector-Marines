package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
/** Player-chosen command training, separate from recruitment traits and moral outlook. */
public enum OfficerSpecialty {
    TACTICAL_COVER("openingOfficerSpecialtyTacticalCover"),
    RECOVERY_PLANNING("openingOfficerSpecialtyRecoveryPlanning"),
    SALVAGE_STEWARDSHIP("openingOfficerSpecialtySalvageStewardship");

    private final String label;
    OfficerSpecialty(String label) { this.label = label; }
    public String label() { return Strings.get(label); }
    public String effect(int rank) {
        return switch (this) {
            case TACTICAL_COVER -> Strings.format("openingOfficerSpecialtyIncomingAccuracyForThisOfficerSDeployed", (5 * rank));
            case RECOVERY_PLANNING -> Strings.format("openingOfficerSpecialtyWoundedRecoveryTimeForThisOfficerS", (15 * rank));
            case SALVAGE_STEWARDSHIP -> Strings.format("openingOfficerSpecialtyVictorySalvageRecoveryPercentagePointsStrongestParticipating", (10 * rank));
        };
    }
}
