package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MechFieldRepair;
import java.util.ArrayList;
import java.util.List;

/** Player-facing names for tracks, tiers and lock reasons; keys are built from the enums. */
public final class RefitText {
    private RefitText() { }

    public static String track(RefitTrack track) { return Strings.get("openingRefitTrack" + track.name()); }

    public static String tier(RefitTrack track, RefitBand band) {
        return Strings.get("openingRefitTier" + track.name() + band.name());
    }

    /** What a band of a track means, in plain figures. */
    public static String effect(RefitTrack track, RefitBand band) {
        return Strings.get("openingRefitEffect" + track.name() + band.name());
    }

    /** Castle on how the system works aboard her, for the systems tree. */
    public static String lore(RefitTrack track) { return Strings.get("openingRefitLore" + track.name()); }

    public static String band(RefitBand band) { return Strings.get("openingRefitBand" + band.name()); }

    /** Castle's reason a tier is locked: the core it needs, in the core's own terms. */
    public static String lock(RefitTrack needed, RefitBand band) {
        String key = needed.core != null ? "openingRefitLock" + needed.name() : "openingRefitLockOther";
        return Strings.format(key, tier(needed, band));
    }

    /**
     * What the Mech Lab's refits add to every company mech, as the mech's sheet and the briefing
     * show it: "Mech Lab: aim +3%  ·  vents +10%  ·  plate +5%". Empty while every mech track is
     * neutral, so a sheet without refits stays quiet.
     */
    public static String mechLine(ShipCondition condition) {
        MechRefit refit = condition.mechRefit();
        List<String> parts = new ArrayList<>();
        if (refit.accuracyMult() != 1f) parts.add(Strings.format("openingRefitMechAim", percent(refit.accuracyMult())));
        if (refit.traverseMult() != 1f) parts.add(Strings.format("openingRefitMechTraverse", percent(refit.traverseMult())));
        if (refit.dissipationMult() != 1f) parts.add(Strings.format("openingRefitMechVents", percent(refit.dissipationMult())));
        if (refit.armorMult() != 1f) parts.add(Strings.format("openingRefitMechPlate", percent(refit.armorMult())));
        MechFieldRepair repair = condition.mechFieldRepair();
        if (repair.repairDaysMult() != 1f) parts.add(Strings.format("openingRefitMechRepairDays", percent(repair.repairDaysMult())));
        if (repair.weaponBillMult() != 1f) parts.add(Strings.format("openingRefitMechSpares", percent(repair.weaponBillMult())));
        return parts.isEmpty() ? "" : Strings.format("openingRefitMechLine", String.join("  ·  ", parts));
    }

    /** A multiplier as a signed whole percentage: 1.06 is "+6", 0.75 is "-25". */
    static String percent(float mult) {
        int points = Math.round((mult - 1f) * 100f);
        return (points > 0 ? "+" : "") + points;
    }

    /**
     * The briefing's line for what the company's home is worth in this
     * operation, frozen at launch: the mess's morale band; while the scrubbers
     * are degraded, the extra day every wound will take; each training or medical room once it
     * changes the fight (the range from its target machinery, the gym and sick
     * bay from opening); and the Mech Lab's refits once any is improved. Null
     * off the founding hull, where every track is neutral.
     */
    public static String briefingLine(ShipCondition condition) {
        if (!condition.applies()) return null;
        List<String> parts = new ArrayList<>();
        for (RefitTrack track : new RefitTrack[]{RefitTrack.MESS, RefitTrack.LIFE_SUPPORT,
                RefitTrack.RANGE, RefitTrack.GYM, RefitTrack.SICK_BAY}) {
            RefitBand band = condition.band(track);
            if (!onBriefing(track, band)) continue;
            parts.add(Strings.format("openingRefitTooltipRow", track(track), tier(track, band), effect(track, band)));
        }
        String mechs = mechLine(condition);
        if (!mechs.isEmpty()) parts.add(mechs);
        return String.join("  ·  ", parts);
    }

    private static boolean onBriefing(RefitTrack track, RefitBand band) {
        return switch (track) {
            case MESS -> true;
            case LIFE_SUPPORT -> band == RefitBand.DEFICIT;
            // An open range trains aboard but does not yet change the fight.
            case RANGE -> band.atLeast(RefitBand.IMPROVED_I);
            default -> band.atLeast(RefitBand.RESTORED);
        };
    }
}
