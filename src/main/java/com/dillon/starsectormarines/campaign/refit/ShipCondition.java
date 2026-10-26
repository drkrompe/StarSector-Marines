package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.battle.squad.HullWelfare;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.ReservedFacility;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.marine.MechFieldRepair;
import com.dillon.starsectormarines.marine.RecruitIntake;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where every refit track of the founding hull stands, and what that is worth.
 * Pure: the campaign reads receipts into one of these, and everything a band
 * does is answered here so Engineering, the launch freeze and the hull mod
 * cannot disagree. {@link #NONE} is every other hull: neutral on every track.
 */
public record ShipCondition(boolean applies, Map<RefitTrack, RefitBand> bands) {
    public static final ShipCondition NONE = new ShipCondition(false, Map.of());

    public ShipCondition {
        var copy = new EnumMap<RefitTrack, RefitBand>(RefitTrack.class);
        if (applies) for (RefitTrack track : RefitTrack.values()) copy.put(track, bands.getOrDefault(track, track.start()));
        bands = Collections.unmodifiableMap(copy);
    }

    /** The battered start: every track at its starting band, the workshop as found. */
    public static ShipCondition battered() { return new ShipCondition(true, Map.of()); }

    public RefitBand band(RefitTrack track) { return applies ? bands.get(track) : RefitBand.RESTORED; }

    public ShipCondition with(RefitTrack track, RefitBand band) {
        if (!applies) return this;
        var copy = new EnumMap<>(bands);
        copy.put(track, band);
        return new ShipCondition(true, copy);
    }

    /** The workshop chain is the fabrication core: repaired machinery is restored, tooling improves it, the reclamation line further. */
    public static RefitBand fabrication(WorkshopState workshop) {
        return switch (workshop) {
            case REPAIRED -> RefitBand.RESTORED;
            case EQUIPPED -> RefitBand.IMPROVED_I;
            case RECLAIMING -> RefitBand.IMPROVED_II;
            case NONE -> RefitBand.RESTORED;
            default -> RefitBand.DEFICIT;
        };
    }

    /** Cores whose restoration has cleared their deficit dressing from the deck. */
    public Set<ShipCore> restoredCores() {
        var cores = EnumSet.noneOf(ShipCore.class);
        for (RefitTrack track : RefitTrack.values())
            if (track.core != null && band(track).atLeast(RefitBand.RESTORED)) cores.add(track.core);
        return cores;
    }

    /** Reserved compartments whose facility has been opened; the deck seals the rest. */
    public Set<ReservedFacility> openReserves() {
        var open = EnumSet.noneOf(ReservedFacility.class);
        if (!applies) return open;
        for (RefitTrack track : RefitTrack.values())
            if (track.reserve != null && band(track).atLeast(RefitBand.RESTORED)) open.add(track.reserve);
        return open;
    }

    /**
     * The tiers bought of each facility fitted into an existing room, as the
     * deck dresses them. A hull this record does not apply to shows none.
     */
    public FacilityDressing facilityDressing() {
        if (!applies) return FacilityDressing.NONE;
        return new FacilityDressing(band(RefitTrack.HYDROPONICS).ordinal(), band(RefitTrack.WARDROOM).ordinal(),
                band(RefitTrack.BRIEFING).ordinal(), band(RefitTrack.HOLDS).ordinal());
    }

    /** The order that would advance {@code track}, or null when it is at its ceiling. */
    public RefitOrder next(RefitTrack track) { return RefitOrder.after(track, band(track)); }

    /** The first unmet requirement of {@code order}, in its declared order, or null when none. */
    public Map.Entry<RefitTrack, RefitBand> missing(RefitOrder order) {
        for (var need : order.requires.entrySet()) if (!band(need.getKey()).atLeast(need.getValue())) return need;
        return null;
    }

    // --- Company passives (frozen at launch or read at resolution) ---

    /** Infantry morale recovery: field rations, restored mess, galley, cold store. */
    public float moraleRecovery() {
        return switch (band(RefitTrack.MESS)) {
            case DEFICIT -> 0.85f;
            case RESTORED -> 1f;
            case IMPROVED_I -> 1.15f;
            case IMPROVED_II -> 1.25f;
        };
    }

    /** Days added to a wound's recovery while the scrubbers are degraded. */
    public float woundRecoveryExtraDays() { return band(RefitTrack.LIFE_SUPPORT) == RefitBand.DEFICIT ? 1f : 0f; }

    // --- The reserved facilities. A sealed compartment is neutral, so opening one is
    // already a gain, and every other hull reads as sealed rather than as opened. ---

    /** Infantry primary accuracy: the range's target machinery, then its simulator lanes. */
    public float infantryAccuracy() { return facility(RefitTrack.RANGE, 1f, 1f, 1.03f, 1.06f); }

    /** Infantry move speed: the gym open, its conditioning programme, the track loop. */
    public float infantryMoveSpeed() { return facility(RefitTrack.GYM, 1f, 1.02f, 1.04f, 1.06f); }

    /** Morale a hit or near miss drains from a fire team: the track loop's squads take it steadier. */
    public float infantryMoraleDrain() { return facility(RefitTrack.GYM, 1f, 1f, 1f, 0.90f); }

    /** Everything above that reaches a battle, frozen onto each squad at launch. */
    public HullWelfare hullWelfare() {
        return new HullWelfare(moraleRecovery(), infantryAccuracy(), infantryMoveSpeed(), infantryMoraleDrain());
    }

    /**
     * The share of marines a battle counted as killed whom the sick bay brings back
     * wounded instead. Read when the battle is settled; it moves no one already
     * wounded or missing, and stacks with a squad medic's work in the field.
     */
    public float casualtiesSaved() { return facility(RefitTrack.SICK_BAY, 0f, 0.10f, 0.25f, 0.40f); }

    /** Where a cargo replacement's aptitude lands: the lounge keeps marginal hands, then draws better ones. */
    public RecruitIntake recruitIntake() {
        return switch (facilityBand(RefitTrack.REC_LOUNGE)) {
            case DEFICIT -> RecruitIntake.NEUTRAL;
            case RESTORED -> new RecruitIntake(5, 25, 100);
            case IMPROVED_I -> new RecruitIntake(5, 35, 100);
            case IMPROVED_II -> new RecruitIntake(10, 45, 100);
        };
    }

    private RefitBand facilityBand(RefitTrack track) { return applies ? band(track) : RefitBand.DEFICIT; }

    private float facility(RefitTrack track, float sealed, float open, float first, float second) {
        return switch (facilityBand(track)) {
            case DEFICIT -> sealed;
            case RESTORED -> open;
            case IMPROVED_I -> first;
            case IMPROVED_II -> second;
        };
    }

    // --- Mech Lab: frozen into each company mech's deployment at launch, or read at a quote ---

    /**
     * What the Mech Lab's refits are worth to a deployed company mech: calibration's aim (and
     * the tactical core link's torso traverse), the coolant plant's dissipation, the armor shop's
     * plate. Neutral until a track is improved; nothing in the Mech Lab starts at a deficit.
     */
    public MechRefit mechRefit() {
        RefitBand calibration = band(RefitTrack.MECH_CALIBRATION);
        return new MechRefit(
                facility(RefitTrack.MECH_CALIBRATION, 1.03f, 1.06f),
                facility(RefitTrack.MECH_COOLANT, 1.10f, 1.20f),
                facility(RefitTrack.MECH_ARMOR, 1.05f, 1.10f),
                calibration == RefitBand.IMPROVED_II ? 1.25f : 1f);
    }

    /** Field repair: a quarter off every repair clock, and with the spares rack a quarter off a weapon assembly. */
    public MechFieldRepair mechFieldRepair() {
        RefitBand repair = band(RefitTrack.MECH_REPAIR);
        return new MechFieldRepair(repair.atLeast(RefitBand.IMPROVED_I) ? 0.75f : 1f,
                repair == RefitBand.IMPROVED_II ? 0.75f : 1f);
    }

    /** A facility's figure: neutral until improved. */
    private float facility(RefitTrack track, float first, float second) {
        return switch (band(track)) {
            case IMPROVED_I -> first;
            case IMPROVED_II -> second;
            default -> 1f;
        };
    }

    /** Officer XP from a mission, read at resolution: the wardroom, then the staff college. */
    public float officerXpMult() {
        return switch (band(RefitTrack.WARDROOM)) {
            case IMPROVED_I -> 1.15f;
            case IMPROVED_II -> 1.30f;
            default -> 1f;
        };
    }

    /** The holo-tank shows the briefing the garrison the operation faces; read when the briefing is drawn. */
    public boolean garrisonIntel() { return applies && band(RefitTrack.BRIEFING).atLeast(RefitBand.IMPROVED_II); }

    // --- Hull figures, applied by the conversion hull mod ---

    /** Her own supplies per month: hydroponic racks grow part of what the company eats. */
    public float suppliesPerMonthMult() {
        return switch (band(RefitTrack.HYDROPONICS)) {
            case IMPROVED_I -> 0.95f;
            case IMPROVED_II -> 0.90f;
            default -> 1f;
        };
    }

    /** Cargo space won back by racking the holds, on top of what the conversion took. */
    public int cargoBonus() {
        return switch (band(RefitTrack.HOLDS)) {
            case IMPROVED_I -> 20;
            case IMPROVED_II -> 40;
            default -> 0;
        };
    }

    public float hullMult() { return combat(RefitTrack.HULL, 0.80f, 1.15f, 1.30f); }
    public float armorMult() { return combat(RefitTrack.HULL, 0.85f, 1.10f, 1.20f); }
    public float fluxCapacityMult() { return combat(RefitTrack.FLUX, 0.80f, 1.15f, 1.30f); }
    public float fluxDissipationMult() { return combat(RefitTrack.FLUX, 0.80f, 1.10f, 1.20f); }
    public float sensorStrengthMult() { return combat(RefitTrack.SENSORS, 0.70f, 1.25f, 1.50f); }
    public float sensorProfileMult() { return band(RefitTrack.SENSORS) == RefitBand.IMPROVED_II ? 0.75f : 1f; }
    public float speedMult() { return combat(RefitTrack.DRIVE, 0.90f, 1.10f, 1.20f); }
    public float accelerationMult() { return combat(RefitTrack.DRIVE, 0.90f, 1.15f, 1.25f); }
    public int burnDelta() {
        return switch (band(RefitTrack.DRIVE)) {
            case DEFICIT -> -1;
            case IMPROVED_II -> 1;
            default -> 0;
        };
    }

    private float combat(RefitTrack track, float deficit, float first, float second) {
        return switch (band(track)) {
            case DEFICIT -> deficit;
            case RESTORED -> 1f;
            case IMPROVED_I -> first;
            case IMPROVED_II -> second;
        };
    }
}
