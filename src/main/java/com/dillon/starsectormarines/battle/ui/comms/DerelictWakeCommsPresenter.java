package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.FacilityWake;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * The cue of the facility's wake at a derelict site (`derelict-watch.md`, Waking): each stage the plant comes up to is a
 * comms line in the watch's voice, headed by the site's level, saying what woke. It names nothing the company has not
 * seen: vents, bays and bulkheads are heard, not shown.
 */
public final class DerelictWakeCommsPresenter {
    /** Seconds a stage's line stays up. */
    static final float SECONDS = 8f;

    private DerelictSecurityDirector current;
    private FacilityWake.Stage announced = FacilityWake.Stage.QUIET;

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        if (sim == null || sim.isComplete()) return;
        DerelictSecurityDirector director = sim.derelictSecurity();
        if (director != current) {
            current = director;
            announced = FacilityWake.Stage.QUIET;
        }
        if (director == null) return;
        FacilityWake.Stage stage = director.wake().stage();
        if (stage.ordinal() <= announced.ordinal()) return;
        announced = stage;
        BattleCommsFeed.Notice notice = notice(stage);
        if (notice != null) feed.post(notice);
    }

    /** The line for a stage, or null for one that says nothing. */
    public static BattleCommsFeed.Notice notice(FacilityWake.Stage stage) {
        String key = stage.commsKey();
        if (key == null) return null;
        RuinsPressure.Level level = RuinsPressure.Level.values()[Math.min(stage.ordinal(),
                RuinsPressure.Level.values().length - 1)];
        return new BattleCommsFeed.Notice(Strings.get(level.key), Strings.get(key), BattleCommsFeed.Tone.WARNING,
                SECONDS);
    }
}
