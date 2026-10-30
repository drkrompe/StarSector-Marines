package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.ConstructionOrder;
import com.dillon.starsectormarines.battle.tender.Tender;

import java.util.ArrayList;
import java.util.List;

/**
 * Mission-supplied grounded construction of one build option through the ordinary command-card target flow. The
 * builders fly out, so any seen, open ground off the hull is a site.
 */
public final class YardConstruction extends CommandPower {
    /** The id prefix every yard power shares; the option's own id follows it. */
    public static final String ID_PREFIX = "yard-construction-";

    private final Tender source;
    private final BuildOption option;

    public YardConstruction(Tender source, BuildOption option) {
        super(ID_PREFIX + option.id(), "Build " + option.label(), 0f, 0f);
        if (source == null || !(source.grounded() || source.lowHover())) throw new IllegalArgumentException("recovery tender required");
        if (!source.options().contains(option)) throw new IllegalArgumentException(option.id() + " is not a recovery build");
        this.source = source;
        this.option = option;
    }

    /** One power for each build the recovery host offers, in its own order. */
    public static List<YardConstruction> forEachOption(Tender source) {
        List<YardConstruction> powers = new ArrayList<>();
        for (BuildOption option : source.options()) powers.add(new YardConstruction(source, option));
        return powers;
    }

    /** Exact mission-owned drone bay; UI association does not grant another source access. */
    public Tender source() { return source; }

    public BuildOption option() { return option; }

    @Override public boolean canTarget(int x, int y, BattleView battle) {
        if (!super.canTarget(x, y, battle) || !source.anchorAlive()) return false;
        if (!battle.getGrid().isWalkable(x, y) || !battle.playerSeesCell(x, y)) return false;
        if (source.metered() && source.stock() < option.cost()) return false;
        if (!source.buildableSite(x + 0.5f, y + 0.5f)) return false;
        if (source.groundPlotReserved(x + 0.5f, y + 0.5f)) return false;
        for (ConstructionOrder order : source.orders()) {
            if (!order.phase().terminal() && Math.hypot(order.siteX() - x - 0.5f,
                    order.siteY() - y - 0.5f) < Tender.SITE_SPACING) return false;
        }
        return true;
    }

    @Override public void resolve(int x, int y, CommandPowerService service, BattleControl battle) {
        source.lay(option, x + 0.5f, y + 0.5f, battle.getGrid());
    }
}
