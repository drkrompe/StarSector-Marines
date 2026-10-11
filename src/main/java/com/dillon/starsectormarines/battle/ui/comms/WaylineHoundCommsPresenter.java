package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;

/** Optional instructions over confirmed recovery events; never takes control for the player. */
public final class WaylineHoundCommsPresenter {
    private BattleSimulation observed;
    private int cursor;

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        if (observed != sim) { observed = sim; cursor = 0; }
        if (sim == null) return;
        for (var event : sim.getPlayerStoryBeatsAfter(cursor)) {
            cursor = event.sequence();
            if (sim.isComplete()) continue;
            BattleCommsFeed.Notice notice = notice(event.id());
            if (notice != null) feed.post(notice);
        }
    }

    static BattleCommsFeed.Notice notice(String key) {
        return switch (key) {
            case WaylineHoundRecovery.FOUND -> new BattleCommsFeed.Notice("WAYLINE // WAYWARD LOCATED",
                    "Clear the marked berth. Keep infantry nearby for five seconds to bring the Hound online.",
                    BattleCommsFeed.Tone.GOOD_NEWS, 12f);
            case WaylineHoundRecovery.READY -> new BattleCommsFeed.Notice("WAYWARD // OPTIONAL DIRECT CONTROL",
                    "Select Wayward: Tab to control. WASD moves; mouse aims, LMB fires. Tab / Esc hands back.",
                    BattleCommsFeed.Tone.GOOD_NEWS, 18f);
            case WaylineHoundRecovery.LOST -> new BattleCommsFeed.Notice("WAYLINE // WALKER DISABLED",
                    "Keep pushing. Win the depot and we'll recover and repair Wayward for your company.",
                    BattleCommsFeed.Tone.WARNING, 12f);
            default -> null;
        };
    }
}
