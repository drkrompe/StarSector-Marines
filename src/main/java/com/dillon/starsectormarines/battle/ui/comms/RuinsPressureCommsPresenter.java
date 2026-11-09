package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.ruins.RuinsPushes;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;

/**
 * The cue of a stir push ({@code ruins-pressure.md}, Cues): each push the site sends is a comms line in the
 * occupant's voice, headed by the level the site has reached, saying the layer it comes from. A way-back push's line
 * says it is behind the company, in the danger tone. It names no unit the company has not seen.
 */
public final class RuinsPressureCommsPresenter {
    /** Seconds a push's line stays up. */
    static final float SECONDS = 8f;

    private RuinsPressure current;
    private int posted;

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        if (sim == null || sim.isComplete()) return;
        RuinsPressure pressure = sim.ruinsPressure();
        if (pressure != current) {
            current = pressure;
            posted = 0;
        }
        if (pressure == null || pressure.fielded() == null) return;
        List<Push> pushes = pressure.pushes();
        if (posted >= pushes.size()) return;
        Push push = pushes.get(pushes.size() - 1);
        posted = pushes.size();
        feed.post(notice(pressure.fielded().voice(), push));
    }

    /** The line for one push. */
    public static BattleCommsFeed.Notice notice(RuinsPushes.Voice voice, Push push) {
        String heading = push.step() == 0 ? Strings.get("ruinsPushBreachTitle")
                : Strings.get(RuinsPressure.Level.values()[Math.min(push.step(),
                RuinsPressure.Level.values().length - 1)].key);
        String body = Strings.format(bodyKey(voice, push.aim()), push.layer() + 1);
        BattleCommsFeed.Tone tone = push.aim() == Aim.WAY_BACK ? BattleCommsFeed.Tone.DANGER
                : BattleCommsFeed.Tone.WARNING;
        return new BattleCommsFeed.Notice(heading, body, tone, SECONDS);
    }

    static String bodyKey(RuinsPushes.Voice voice, Aim aim) {
        String who = switch (voice) {
            case SECURITY -> "Security";
            case CREW -> "Crew";
            case SLEEPERS -> "Sleepers";
        };
        return "ruinsPush" + who + (aim == Aim.HUNT ? "Hunt" : "WayBack");
    }
}
