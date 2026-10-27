package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.List;

/**
 * One part a yard can put in a ship's boat, and what it costs: in materials to
 * build, and in weight against the boat's frame ({@link BoatFrame}).
 *
 * <p>A code catalog, the shape {@link
 * com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent} uses:
 * authored entries with stable ids, resolved by id everywhere a boat is
 * persisted. Each slot has a yard-standard tier 1 part, which every boat is
 * built with and which weighs nothing, and upgrade tiers above it. Every part,
 * the stock one too, is a real thing: taken off, it goes into the boat deck's
 * stores as a spare, and fitting one takes a spare out ({@link BoatWorkshop}).
 *
 * <p>The tiers are built around the Hermes, the company's boat: tier 1 is the
 * Hermes as the base game builds her, and every factor here is a step from her
 * own numbers ({@code company-view-nouns.md}, Fitting).
 *
 * @param effect what the part does to the boat it is on
 * @param weight what it takes out of the frame's rating; zero for a standard part
 * @param bill what the yard charges, in credits, to build one spare
 * @param provenance where the thing comes from, for the player reading the card
 */
public record BoatFitting(
        String id,
        String displayName,
        BoatFittingSlot slot,
        int tier,
        Effect effect,
        int weight,
        FabricationCost bill,
        String provenance) {

    /**
     * What a part does. Only the figures its slot is about differ from
     * {@link #NONE}: a factor of 1, a lift of none, a seat count, shield arc of
     * zero (the pattern's own) and so on.
     *
     * @param deboardFactor scales the seconds each marine takes to get off
     * @param seats the marines a sortie lands; zero leaves the pattern's own
     * @param shieldArcDeg the shield's arc; zero leaves the hull's own
     * @param shieldEfficiency scales the flux a blocked point of damage costs
     * @param fluxCapacityFactor scales the hull's flux capacity
     * @param fluxDissipationFactor scales the hull's flux dissipation
     */
    public record Effect(float armorFactor, float speedFactor, float accelFactor, float deboardFactor,
                         LiftRating liftRating, int seats, float shieldArcDeg, float shieldEfficiency,
                         float fluxCapacityFactor, float fluxDissipationFactor) {

        public static final Effect NONE = new Effect(1f, 1f, 1f, 1f, LiftRating.NONE, 0, 0f, 1f, 1f, 1f);

        public Effect {
            requirePositive(armorFactor, "armor");
            requirePositive(speedFactor, "speed");
            requirePositive(accelFactor, "acceleration");
            requirePositive(deboardFactor, "deboard");
            requirePositive(shieldEfficiency, "shield efficiency");
            requirePositive(fluxCapacityFactor, "flux capacity");
            requirePositive(fluxDissipationFactor, "flux dissipation");
            if (liftRating == null) throw new IllegalArgumentException("Boat fitting lift rating is required");
            if (seats < 0) throw new IllegalArgumentException("Boat fitting seats cannot be negative: " + seats);
            if (!(shieldArcDeg >= 0f && shieldArcDeg <= 360f)) {
                throw new IllegalArgumentException("Boat fitting shield arc must be 0..360: " + shieldArcDeg);
            }
        }

        static Effect armor(float factor) {
            return new Effect(factor, 1f, 1f, 1f, LiftRating.NONE, 0, 0f, 1f, 1f, 1f);
        }

        static Effect drive(float speed, float deboard) {
            return new Effect(1f, speed, speed, deboard, LiftRating.NONE, 0, 0f, 1f, 1f, 1f);
        }

        static Effect lift(LiftRating rating) {
            return new Effect(1f, 1f, 1f, 1f, rating, 0, 0f, 1f, 1f, 1f);
        }

        static Effect emitter(float arcDeg, float efficiency) {
            return new Effect(1f, 1f, 1f, 1f, LiftRating.NONE, 0, arcDeg, efficiency, 1f, 1f);
        }

        static Effect power(float capacity, float dissipation) {
            return new Effect(1f, 1f, 1f, 1f, LiftRating.NONE, 0, 0f, 1f, capacity, dissipation);
        }

        private static void requirePositive(float factor, String what) {
            if (!(factor > 0f) || !Float.isFinite(factor)) {
                throw new IllegalArgumentException(
                        "Boat fitting " + what + " factor must be finite and positive: " + factor);
            }
        }
    }

    public static final BoatFitting STANDARD_PLATING = new BoatFitting(
            "standard_plating", "@openingBoatFittingYardStandardPlating", BoatFittingSlot.PLATING, 1,
            Effect.NONE, 0, credits(800), "@openingBoatFittingWhatABoatLeavesTheYardWith");

    public static final BoatFitting REINFORCED_PLATING = new BoatFitting(
            "reinforced_plating", "@openingBoatFittingReinforcedBellyPlate", BoatFittingSlot.PLATING, 2,
            Effect.armor(1.35f), 2, credits(1600), "@openingBoatFittingASecondSkinLaidOverTheBelly");

    public static final BoatFitting ARMOURED_PLATING = new BoatFitting(
            "armoured_plating", "@openingBoatFittingArmouredLanderShell", BoatFittingSlot.PLATING, 3,
            Effect.armor(1.75f), 3, credits(3950), "@openingBoatFittingAssaultLanderPlateLaminateOverTheCrew");

    public static final BoatFitting STANDARD_DRIVE = new BoatFitting(
            "standard_drive", "@openingBoatFittingYardStandardDrive", BoatFittingSlot.DRIVE, 1,
            Effect.NONE, 0, credits(1250), "@openingBoatFittingThePlantTheHullWasRatedFor");

    public static final BoatFitting TUNED_DRIVE = new BoatFitting(
            "tuned_drive", "@openingBoatFittingTunedDrive", BoatFittingSlot.DRIVE, 2,
            Effect.drive(1.20f, 0.80f), 2, credits(2250), "@openingBoatFittingARefitCrewSWeekCleanFeed");

    public static final BoatFitting UPRATED_DRIVE = new BoatFitting(
            "uprated_drive", "@openingBoatFittingUpratedDrive", BoatFittingSlot.DRIVE, 3,
            Effect.drive(1.45f, 0.60f), 4, credits(5500), "@openingBoatFittingABiggerPlantThanTheHullWas");

    public static final BoatFitting STANDARD_LIFT = new BoatFitting(
            "standard_lift", "@boatFittingStandardHold", BoatFittingSlot.LIFT, 1,
            Effect.NONE, 0, credits(800), "@boatFittingStandardHoldProvenance");

    public static final BoatFitting LIFT_CRADLE = new BoatFitting(
            "lift_cradle", "@boatFittingLiftCradle", BoatFittingSlot.LIFT, 2,
            Effect.lift(LiftRating.LIGHT), 2, credits(3900), "@boatFittingLiftCradleProvenance");

    public static final BoatFitting HEAVY_CRADLE = new BoatFitting(
            "heavy_cradle", "@boatFittingHeavyCradle", BoatFittingSlot.LIFT, 3,
            Effect.lift(LiftRating.MEDIUM), 3, credits(8800), "@boatFittingHeavyCradleProvenance");

    public static final BoatFitting HEAVY_LIFT_FRAME = new BoatFitting(
            "heavy_lift_frame", "@boatFittingHeavyLiftFrame", BoatFittingSlot.LIFT, 4,
            Effect.lift(LiftRating.HEAVY), 5, credits(13700), "@boatFittingHeavyLiftFrameProvenance");

    public static final BoatFitting STANDARD_CABIN = new BoatFitting(
            "standard_cabin", "@boatFittingStandardCabin", BoatFittingSlot.CABIN, 1,
            Effect.NONE, 0, credits(950), "@boatFittingStandardCabinProvenance");

    public static final BoatFitting STANDARD_EMITTER = new BoatFitting(
            "standard_emitter", "@boatFittingStandardEmitter", BoatFittingSlot.EMITTER, 1,
            Effect.NONE, 0, credits(1250), "@boatFittingStandardEmitterProvenance");

    public static final BoatFitting WIDE_EMITTER = new BoatFitting(
            "wide_emitter", "@boatFittingWideEmitter", BoatFittingSlot.EMITTER, 2,
            Effect.emitter(180f, 0.8f), 1, credits(3500), "@boatFittingWideEmitterProvenance");

    public static final BoatFitting ASSAULT_EMITTER = new BoatFitting(
            "assault_emitter", "@boatFittingAssaultEmitter", BoatFittingSlot.EMITTER, 3,
            Effect.emitter(240f, 0.6f), 2, credits(6250), "@boatFittingAssaultEmitterProvenance");

    public static final BoatFitting STANDARD_POWER = new BoatFitting(
            "standard_power", "@boatFittingStandardPower", BoatFittingSlot.POWER, 1,
            Effect.NONE, 0, credits(950), "@boatFittingStandardPowerProvenance");

    public static final BoatFitting CAPACITOR_BANK = new BoatFitting(
            "capacitor_bank", "@boatFittingCapacitorBank", BoatFittingSlot.POWER, 2,
            Effect.power(1.5f, 1f), 1, credits(3050), "@boatFittingCapacitorBankProvenance");

    public static final BoatFitting MILITARY_CAPACITORS = new BoatFitting(
            "military_capacitors", "@boatFittingMilitaryCapacitors", BoatFittingSlot.POWER, 3,
            Effect.power(1.75f, 1.5f), 2, credits(6100), "@boatFittingMilitaryCapacitorsProvenance");

    private static final List<BoatFitting> CATALOG = List.of(
            STANDARD_PLATING, REINFORCED_PLATING, ARMOURED_PLATING,
            STANDARD_DRIVE, TUNED_DRIVE, UPRATED_DRIVE,
            STANDARD_LIFT, LIFT_CRADLE, HEAVY_CRADLE, HEAVY_LIFT_FRAME,
            STANDARD_CABIN,
            STANDARD_EMITTER, WIDE_EMITTER, ASSAULT_EMITTER,
            STANDARD_POWER, CAPACITOR_BANK, MILITARY_CAPACITORS);

    public String displayName() { return Strings.resolve(displayName); }
    public String provenance() { return Strings.resolve(provenance); }

    public BoatFitting {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Boat fitting id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Boat fitting display name is required");
        }
        if (slot == null) throw new IllegalArgumentException("Boat fitting slot is required");
        if (tier < 1 || tier > 4) {
            throw new IllegalArgumentException("Boat fitting tier must be 1..4: " + tier);
        }
        if (effect == null) throw new IllegalArgumentException("Boat fitting effect is required");
        if (weight < 0) throw new IllegalArgumentException("Boat fitting weight cannot be negative: " + weight);
        if (effect.liftRating() != LiftRating.NONE && slot != BoatFittingSlot.LIFT) {
            throw new IllegalArgumentException("Only a LIFT fitting can carry a lift rating: " + id);
        }
        if (bill == null) throw new IllegalArgumentException("Boat fitting bill is required: " + id);
        if (tier == 1 && weight != 0) {
            throw new IllegalArgumentException("A standard fitting weighs nothing: " + id);
        }
    }

    public float armorFactor() { return effect.armorFactor(); }
    public float speedFactor() { return effect.speedFactor(); }
    public float accelFactor() { return effect.accelFactor(); }
    public float deboardFactor() { return effect.deboardFactor(); }
    public LiftRating liftRating() { return effect.liftRating(); }
    public int seats() { return effect.seats(); }
    public float shieldArcDeg() { return effect.shieldArcDeg(); }
    public float shieldEfficiency() { return effect.shieldEfficiency(); }
    public float fluxCapacityFactor() { return effect.fluxCapacityFactor(); }
    public float fluxDissipationFactor() { return effect.fluxDissipationFactor(); }

    /** Whether this is the fitting a boat is built with rather than refitted to. */
    public boolean standard() {
        return tier == 1;
    }

    /**
     * Whether a boat of {@code pattern} can take this fitting. Every boat takes
     * every part except a lift that does not lift more than her pattern already
     * does: a Valkyrie gains nothing from a cradle.
     */
    public boolean supports(ShuttleType pattern) {
        if (pattern == null) return false;
        if (standard() || slot != BoatFittingSlot.LIFT) return true;
        return liftRating().ordinal() > pattern.liftRating().ordinal();
    }

    /**
     * Whether this hold lifts a machine. The seats stay, so the boat still
     * ferries squads; one sortie carries one kind or the other.
     */
    public boolean cradle() {
        return liftRating() != LiftRating.NONE;
    }

    /** Every authored fitting, in slot then tier order. */
    public static List<BoatFitting> catalog() {
        return CATALOG;
    }

    /** Everything that can go in one slot, cheapest first. */
    public static List<BoatFitting> catalog(BoatFittingSlot slot) {
        List<BoatFitting> found = new ArrayList<>();
        for (BoatFitting fitting : CATALOG) {
            if (fitting.slot() == slot) found.add(fitting);
        }
        return List.copyOf(found);
    }

    /** The fitting a boat leaves the yard with in {@code slot}. */
    public static BoatFitting standard(BoatFittingSlot slot) {
        return switch (slot) {
            case PLATING -> STANDARD_PLATING;
            case DRIVE -> STANDARD_DRIVE;
            case LIFT -> STANDARD_LIFT;
            case CABIN -> STANDARD_CABIN;
            case EMITTER -> STANDARD_EMITTER;
            case POWER -> STANDARD_POWER;
        };
    }

    /** Returns null for an unknown persisted fitting id. */
    public static BoatFitting findById(String id) {
        if (id == null) return null;
        for (BoatFitting fitting : CATALOG) {
            if (fitting.id().equals(id)) return fitting;
        }
        return null;
    }

    /** Resolves a persisted id, rejecting unknown fixture vocabulary. */
    public static BoatFitting requireById(String id) {
        BoatFitting fitting = findById(id);
        if (fitting == null) {
            throw new IllegalArgumentException("Unknown boat fitting id '" + id + "'");
        }
        return fitting;
    }

    /**
     * Resolution for a live boat or a fixture: no id, or one this catalog does not
     * know or that names another slot, reads as the slot's standard fitting.
     */
    public static BoatFitting resolve(String id, BoatFittingSlot slot) {
        BoatFitting fitting = findById(id);
        return fitting != null && fitting.slot() == slot ? fitting : standard(slot);
    }

    private static FabricationCost credits(int credits) {
        return new FabricationCost(credits);
    }
}
