package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.PlayerEventInbox;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.MarineOpsDialogPlugin;
import com.dillon.starsectormarines.ops.StationingResponseLaunch;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.apache.log4j.Logger;

/**
 * Watches {@link PlayerEventInbox} and offers the most urgent unacknowledged decision
 * as a modal card, without ever fighting the player for the screen.
 *
 * <p>Every piece of queue state is the persisted domain state itself, so a refused
 * offer costs nothing: the notice is simply still there on a later frame, after a
 * reload, or after a battle. The one rule that matters is that a notice is
 * acknowledged only when {@code showInteractionDialog} actually returns true —
 * acknowledging the attempt would silently eat the event.
 */
public final class PlayerEventPresenter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(PlayerEventPresenter.class);

    /**
     * Frames between inbox scans. The scan walks the contracts table and builds payload
     * objects, which is not something to do 60 times a second for a queue that changes
     * at most once per campaign day.
     */
    private static final int SCAN_INTERVAL_FRAMES = 30;

    /** Behavior only — nothing here is worth persisting; the queue lives on CampaignState. */
    private transient int framesSinceScan;
    private transient PlayerEventNotice pendingDeployment;

    /**
     * Queues the Marine Ops hand-off requested by the card's Deploy button. It cannot be
     * opened inline: the event dialog is still showing at that moment, and
     * {@code showInteractionDialog} refuses while another dialog is up. Taking it
     * through the same politeness gate as a fresh offer means the hand-off waits for the
     * card to actually close instead of being dropped.
     */
    public static void requestDeployment(PlayerEventNotice notice) {
        PlayerEventPresenter presenter = getInstance();
        if (presenter != null) presenter.pendingDeployment = notice;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    /**
     * True so a decision still reaches a player who paused to think, and — more
     * importantly — so the Deploy hand-off queued below still opens when the campaign
     * comes back from the dialog still paused. Interaction dialogs pause the campaign,
     * so a false here would strand a requested deployment indefinitely.
     */
    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || Global.getCurrentState() != GameState.CAMPAIGN) return;
        CampaignUIAPI ui = sector.getCampaignUI();
        if (!isQuiet(ui)) return;

        if (pendingDeployment != null) {
            PlayerEventNotice deployment = pendingDeployment;
            pendingDeployment = null;
            openDeployment(sector, ui, deployment);
            return;
        }

        if (++framesSinceScan < SCAN_INTERVAL_FRAMES) return;
        framesSinceScan = 0;

        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        presentNext(script.state(), roster(), CampaignClock.day(),
                (notice, day) -> ui.showInteractionDialog(
                        new PlayerEventDialogPlugin(notice, day), sector.getPlayerFleet()));
    }

    /** Injection seam for {@link #presentNext}: try to show the card, report whether it went up. */
    interface Offer {
        boolean show(PlayerEventNotice notice, int day);
    }

    /**
     * Offers the most urgent unacknowledged notice and acknowledges it <em>only</em> if
     * the offer was accepted. {@code showInteractionDialog} returns false whenever the
     * UI is busy, and that is a normal outcome, not an error — acknowledging on the
     * attempt would drop a campaign decision the player never saw.
     *
     * @return the notice actually presented, or null if there was none or it was refused
     */
    static PlayerEventNotice presentNext(CampaignState state, MarineRoster roster,
                                         int day, Offer offer) {
        PlayerEventNotice notice = PlayerEventInbox.nextToPresent(state, roster, day);
        if (notice == null || !offer.show(notice, day)) return null;
        PlayerEventInbox.acknowledge(state, notice, day);
        return notice;
    }

    /** No dialog, no core tab, no menu — anything else and we wait for a later frame. */
    private static boolean isQuiet(CampaignUIAPI ui) {
        return ui != null && isQuiet(ui.isShowingDialog(), ui.isShowingMenu(),
                ui.getCurrentCoreTab() != null);
    }

    static boolean isQuiet(boolean showingDialog, boolean showingMenu, boolean inCoreTab) {
        return !showingDialog && !showingMenu && !inCoreTab;
    }

    /**
     * Opens Marine Ops against the notice's own market, seeded onto the pending
     * response. The interaction target is the player's fleet rather than the distant
     * planet: the detachment is already stationed there and fights with local
     * transport, so the player's position is fictionally irrelevant and there is no
     * reason to involve a remote entity in the interaction.
     */
    private static void openDeployment(SectorAPI sector, CampaignUIAPI ui,
                                       PlayerEventNotice notice) {
        PlanetAPI planet = PlayerEventTarget.planet(notice);
        if (planet == null) {
            LOG.warn("PlayerEvent: no planet for " + notice + "; deployment dropped");
            return;
        }
        ui.showInteractionDialog(
                new MarineOpsDialogPlugin(planet,
                        ctx -> StationingResponseLaunch.respondTo(ctx, notice.contractId)),
                sector.getPlayerFleet());
    }

    private static MarineRoster roster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    /** Finds the registered presenter, or null if not yet installed. */
    public static PlayerEventPresenter getInstance() {
        if (Global.getSector() == null) return null;
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof PlayerEventPresenter) {
                return (PlayerEventPresenter) script;
            }
        }
        return null;
    }
}
