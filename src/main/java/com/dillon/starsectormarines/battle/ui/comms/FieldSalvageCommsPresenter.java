package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.salvage.FieldSalvage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import java.util.HashSet;
import java.util.Set;

/** Optional finds wait for an idle radio plate instead of displacing mission warnings. */
public final class FieldSalvageCommsPresenter {
    private FieldSalvage current;
    private final Set<String> discovered = new HashSet<>(), secured = new HashSet<>();

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        if (sim == null || sim.isComplete()) return;
        if (current != sim.fieldSalvage()) {
            current = sim.fieldSalvage(); discovered.clear(); secured.clear();
        }
        if (feed.activeNotice() != null) return;
        for (var cache : current.caches()) {
            if (cache.secured() && secured.add(cache.id)) {
                discovered.add(cache.id);
                feed.post(new BattleCommsFeed.Notice("FIELD SALVAGE // SECURED",
                        cache.quantity + " " + cache.resource.displayName + " tagged for recovery. Claim it after a successful operation.",
                        BattleCommsFeed.Tone.GOOD_NEWS, 6));
                return;
            }
            if (cache.discovered() && discovered.add(cache.id)) {
                feed.post(new BattleCommsFeed.Notice("FIELD SALVAGE // OPTIONAL FIND",
                        cache.quantity + " " + cache.resource.displayName + ". Move infantry to the marked cache and hold the area for five seconds to secure it.",
                        BattleCommsFeed.Tone.STATUS, 8));
                return;
            }
        }
    }
}
