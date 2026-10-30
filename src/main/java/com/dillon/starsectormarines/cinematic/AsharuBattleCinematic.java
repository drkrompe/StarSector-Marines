package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The Asharu wreckfall recording: when it is due. It plays once, the first time the player docks at
 * Asharu after A Cold Account is won and its aftermath acknowledged (the recovery arc opens), and
 * before the Salvage Rights briefing has been acted on. The film itself is {@link #FILM}.
 */
public final class AsharuBattleCinematic {
    public static final CinematicFilm FILM = new CinematicFilm("asharu-battle", "sm_asharu_battle_played");

    private AsharuBattleCinematic() { }

    /**
     * Due when the recovery arc is open ({@code recovery} non-null), the player is docked at Asharu,
     * the film has not played, Salvage Rights has never been launched (so a save already into the arc
     * is never surprised by it) and at least one shot is installed. {@code available} is asked last,
     * since it reads the film's files.
     */
    public static boolean due(Map<String, Object> saved, ShepherdRecoveryProgress recovery, boolean atAsharu,
                              BooleanSupplier available) {
        return recovery != null && atAsharu && !FILM.played(saved) && recovery.untouched() && available.getAsBoolean();
    }

    public static boolean due(InteractionDialogAPI dialog) {
        if (Global.getSector() == null || dialog == null || !(dialog.getInteractionTarget() instanceof PlanetAPI docked)) return false;
        return due(Global.getSector().getPersistentData(), ShepherdRecoveryCampaign.current(),
                ShepherdRecoveryCampaign.atSite(docked), FILM::available);
    }
}
