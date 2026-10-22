package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/** The defender's ship warning, over the story beat the sim raised when the call was decided. */
public final class HostileShipCommsPresenter {
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
        if (HostileShipSupport.BEAT_INBOUND.equals(key)) {
            return new BattleCommsFeed.Notice(Strings.get("battleHostileCapitalInbound"),
                    Strings.get("battleCommsHostileShipBody"),
                    BattleCommsFeed.Tone.DANGER, HostileShipSupport.WARNING_SECONDS + 4f);
        }
        return null;
    }
}
