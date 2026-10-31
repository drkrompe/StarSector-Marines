package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Mission-supplied grounded construction of one build option through the ordinary command-card target flow. The
 * builders fly out, so any seen, open ground off the hull is a site. The host's supply pays for it, and no more than
 * {@link TenderPilot#MAX_SITES} sites may be in progress at once.
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
        if (source.metered() && source.stock() < option.cost()) return false;
        if (TenderPilot.liveSites(source) >= TenderPilot.MAX_SITES) return false;
        if (!Tender.everyCell(option, x, y, (px, py) -> battle.getGrid().inBounds(px, py)
                && battle.playerSeesCell(px, py))) return false;
        return source.siteOpen(option, x + 0.5f, y + 0.5f, battle.getGrid());
    }

    /** On supply, unavailable while the host cannot pay for this build; otherwise unlimited. */
    @Override public int availableCalls() {
        return source.metered() && source.stock() < option.cost() ? 0 : -1;
    }

    /** On supply, the card names the build's cost; what the host holds shows once, on Morrow's selected title. */
    @Override public String cardLabel(int chargesRemaining) {
        if (!source.metered()) return displayName;
        return Strings.format("yardConstructionCard", displayName, option.cost());
    }

    /** A staged build's plot, outlined under the cursor so a cramped spot shows before the click. */
    @Override public int previewSquareCells() {
        return option.plot() == null ? 0 : option.plot().span();
    }

    @Override public void resolve(int x, int y, CommandPowerService service, BattleControl battle) {
        source.lay(option, x + 0.5f, y + 0.5f, battle.getGrid());
    }
}
