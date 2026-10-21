package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
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

    /** The workshop chain is the fabrication core: repaired machinery is restored, tooling improves it. */
    public static RefitBand fabrication(WorkshopState workshop) {
        return switch (workshop) {
            case REPAIRED -> RefitBand.RESTORED;
            case EQUIPPED -> RefitBand.IMPROVED_I;
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

    // --- Hull figures, applied by the conversion hull mod ---

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
