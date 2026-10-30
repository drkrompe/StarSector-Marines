package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.ops.CompanyStoryDialogDelegate;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One pre-rendered film, known by its id: its timelines live in {@code data/cinematic/<id>/}
 * ({@code sequence.json} and one {@code <shot>.json} per shot) and its videos under
 * {@code cinematic/<id>/}. A film plays once per save; {@code receipt} is the sector persistent-data
 * key that records it. Shots that have not been exported are left out, so a film grows as they land
 * and a film with none installed never plays.
 */
public final class CinematicFilm {
    private static final Logger LOG = Global.getLogger(CinematicFilm.class);
    /** The film on screen now, if any: two films never stack over one dialog. */
    private static CinematicFilm showing;

    private final String id;
    private final String receipt;

    public CinematicFilm(String id, String receipt) {
        this.id = id;
        this.receipt = receipt;
    }

    public String id() { return id; }
    public String receipt() { return receipt; }
    String data() { return "data/cinematic/" + id + "/"; }

    /** Whether this save has already played the film (or started it: the receipt is taken first). */
    public boolean played(Map<String, Object> saved) {
        return Boolean.TRUE.equals(saved.get(receipt));
    }

    /** Forgets a film left showing by a dialog that never reported its dismissal; a fresh load shows none. */
    public static void clearShowing() {
        showing = null;
    }

    /** Whether any film is on screen, so a second presenter waits rather than stacking over it. */
    public static boolean anyShowing() {
        return showing != null;
    }

    /** Whether any shot of the film is installed. */
    public boolean available() {
        return !load().isEmpty();
    }

    /**
     * Shows the film over the dialog and runs {@code after} when it ends or is skipped. The dialog's
     * panels stay hidden: {@code after} either opens the next screen over them or puts them back.
     * Returns false, leaving the dialog alone, when no shot is available or another film is showing.
     */
    public boolean play(InteractionDialogAPI dialog, Runnable after) {
        if (showing != null) return false;
        CinematicSequence sequence = load();
        if (sequence.isEmpty()) return false;
        // The receipt is taken first: a crash or a quit mid-film must not replay it.
        Global.getSector().getPersistentData().put(receipt, true);
        showing = this;
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        try {
            dialog.showCustomVisualDialog(Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                    CompanyStoryDialogDelegate.scored(new CinematicScreen(sequence, CinematicFilm::readVideo), () -> {
                        showing = null;
                        after.run();
                    }));
        } catch (RuntimeException failure) {
            showing = null;
            throw failure;
        }
        return true;
    }

    /** Every shot in {@code sequence.json} whose video and timeline are both present, in order. */
    public CinematicSequence load() {
        return load(CinematicFilm::videoExists, path -> {
            try {
                return Global.getSettings().loadJSON(path);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    CinematicSequence load(Predicate<String> videoExists, Function<String, JSONObject> json) {
        List<CinematicShot> shots = new ArrayList<>();
        CinematicSequence.Captions captions = CinematicSequence.Captions.ALWAYS;
        try {
            JSONObject sequence = json.apply(data() + "sequence.json");
            captions = CinematicSequence.Captions.parse(sequence.optString("captions", ""));
            JSONArray order = sequence.getJSONArray("shots");
            for (int i = 0; i < order.length(); i++) {
                String shotId = order.getString(i);
                try {
                    CinematicShot shot = CinematicShot.parse(json.apply(data() + shotId + ".json"));
                    if (videoExists.test(shot.video())) shots.add(shot);
                } catch (RuntimeException | JSONException missing) {
                    LOG.info("Film " + id + " shot " + shotId + " not available: " + missing.getMessage());
                }
            }
        } catch (RuntimeException | JSONException broken) {
            LOG.warn("Film " + id + " sequence unreadable", broken);
        }
        return new CinematicSequence(shots, captions);
    }

    /** Videos ship beside the mod's data, outside the jar, and are read through the settings API. */
    static boolean videoExists(String modPath) {
        try (InputStream in = Global.getSettings().openStream(modPath)) {
            return in != null;
        } catch (IOException | RuntimeException absent) {
            return false;
        }
    }

    static byte[] readVideo(String modPath) throws IOException {
        try (InputStream in = Global.getSettings().openStream(modPath)) {
            return in.readAllBytes();
        }
    }
}
