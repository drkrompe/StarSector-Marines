package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.MechLivery;
import com.dillon.starsectormarines.battle.appearance.VehiclePaint;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The campaign factions a review render paints its sides as, where the live screen takes them from the employer and
 * the target ({@code BattleScreen}). Headless sprite sets are shared across shots and drains, so a suite binds the
 * factions for its own shots and closes the binding after; unbound, every side drives in the Hegemony's paint.
 */
public final class ReviewFactions implements AutoCloseable {

    private static volatile ReviewFactions bound;

    private final MechLivery marineLivery;
    private final MechLivery defenderLivery;
    private final VehiclePaint marine;
    private final VehiclePaint defender;

    private ReviewFactions(String marineFactionId, String defenderFactionId) {
        this.marineLivery = MechLivery.forFactionId(marineFactionId);
        this.defenderLivery = MechLivery.forFactionId(defenderFactionId);
        this.marine = VehiclePaint.of(marineLivery);
        this.defender = VehiclePaint.of(defenderLivery);
    }

    /** The livery {@code side}'s mechs wear under the current binding, or null when nothing is bound. */
    static MechLivery mechLivery(Faction side) {
        ReviewFactions binding = bound;
        if (binding == null) return null;
        if (side == Faction.MARINE) return binding.marineLivery;
        if (side == Faction.DEFENDER) return binding.defenderLivery;
        return MechLivery.BASE;
    }

    /** Paints the marine side as {@code marineFactionId} and the defenders as {@code defenderFactionId} until closed. */
    public static ReviewFactions bind(String marineFactionId, String defenderFactionId) {
        ReviewFactions binding = new ReviewFactions(marineFactionId, defenderFactionId);
        bound = binding;
        return binding;
    }

    /** The paint {@code side} wears under the current binding, or null when nothing is bound. */
    static VehiclePaint vehiclePaint(Faction side) {
        ReviewFactions binding = bound;
        if (binding == null) return null;
        if (side == Faction.MARINE) return binding.marine;
        if (side == Faction.DEFENDER) return binding.defender;
        return VehiclePaint.HEGEMONY;
    }

    @Override
    public void close() {
        if (bound == this) bound = null;
    }
}
