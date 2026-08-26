package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Objective and own-force task facts legally disclosed to Sabotage command. */
public final class SabotageCommandFacts {

    public record Site(
            int index,
            String id,
            String name,
            int cellX,
            int cellY,
            int zoneId,
            float progress,
            float plantDuration,
            boolean planterOnSite,
            boolean complete,
            List<Integer> planterSquadIds,
            List<Integer> retrieverSquadIds) {

        public Site {
            planterSquadIds = List.copyOf(planterSquadIds);
            retrieverSquadIds = List.copyOf(retrieverSquadIds);
        }
    }

    private final List<Site> sites;

    private SabotageCommandFacts(List<Site> sites) {
        this.sites = List.copyOf(sites);
    }

    /** Sole live objective/task projection used by migrated Sabotage strategies. */
    static SabotageCommandFacts freeze(BattleView sim) {
        List<ChargeSiteObjective> objectives = new ArrayList<>();
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof ChargeSiteObjective site) objectives.add(site);
        }

        List<Set<Integer>> planterSquads = new ArrayList<>(objectives.size());
        List<Set<Integer>> retrieverSquads = new ArrayList<>(objectives.size());
        for (int i = 0; i < objectives.size(); i++) {
            planterSquads.add(new LinkedHashSet<>());
            retrieverSquads.add(new LinkedHashSet<>());
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(unit)) continue;
            int squadId = sim.squad().squadId(unit);
            UnitRole role = sim.role().role(unit);
            if (role == UnitRole.PLANTER
                    && sim.task().assignedObjective(unit)
                    instanceof ChargeSiteObjective site) {
                int siteIndex = identityIndex(objectives, site);
                if (siteIndex >= 0 && !site.isComplete()) {
                    planterSquads.get(siteIndex).add(squadId);
                }
            } else if (role == UnitRole.KIT_RETRIEVER) {
                EquipmentDrop drop = sim.task().equipmentDropTarget(unit);
                if (drop == null || !(drop.objective instanceof ChargeSiteObjective site)) {
                    continue;
                }
                int siteIndex = identityIndex(objectives, site);
                if (siteIndex >= 0 && !site.isComplete()) {
                    retrieverSquads.get(siteIndex).add(squadId);
                }
            }
        }

        List<Site> facts = new ArrayList<>(objectives.size());
        for (int i = 0; i < objectives.size(); i++) {
            ChargeSiteObjective site = objectives.get(i);
            facts.add(new Site(i, site.siteId(), site.displayName(), site.cellX(), site.cellY(),
                    sim.getZoneGraph().zoneIdAt(site.cellX(), site.cellY()),
                    site.progress(), site.plantDuration(), site.planterOnSite(),
                    site.isComplete(), new ArrayList<>(planterSquads.get(i)),
                    new ArrayList<>(retrieverSquads.get(i))));
        }
        return new SabotageCommandFacts(facts);
    }

    private static int identityIndex(List<ChargeSiteObjective> sites,
                                     ChargeSiteObjective target) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i) == target) return i;
        }
        return -1;
    }

    public List<Site> sites() {
        return sites;
    }

    public Site site(int index) {
        return index >= 0 && index < sites.size() ? sites.get(index) : null;
    }
}
