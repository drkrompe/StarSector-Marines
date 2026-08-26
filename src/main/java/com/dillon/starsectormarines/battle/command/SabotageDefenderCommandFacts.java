package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.List;

/** Identity-free installation facts legally disclosed to Sabotage defenders. */
public final class SabotageDefenderCommandFacts {

    public record Alarm(boolean active, int raisedTick, int expiresTick) { }

    public record Site(int index, String id, String name, int cellX, int cellY,
                       int zoneId, boolean complete, Alarm alarm) { }

    private final List<Site> sites;

    private SabotageDefenderCommandFacts(List<Site> sites) {
        this.sites = List.copyOf(sites);
    }

    static SabotageDefenderCommandFacts freeze(BattleView sim) {
        List<Site> sites = new ArrayList<>();
        for (Objective objective : sim.getObjectives()) {
            if (!(objective instanceof ChargeSiteObjective site)) continue;
            ChargeSiteObjective.SiteAlarm alarm =
                    site.defenderAlarm(sim.getSimTickIndex());
            sites.add(new Site(sites.size(), site.siteId(), site.displayName(),
                    site.cellX(), site.cellY(),
                    sim.getZoneGraph().zoneIdAt(site.cellX(), site.cellY()),
                    site.isComplete(), new Alarm(alarm.active(),
                    alarm.raisedTick(), alarm.expiresTick())));
        }
        return new SabotageDefenderCommandFacts(sites);
    }

    public List<Site> sites() { return sites; }

    public Site site(int index) {
        return index >= 0 && index < sites.size() ? sites.get(index) : null;
    }
}
