package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * Sector-attached holder for the marine roster. Registering an instance with
 * {@code Global.getSector().addScript(this)} makes it part of the campaign's persisted
 * graph — xstream walks {@link #roster} transitively, so the captain list survives
 * save/load with no extra plumbing.
 *
 * <p>The script doesn't currently do anything per-frame; {@link #advance(float)} is a no-op.
 * Future phases will use it for periodic tasks (injury recovery ticks, recruitment offers).
 */
public class MarineRosterScript implements EveryFrameScript {

    private final MarineRoster roster = new MarineRoster();

    public MarineRoster roster() {
        return roster;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        // No-op for now. Future: tick injury recovery, refresh recruitment offers.
    }

    /** Find the registered roster script on the sector, or null if not yet installed. */
    public static MarineRosterScript getInstance() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof MarineRosterScript) {
                return (MarineRosterScript) script;
            }
        }
        return null;
    }
}
