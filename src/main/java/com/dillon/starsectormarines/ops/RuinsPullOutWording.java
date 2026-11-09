package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;

/**
 * A Ruins battle's Retreat is Pull out ({@code ruins-expedition.md}, Pressing on or pulling out): its confirmation
 * lists the banked finds that will come home and says the vault stays sealed. The circles it draws are the battle's
 * {@code withdrawalPads}.
 */
final class RuinsPullOutWording {

    private RuinsPullOutWording() {}

    /** The battle's wording for leaving, or null when it is not a Ruins battle. */
    static BattleRetreatOverlayModel.Wording of(BattleSimulation sim) {
        if (sim == null) return null;
        RuinsVaultObjective vault = RuinsPullOut.vault(sim);
        if (vault == null) return null;
        return of(sim.fieldSalvage().recovered());
    }

    static BattleRetreatOverlayModel.Wording of(List<SalvageCache.Recovered> banked) {
        String detail = banked.isEmpty() ? Strings.get("ruinsPullOutNothingBanked")
                : Strings.format("ruinsPullOutBanked", Strings.join("separatorDot", banked.stream()
                        .map(find -> Strings.format("resultsScreenFieldFind", find.quantity(),
                                find.resource().displayName))
                        .toArray(String[]::new)));
        return new BattleRetreatOverlayModel.Wording(Strings.get("ruinsPullOut"),
                Strings.get("ruinsPullOutConfirm"), detail, Strings.get("ruinsPullOut"));
    }
}
