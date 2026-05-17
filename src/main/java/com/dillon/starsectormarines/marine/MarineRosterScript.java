package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.text.MessageFormat;

/**
 * Sector-attached holder for the marine roster. Registering an instance with
 * {@code Global.getSector().addScript(this)} makes it part of the campaign's persisted
 * graph — xstream walks {@link #roster} transitively, so the captain list survives
 * save/load with no extra plumbing.
 *
 * <p>{@link #advance(float)} ticks injury recovery — INJURED captains return to ACTIVE
 * once the sector clock passes their {@code injuredUntilDay}. Cheap O(n) sweep over a
 * small list; runs in the same in-game-time domain as the timer was set in, so save/load
 * round-trips it cleanly.
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
        if (Global.getSector() == null) return;
        float day = Global.getSector().getClock().getDay();
        for (MarineCaptain c : roster.all()) {
            if (c.status() == Status.INJURED && day >= c.injuredUntilDay()) {
                c.setStatus(Status.ACTIVE);
                c.setInjuredUntilDay(0f);
                c.commendations().add(MessageFormat.format(
                        "Day {0}: Returned to active duty.",
                        (int) day));
            }
        }
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
