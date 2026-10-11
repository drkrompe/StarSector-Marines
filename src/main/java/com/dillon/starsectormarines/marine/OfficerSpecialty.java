package com.dillon.starsectormarines.marine;

/** Player-chosen command training, separate from recruitment traits and moral outlook. */
public enum OfficerSpecialty {
    TACTICAL_COVER("Tactical Cover"),
    RECOVERY_PLANNING("Recovery Planning"),
    SALVAGE_STEWARDSHIP("Salvage Stewardship");

    private final String label;
    OfficerSpecialty(String label) { this.label = label; }
    public String label() { return label; }
    public String effect(int rank) {
        return switch (this) {
            case TACTICAL_COVER -> "Incoming accuracy -" + (5 * rank) + "% for this officer's deployed marines.";
            case RECOVERY_PLANNING -> "Wounded recovery time -" + (15 * rank) + "% for this officer's deployed marines.";
            case SALVAGE_STEWARDSHIP -> "Victory salvage recovery +" + (10 * rank) + " percentage points. Strongest participating specialist only.";
        };
    }
}
