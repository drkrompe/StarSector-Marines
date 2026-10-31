package com.dillon.starsectormarines.battle.mech;

/**
 * What the company ship's Mech Lab refits are worth to one deployed mech, frozen at launch
 * beside its {@link MechFrame}: fire-control calibration (weapon accuracy, and with the tactical
 * core link a faster torso traverse onto a new target), the coolant plant (flux dissipation) and
 * the armor shop (armor capacity, which every section's plate is a share of). Battle reads only
 * these numbers; which tiers the ship has bought stays campaign authority.
 *
 * <p>{@link #NEUTRAL} is every mech the company does not own, and every owned mech off the
 * founding hull.</p>
 */
public record MechRefit(float accuracyMult, float dissipationMult, float armorMult, float traverseMult) {

    public static final MechRefit NEUTRAL = new MechRefit(1f, 1f, 1f, 1f);

    public MechRefit {
        if (!(accuracyMult > 0f) || !Float.isFinite(accuracyMult)
                || !(dissipationMult > 0f) || !Float.isFinite(dissipationMult)
                || !(armorMult > 0f) || !Float.isFinite(armorMult)
                || !(traverseMult > 0f) || !Float.isFinite(traverseMult)) {
            throw new IllegalArgumentException("Mech refit multipliers must be finite and positive");
        }
    }

    public boolean neutral() { return equals(NEUTRAL); }

    /** The frame a mech deploys on: the fit's own values, with the shop's plate and the plant's coolant over them. */
    public MechFrame over(MechFrame frame) {
        if (neutral()) return frame;
        return new MechFrame(frame.armorCapacity() * armorMult, frame.moveSpeed(),
                frame.fluxCapacity(), frame.dissipationPerSecond() * dissipationMult, frame.systems());
    }
}
