package com.dillon.starsectormarines.battle.air;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Worst structural damage per owned aircraft, retained across tactical servicing and trips. */
public final class BoatDamageLedger {
    private final String settlementId = UUID.randomUUID().toString();
    private final Map<String, Float> damage = new LinkedHashMap<>();

    public void observe(Airframe frame, float hp) {
        if (!(frame instanceof FittedBoat boat) || boat.boatId() == null
                || !Float.isFinite(hp) || frame.maxHp() <= 0f) return;
        float fraction = Math.max(0f, Math.min(1f, 1f - hp / frame.maxHp()));
        if (fraction > 0f) damage.merge(boat.boatId(), fraction, Math::max);
    }

    public String settlementId() { return settlementId; }
    public Map<String, Float> damage() { return Map.copyOf(damage); }
}
