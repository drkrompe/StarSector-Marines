package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.ConstructionOrder;
import com.dillon.starsectormarines.battle.tender.Tender;

/** Mission-supplied grounded construction through the ordinary command-card target flow. */
public final class YardConstruction extends CommandPower {
    private final Tender source;

    public YardConstruction(Tender source) {
        super("yard-construction", "Build Vulcan Turret", 0f, 0f);
        if (source == null || !source.grounded()) throw new IllegalArgumentException("grounded tender required");
        this.source = source;
    }

    @Override public boolean canTarget(int x, int y, BattleView battle) {
        if (!super.canTarget(x, y, battle) || !source.anchorAlive()) return false;
        if (source.metered() && source.stock() < BuildOption.VULCAN_TURRET.cost()) return false;
        if (!source.groundSiteReachable(x + 0.5f, y + 0.5f, battle.getGrid())) return false;
        if (source.groundPlotReserved(x + 0.5f, y + 0.5f)) return false;
        for (ConstructionOrder order : source.orders()) {
            if (!order.phase().terminal() && Math.hypot(order.siteX() - x - 0.5f,
                    order.siteY() - y - 0.5f) < Tender.SITE_SPACING) return false;
        }
        return true;
    }

    @Override public void resolve(int x, int y, CommandPowerService service, BattleControl battle) {
        source.lay(BuildOption.VULCAN_TURRET, x + 0.5f, y + 0.5f, battle.getGrid());
    }
}
