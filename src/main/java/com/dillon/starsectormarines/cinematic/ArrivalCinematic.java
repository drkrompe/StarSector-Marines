package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;

import java.util.Map;

/**
 * The Jangala arrival: when it is due. It plays once, the first time the player docks at Jangala
 * after the company prologue and before the yard panel. The film itself is {@link #FILM}.
 */
public final class ArrivalCinematic {
    public static final CinematicFilm FILM = new CinematicFilm("jangala-arrival", "sm_jangala_arrival_played");

    private ArrivalCinematic() { }

    /**
     * Due when the founding conversion is still owed, the player is docked at the planet the opening
     * was set toward, that planet is Jangala, the prologue is over and the arrival has not played.
     * A start that fell back to another port never satisfies the destination test.
     */
    public static boolean due(Map<String, Object> saved, PlanetAPI docked, PlanetAPI destination) {
        return docked != null && destination != null && "jangala".equals(docked.getId())
                && docked.getId().equals(destination.getId())
                && saved.containsKey(CerberusCompanyStart.SHIP_KEY) && !CompanyPrologue.pending(saved)
                && !FILM.played(saved);
    }

    public static boolean due(InteractionDialogAPI dialog) {
        if (Global.getSector() == null || dialog == null || !(dialog.getInteractionTarget() instanceof PlanetAPI docked)) return false;
        return CerberusCompanyStart.conversionRequired()
                && due(Global.getSector().getPersistentData(), docked, CerberusCompanyStart.destination());
    }

    /** Whether any shot of the arrival is installed, so the opening can promise it. */
    public static boolean available() {
        return FILM.available();
    }
}
