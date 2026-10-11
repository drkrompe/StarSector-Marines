package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, reviewable transfers between line formations. Never recruits or issues kit. */
public final class SquadConsolidation {
    private SquadConsolidation() { }

    public record Move(String soldierId, String sourceId, String targetId) { }
    public record Strength(String squadId, int readyBefore, int readyAfter, int wounded,
                           int vacantBefore, int vacantAfter) { }
    public record Plan(Set<String> recipients, Set<String> donors, List<Move> moves,
                       List<Strength> strengths, String reason) {
        public Plan {
            recipients = Set.copyOf(recipients);
            donors = Set.copyOf(donors);
            moves = List.copyOf(moves);
            strengths = List.copyOf(strengths);
        }
        public boolean canApply() { return reason.isEmpty() && !moves.isEmpty(); }
    }

    public static Plan preview(MarineRoster roster, Set<String> recipients, Set<String> donors) {
        String reason = recipients.isEmpty() ? "Select receiving squads first."
                : donors.isEmpty() ? "Mark the squads that may supply personnel."
                : !Collections.disjoint(recipients, donors) ? "A squad cannot give and receive in the same order." : "";
        for (String id : union(recipients, donors)) {
            MarineSquad squad = roster.squadById(id);
            if (squad == null || squad.reserve() || squad.stationed()) {
                reason = "Only available line squads may give or receive personnel.";
                break;
            }
        }
        if (!reason.isEmpty()) return new Plan(recipients, donors, List.of(), List.of(), reason);
        List<Move> moves = new ArrayList<>();
        List<MarineSoldier> available = new ArrayList<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (MarineSquad squad : roster.squads()) {
            if (!donors.contains(squad.id())) continue;
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE) {
                    available.add(soldier);
                    sources.put(soldier.id(), squad.id());
                }
            }
        }
        int next = 0;
        Map<String, Integer> delta = new LinkedHashMap<>();
        for (MarineSquad target : roster.squads()) {
            if (!recipients.contains(target.id())) continue;
            for (int vacancy = 0; vacancy < roster.vacancies(target) && next < available.size(); vacancy++) {
                MarineSoldier soldier = available.get(next++);
                String source = sources.get(soldier.id());
                moves.add(new Move(soldier.id(), source, target.id()));
                delta.merge(source, -1, Integer::sum);
                delta.merge(target.id(), 1, Integer::sum);
            }
        }
        List<Strength> strengths = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (!recipients.contains(squad.id()) && !donors.contains(squad.id())) continue;
            int ready = roster.readyCount(squad);
            int vacant = roster.vacancies(squad);
            int change = delta.getOrDefault(squad.id(), 0);
            strengths.add(new Strength(squad.id(), ready, ready + change,
                    MarineSquad.CAPACITY - vacant - ready, vacant, vacant - change));
        }
        return new Plan(recipients, donors, moves, strengths,
                moves.isEmpty() ? "No ready donors or receiving vacancies. Wounded soldiers keep their billets." : "");
    }

    /** Campaign-thread transaction: a changed preview is rejected before the first move. */
    public static boolean apply(MarineRoster roster, Plan reviewed) {
        if (reviewed == null || !reviewed.canApply()
                || !reviewed.equals(preview(roster, reviewed.recipients(), reviewed.donors()))) return false;
        for (Move move : reviewed.moves()) {
            if (!roster.transferSoldier(move.soldierId(), move.targetId()))
                throw new IllegalStateException("Preflighted personnel transfer changed during commit");
        }
        return true;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }
}
