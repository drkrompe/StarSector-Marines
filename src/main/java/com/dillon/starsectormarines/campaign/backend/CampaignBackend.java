package com.dillon.starsectormarines.campaign.backend;

import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * Where the mod's screens and rules reach the running campaign, so that one seam can answer them without a game, as
 * the battle renderer drains into a window, a snapshot or nothing. {@code playBattle} plays the company's screens over a
 * stand-in backend; the shipped mod only ever uses the game's.
 *
 * <p>One small interface per family of question, in the mod's own terms where it can be. Each family moves here whole
 * in its own commit, and {@code CampaignBackendBoundaryTest} keeps it moved ({@code campaign-backend.md}).
 *
 * <p><b>Held statically and never saved.</b> Nothing reachable from a saved object may keep a backend in a field: the
 * game serializes those, and the answers belong to whichever backend is installed now. Read it here each time.
 */
public final class CampaignBackend {

    /** The save's own key-value store. */
    public interface SavedData {
        /** The store, or null when no campaign is running. */
        Map<String, Object> data();
    }

    /** The game's answers, read afresh each time since a sector comes and goes with each save. */
    private static final SavedData GAME_SAVED_DATA =
            () -> Global.getSector() == null ? null : Global.getSector().getPersistentData();

    private static volatile SavedData savedData = GAME_SAVED_DATA;

    private CampaignBackend() {
    }

    /** {@link SavedData#data} of the installed backend. */
    public static Map<String, Object> savedData() {
        return savedData.data();
    }

    /** Answers saved data from {@code backend} from now on, for a host with no game running. */
    public static void useSavedData(SavedData backend) {
        savedData = backend;
    }
}
