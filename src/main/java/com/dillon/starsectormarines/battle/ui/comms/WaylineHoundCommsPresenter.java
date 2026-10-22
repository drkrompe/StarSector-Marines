package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;

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
            case WaylineHoundRecovery.FOUND -> new BattleCommsFeed.Notice(Strings.get("battleCommsWaylineFoundTitle"),
                    Strings.get("battleCommsWaylineFoundBody"),
                    BattleCommsFeed.Tone.GOOD_NEWS, 12f);
            case WaylineHoundRecovery.READY -> new BattleCommsFeed.Notice(Strings.get("battleCommsWaylineReadyTitle"),
                    Strings.get("battleCommsWaylineReadyBody"),
                    BattleCommsFeed.Tone.GOOD_NEWS, 18f);
            case WaylineHoundRecovery.LOST -> new BattleCommsFeed.Notice(Strings.get("battleCommsWaylineLostTitle"),
                    Strings.get("battleCommsWaylineLostBody"),
                    BattleCommsFeed.Tone.WARNING, 12f);
            case DepotHoldSequence.CAPTURED -> new BattleCommsFeed.Notice(Strings.get("battleCommsDepotCapturedTitle"),
                    Strings.get("battleCommsDepotCapturedBody"),
                    BattleCommsFeed.Tone.WARNING, 12f);
            case DepotHoldSequence.FIRST -> new BattleCommsFeed.Notice(Strings.get("battleCommsDepotFirstTitle"),
                    Strings.get("battleCommsDepotFirstBody"),
                    BattleCommsFeed.Tone.DANGER, 10f);
            case DepotHoldSequence.WARNING -> new BattleCommsFeed.Notice(Strings.get("battleCommsDepotWarningTitle"),
                    Strings.get("battleCommsDepotWarningBody"),
                    BattleCommsFeed.Tone.WARNING, 10f);
            case DepotHoldSequence.SECOND -> new BattleCommsFeed.Notice(Strings.get("battleCommsDepotSecondTitle"),
                    Strings.get("battleCommsDepotSecondBody"),
                    BattleCommsFeed.Tone.DANGER, 10f);
            case DepotHoldSequence.CONTESTED -> new BattleCommsFeed.Notice(Strings.get("battleCommsDepotContestedTitle"),
                    Strings.get("battleCommsDepotContestedBody"),
                    BattleCommsFeed.Tone.DANGER, 10f);
            default -> null;
        };
    }
}
