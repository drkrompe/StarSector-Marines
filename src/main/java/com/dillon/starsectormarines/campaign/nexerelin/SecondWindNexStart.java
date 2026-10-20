package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.customstart.CustomStart;

import java.util.Map;

/**
 * The Second Wind start offered in Nexerelin's custom-start list ({@code nexerelin-nouns.md}). Nexerelin replaces
 * vanilla new-game creation, so the {@code smCerberus*} rules never show there; this reproduces what they do. Named
 * only by {@code data/config/exerelin/customStarts.json}, which Nexerelin alone reads and instantiates reflectively,
 * so a game without Nexerelin never loads it.
 *
 * <p>Difficulty is Nexerelin's own Easy-mode setting from its new-game panel rather than a second prompt here.
 */
public final class SecondWindNexStart extends CustomStart {

    public static final String VARIANT_ID = CerberusCompanyStart.START_VARIANT_ID;
    public static final int CHARACTER_POINTS = 1;
    /** Where vanilla's {@code NGCSetStartingLocation Corvus -2500 3000} puts the fleet before the opening moves it. */
    public static final String STARTING_SYSTEM = "Corvus";
    public static final float STARTING_X = -2500f;
    public static final float STARTING_Y = 3000f;
    /** Nexerelin's override of its post-time-pass fleet placement; an entity id. */
    public static final String START_LOCATION_KEY = "$nex_startLocation";

    @Override
    public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
        dialog.getTextPanel().addPara(Strings.get("openingSecondWindNexStartStory"));
        data.addStartingFleetMember(VARIANT_ID, FleetMemberType.SHIP);
        AddRemoveCommodity.addFleetMemberGainText(Global.getSettings().getVariant(VARIANT_ID), dialog.getTextPanel());
        data.getPerson().getStats().addPoints(CHARACTER_POINTS);
        data.setStartingLocationName(STARTING_SYSTEM);
        data.getStartingCoordinates().set(STARTING_X, STARTING_Y);

        PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);
        ExerelinSetupData.getInstance().freeStart = true;

        // Creation scripts run before Nexerelin's post-time-pass placement, which would otherwise move a free start to
        // a random friendly market. Point it at the opening's yard, then restore the opening's approach once play begins.
        // With no yard open anywhere, Nexerelin's own friendly market is a better place to wait than a forced Jangala.
        data.addScript(() -> {
            PlanetAPI planet = CerberusCompanyStart.destination();
            String entity = startEntityId(planet == null ? null : planet.getId());
            if (entity != null) Global.getSector().getMemoryWithoutUpdate().set(START_LOCATION_KEY, entity, 15);
        });
        data.addScript(new CerberusCompanyStart());
        data.addScript(() -> Global.getSector().addTransientScript(new Approach()));

        FireBest.fire(null, dialog, memoryMap, "NewGameBackgroundSelection");
    }

    /** The entity Nexerelin should place the fleet at: the opening's own yard, else null to leave it to Nexerelin. */
    public static String startEntityId(String destinationId) {
        return destinationId == null || destinationId.isEmpty() ? null : destinationId;
    }

    /** Re-applies the opening's approach once, after Nexerelin has finished placing the fleet. */
    private static final class Approach implements EveryFrameScript {
        private boolean done;

        @Override public boolean isDone() { return done; }
        @Override public boolean runWhilePaused() { return true; }

        @Override public void advance(float amount) {
            if (done) return;
            done = true;
            if (Global.getSector().getPlayerFleet() != null)
                CerberusCompanyStart.approachDestination(Global.getSector().getPlayerFleet());
        }
    }
}
