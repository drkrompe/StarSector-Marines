package com.dillon.starsectormarines.battle.warship;

/**
 * A warship's current job: the sticky value a {@link WarshipDoctrine} chooses
 * from a snapshot of the battle. It sets the parameters of the ordinary ship
 * machinery (director areas, an opportunity range, a flux ceiling, a station)
 * through a {@link PostureProfile}; it never fires a gun and never changes how
 * hard one hits.
 */
public enum WarshipPosture {
    /** Today's director: the densest hostile ground in reach. The default, and the control for every other posture. */
    BOMBARD("bombard"),
    /** Attacking: fire where the assault is, and avoid what would land on friendlies. */
    SUPPORT("support"),
    /** Defending: deny inbound drops at their landing zones, then cover the defended ground. */
    INTERCEPT("intercept"),
    /** A fight with something that can hurt the ship: conserve flux, shots of opportunity only. */
    AIR_SUPERIORITY("air-superiority"),
    /** Leaving the battle for good. */
    WITHDRAW("withdraw");

    private final String label;

    WarshipPosture(String label) {
        this.label = label;
    }

    /** One lower-case word for the HUD and the evidence. */
    public String label() {
        return label;
    }
}
