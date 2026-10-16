package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.ops.CompanyStoryDialogDelegate;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
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
 * The Jangala arrival: when it is due, which shots exist, and how it is put on screen. It plays once,
 * the first time the player docks at Jangala after the company prologue and before the yard panel.
 * Shots that have not been exported are left out, so the arrival grows as they land.
 */
public final class ArrivalCinematic {
    static final String MOD_ID = "starsector_marines";
    static final String DATA = "data/cinematic/jangala-arrival/";
    private static final String PLAYED = "sm_jangala_arrival_played";
    private static final Logger LOG = Global.getLogger(ArrivalCinematic.class);

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
                && !Boolean.TRUE.equals(saved.get(PLAYED));
    }

    public static boolean due(InteractionDialogAPI dialog) {
        if (Global.getSector() == null || dialog == null || !(dialog.getInteractionTarget() instanceof PlanetAPI docked)) return false;
        return CerberusCompanyStart.conversionRequired()
                && due(Global.getSector().getPersistentData(), docked, CerberusCompanyStart.destination());
    }

    /** Whether any shot of the arrival is installed, so the opening can promise it. */
    public static boolean available() {
        return !load().isEmpty();
    }

    /**
     * Shows the arrival over the docking dialog and runs {@code after} when it ends or is skipped.
     * Returns false, leaving the dialog alone, when no shot is available.
     */
    public static boolean play(InteractionDialogAPI dialog, Runnable after) {
        CinematicSequence sequence = load();
        if (sequence.isEmpty()) return false;
        // The receipt is taken first: a crash or a quit mid-arrival must not replay it.
        Global.getSector().getPersistentData().put(PLAYED, true);
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                new CompanyStoryDialogDelegate(new ArrivalCinematicScreen(sequence, ArrivalCinematic::readVideo), () -> {
                    dialog.showTextPanel();
                    dialog.showVisualPanel();
                    after.run();
                }));
        return true;
    }

    /** Every shot in {@code sequence.json} whose video and timeline are both present, in order. */
    public static CinematicSequence load() {
        return load(ArrivalCinematic::videoExists, path -> {
            try {
                return Global.getSettings().loadJSON(path);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static CinematicSequence load(Predicate<String> videoExists, Function<String, JSONObject> json) {
        List<CinematicShot> shots = new ArrayList<>();
        try {
            JSONArray order = json.apply(DATA + "sequence.json").getJSONArray("shots");
            for (int i = 0; i < order.length(); i++) {
                String id = order.getString(i);
                try {
                    CinematicShot shot = CinematicShot.parse(json.apply(DATA + id + ".json"));
                    if (videoExists.test(shot.video())) shots.add(shot);
                } catch (RuntimeException | JSONException missing) {
                    LOG.info("Arrival shot " + id + " not available: " + missing.getMessage());
                }
            }
        } catch (RuntimeException | JSONException broken) {
            LOG.warn("Arrival sequence unreadable", broken);
        }
        return new CinematicSequence(shots);
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
