package com.dillon.starsectormarines.campaign.personnel;

import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import org.apache.log4j.Logger;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Observes vanilla loot presentation and publishes eligible derelict discoveries. */
public final class CaptainDiscoverySalvageListener implements ShowLootListener {

    private static final Logger LOG = Global.getLogger(CaptainDiscoverySalvageListener.class);

    private final Supplier<MarineRoster> rosterSupplier;
    private final DoubleSupplier daySupplier;

    public CaptainDiscoverySalvageListener() {
        this(CaptainDiscoverySalvageListener::currentRoster,
                CaptainDiscoverySalvageListener::currentDay);
    }

    CaptainDiscoverySalvageListener(
            Supplier<MarineRoster> rosterSupplier, DoubleSupplier daySupplier) {
        this.rosterSupplier = rosterSupplier;
        this.daySupplier = daySupplier;
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        if (dialog == null) return;
        MarineRoster roster = rosterSupplier.get();
        if (roster == null) return;

        int before = roster.captainCandidates().size();
        CaptainCandidate candidate = DerelictCaptainDiscovery.publish(
                roster, dialog.getInteractionTarget(), (float) daySupplier.getAsDouble());
        if (candidate != null && roster.captainCandidates().size() > before) {
            LOG.info("Starsector Marines: discovered captain candidate "
                    + candidate.name() + " from " + candidate.sourceKey());
        }
    }

    private static MarineRoster currentRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    private static double currentDay() {
        return Global.getSector().getClock().getDay();
    }
}
