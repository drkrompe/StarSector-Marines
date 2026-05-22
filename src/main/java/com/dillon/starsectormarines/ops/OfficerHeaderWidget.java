package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CommsOfficerSummary;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.OfficerMood;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;

import java.awt.Color;

/**
 * The officer's header line at the top of the comms console — calls into
 * {@link CommsOfficerSummary} with counts derived from {@link CampaignState}'s
 * contracts table and the player's currently selected client.
 *
 * <p>Reads live each frame; per-day determinism is enforced inside
 * {@code CommsOfficerSummary} so the line is stable through the player's
 * interaction with the screen on a given day.
 *
 * <p>Mood comes from {@link OfficerMoodReader} — currently stubbed to
 * {@link OfficerMood#STEADY}; deriving real mood from {@link CampaignState}
 * + vanilla state is the next planned step.
 *
 * <p>If the campaign state isn't available (early-game, no script registered)
 * the widget renders the placeholder "—" rather than throwing. Same for the
 * sector clock — defensive defaults at every external lookup.
 */
public class OfficerHeaderWidget extends BaseWidget {

    /** Days-left threshold below which an offer counts as "lapsing soon." */
    private static final int LAPSING_SOON_DAYS = 2;

    private static final Color TEXT_COLOR = new Color(0xE0, 0xE8, 0xF4);

    private final MarineOpsContext ctx;

    public OfficerHeaderWidget(MarineOpsContext ctx, float x, float y, float w, float h) {
        this.ctx = ctx;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public boolean contains(int px, int py) {
        // Header is decorative — never absorbs clicks. Lets dossier cards
        // beneath it stay reachable if the header bounds ever overlap.
        return false;
    }

    @Override
    public void render(float alphaMult) {
        String line = composeLine();
        if (line == null || line.isEmpty()) return;
        Fonts.ORBITRON_20.drawString(line, x, y + h, TEXT_COLOR, alphaMult);
    }

    /**
     * Builds the line for this frame. Pulled out so the rendering path stays
     * trivial and the data-gathering side is testable in isolation if we ever
     * want unit coverage above the {@link CommsOfficerSummary} layer.
     */
    String composeLine() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return "—";
        CampaignState state = script.state();
        int currentDay = currentSectorDay(state);
        OfficerMood mood = OfficerMoodReader.currentMood();

        Client selected = ctx.getSelectedClient();
        // patronHouseId == -1L is the "faction-direct client" sentinel — fall
        // back to the overview line since we don't have a patron house to key
        // the client-summary content off of.
        if (selected == null || selected.patronHouseId == -1L) {
            Counts c = countAllOffers(state, currentDay);
            return CommsOfficerSummary.renderOverview(
                    mood, currentDay, c.offerCount, c.clientCount, c.lapsingCount);
        }

        long patronId = selected.patronHouseId;
        Counts c = countOffersForPatron(state, currentDay, patronId);
        return CommsOfficerSummary.renderForClient(
                mood, currentDay, patronId, selected.displayName,
                c.offerCount, c.lapsingCount);
    }

    private static int currentSectorDay(CampaignState state) {
        if (Global.getSector() != null && Global.getSector().getClock() != null) {
            return (int) Global.getSector().getClock().getDay();
        }
        return state.lastTickDay;
    }

    private static Counts countAllOffers(CampaignState state, int currentDay) {
        int offers = 0;
        int lapsing = 0;
        java.util.HashSet<Long> patrons = new java.util.HashSet<Long>();
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) continue;
            offers++;
            patrons.add(state.contractPatronHouseId[i]);
            int dl = state.contractDaysLeft(i, currentDay);
            if (dl >= 0 && dl <= LAPSING_SOON_DAYS) lapsing++;
        }
        return new Counts(offers, patrons.size(), lapsing);
    }

    private static Counts countOffersForPatron(CampaignState state, int currentDay, long patronId) {
        int offers = 0;
        int lapsing = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) continue;
            if (state.contractPatronHouseId[i] != patronId) continue;
            offers++;
            int dl = state.contractDaysLeft(i, currentDay);
            if (dl >= 0 && dl <= LAPSING_SOON_DAYS) lapsing++;
        }
        return new Counts(offers, 1, lapsing);
    }

    private static final class Counts {
        final int offerCount;
        final int clientCount;
        final int lapsingCount;
        Counts(int offerCount, int clientCount, int lapsingCount) {
            this.offerCount = offerCount;
            this.clientCount = clientCount;
            this.lapsingCount = lapsingCount;
        }
    }
}
