package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.SquadMedicine;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.Arrays;
import java.util.List;

/**
 * Player-chosen command training, separate from recruitment traits and moral outlook. Only
 * specialties that change a fight are offered (owner, 2026-10-31); a retired one stays a
 * constant so older saves still load, and {@link OfficerCareer} refunds its points.
 */
public enum OfficerSpecialty {
    TACTICAL_COVER("openingOfficerSpecialtyTacticalCover", false),
    /** Retired: wounded recovery time. */
    RECOVERY_PLANNING("openingOfficerSpecialtyRecoveryPlanning", true),
    /** Retired: victory salvage. */
    SALVAGE_STEWARDSHIP("openingOfficerSpecialtySalvageStewardship", true),
    /** One designated medic in each of the officer's squads; see {@code SquadMedicSystem}. */
    FIELD_MEDICINE("openingOfficerSpecialtyFieldMedicine", false);

    private static final List<OfficerSpecialty> OFFERED =
            Arrays.stream(values()).filter(s -> !s.retired).toList();

    private final String label;
    private final boolean retired;
    OfficerSpecialty(String label, boolean retired) { this.label = label; this.retired = retired; }

    /** The specialties an officer can train, in the order the tree shows them. */
    public static List<OfficerSpecialty> offered() { return OFFERED; }
    public boolean retired() { return retired; }

    /** A rank as the tree names it: I, II, III. */
    public static String numeral(int rank) { return "I".repeat(rank); }

    public String label() { return Strings.get(label); }
    /** The catalog key of {@link #label()}, for text saved now and read in another language. */
    public String labelKey() { return label; }
    public String effect(int rank) {
        return switch (this) {
            case TACTICAL_COVER -> Strings.format("openingOfficerSpecialtyIncomingAccuracyForThisOfficerSDeployed", (5 * rank));
            case RECOVERY_PLANNING, SALVAGE_STEWARDSHIP -> "";
            case FIELD_MEDICINE -> medicineEffect(SquadMedicine.forRank(rank));
        };
    }
    private static String medicineEffect(SquadMedicine m) {
        if (m == null) return "";
        return Strings.format("openingOfficerSpecialtySquadMedicTreatments", trim(m.energyMax()),
                Math.round(m.energyMax() / m.energyRegenPerSecond()),
                trim(m.healthPerSecond()), trim(m.armorPerSecond()));
    }
    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString(Math.round(value)) : Float.toString(value);
    }
}
