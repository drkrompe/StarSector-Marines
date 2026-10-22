package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.i18n.Strings;
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
                feed.post(new BattleCommsFeed.Notice(Strings.get("battleCommsSalvageSecuredTitle"),
                        Strings.format("battleCommsSalvageSecuredBody", cache.quantity, cache.resource.displayName),
                        BattleCommsFeed.Tone.GOOD_NEWS, 6));
                return;
            }
            if (cache.discovered() && discovered.add(cache.id)) {
                feed.post(new BattleCommsFeed.Notice(Strings.get("battleCommsSalvageFoundTitle"),
                        Strings.format("battleCommsSalvageFoundBody", cache.quantity, cache.resource.displayName),
                        BattleCommsFeed.Tone.STATUS, 8));
                return;
            }
        }
    }
}
