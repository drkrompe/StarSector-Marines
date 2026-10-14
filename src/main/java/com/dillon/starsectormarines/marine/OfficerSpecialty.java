package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.SquadMedicine;
import com.dillon.starsectormarines.i18n.Strings;
/** Player-chosen command training, separate from recruitment traits and moral outlook. */
public enum OfficerSpecialty {
    TACTICAL_COVER("openingOfficerSpecialtyTacticalCover"),
    RECOVERY_PLANNING("openingOfficerSpecialtyRecoveryPlanning"),
    SALVAGE_STEWARDSHIP("openingOfficerSpecialtySalvageStewardship"),
    /** One designated medic in each of the officer's squads; see {@code SquadMedicSystem}. */
    FIELD_MEDICINE("openingOfficerSpecialtyFieldMedicine");

    private final String label;
    OfficerSpecialty(String label) { this.label = label; }
    public String label() { return Strings.get(label); }
    public String effect(int rank) {
        return switch (this) {
            case TACTICAL_COVER -> Strings.format("openingOfficerSpecialtyIncomingAccuracyForThisOfficerSDeployed", (5 * rank));
            case RECOVERY_PLANNING -> Strings.format("openingOfficerSpecialtyWoundedRecoveryTimeForThisOfficerS", (15 * rank));
            case SALVAGE_STEWARDSHIP -> Strings.format("openingOfficerSpecialtyVictorySalvageRecoveryPercentagePointsStrongestParticipating", (10 * rank));
            case FIELD_MEDICINE -> medicineEffect(SquadMedicine.forRank(rank));
        };
    }
    private static String medicineEffect(SquadMedicine m) {
        if (m == null) return "";
        return Strings.format("openingOfficerSpecialtySquadMedicTreatments", m.supplies(),
                Math.round(m.supplyRecoverySeconds()),
                trim(m.healthPerSecond() * m.channelSeconds()),
                trim(m.armorPerSecond() * m.channelSeconds()),
                Math.round(m.channelSeconds()));
    }
    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString(Math.round(value)) : Float.toString(value);
    }
}
