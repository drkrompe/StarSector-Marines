package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frozen own-force picture plus the public place authored by the scenario. */
public final class OpeningOperationCommandFrame extends CommandFrame {
    private final OpeningOperationCommandFacts facts;
    private final int placeZoneId;
    private final List<CompanyReport> company;

    /** Friendly radio report only: no company contacts, orders, or enemy truth. */
    public record CompanyReport(int squadId, int cellX, int cellY, boolean ready, boolean infantry) { }

    private OpeningOperationCommandFrame(CommandFrame common,
                                         OpeningOperationCommandFacts facts,
                                         int placeZoneId, List<CompanyReport> company) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.placeZoneId = placeZoneId;
        this.company = List.copyOf(company);
    }

    static OpeningOperationCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments,
            OpeningOperationCommandFacts facts) {
        return new OpeningOperationCommandFrame(
                CommandFrame.freeze(sim, perspective, topology, assignments),
                facts, topology.zoneIdAt(facts.cellX(), facts.cellY()), companyReports(sim, perspective, facts));
    }

    private static List<CompanyReport> companyReports(BattleView sim, Faction perspective,
                                                       OpeningOperationCommandFacts facts) {
        if (perspective != Faction.ALLY || facts.kind() != OpeningOperationKind.COUNTERATTACK) return List.of();
        List<CompanyReport> reports = new ArrayList<>();
        for (var squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || sim.squadMemberCount(squad.id) == 0) continue;
            long anchor = sim.resolveUnit(squad.leaderId);
            if (anchor == 0L) anchor = sim.squadMemberAt(squad.id, 0);
            reports.add(new CompanyReport(squad.id, sim.world().cellX(anchor), sim.world().cellY(anchor),
                    !SquadFormUpSystem.formingUp(squad), sim.identity().type(anchor).usesInfantryTraining()));
        }
        reports.sort(Comparator.comparingInt(CompanyReport::squadId));
        return reports;
    }

    public List<CompanyReport> company() { return company; }
    public OpeningOperationCommandFacts facts() { return facts; }
    public int placeZoneId() { return placeZoneId; }
}
