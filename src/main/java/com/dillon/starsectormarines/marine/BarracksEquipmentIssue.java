package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Batch orchestration over the existing exact squad issue; no new equipment economy. */
public final class BarracksEquipmentIssue {
    private final MarineRoster roster;

    public BarracksEquipmentIssue(MarineRoster roster, EquipmentIssueResources resources) {
        this.roster = roster;
    }

    public Plan preview(Set<String> ids) {
        List<Entry> entries = new ArrayList<>();
        EquipmentTemplateCost total = EquipmentTemplateCost.ZERO;
        for (MarineSquad squad : roster.squads()) {
            if (!ids.contains(squad.id())) continue;
            SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
            String weapons = profile != null ? profile.weaponDoctrineId() : squad.weaponDoctrineId();
            String armor = profile != null ? profile.armorDoctrineId() : squad.armorDoctrineId();
            SquadEquipmentPreview preview = roster.previewSquadEquipment(squad.id(), weapons, armor,
                    EquipmentIssueResources.UNLIMITED);
            String reason = squad.reserve() ? "Reserve pool"
                    : squad.stationed() ? "On station"
                    : weapons == null || armor == null ? "Assign a profile first"
                    : !preview.canApply() ? "Required equipment not unlocked" : "";
            boolean eligible = reason.isEmpty();
            if (eligible) total = total.plus(preview.issueCost());
            entries.add(new Entry(squad.id(), weapons, armor, eligible, reason));
        }
        boolean valid = !ids.isEmpty() && entries.size() == ids.size();
        return new Plan(List.copyOf(entries), total, valid);
    }

    /** Revalidates all eligible squads before the Machine Shop fits their known equipment. */
    public Result apply(Set<String> ids) {
        Plan plan = preview(ids);
        if (!plan.affordable() || plan.eligible() == 0) return new Result(0, plan.skipped(), false);
        // Campaign commands run on one thread: nothing yields between preflight and commit.
        for (Entry entry : plan.entries()) {
            if (!entry.eligible()) continue;
            SquadEquipmentResult result = roster.applySquadEquipment(entry.squadId(),
                    entry.weapons(), entry.armor(), EquipmentIssueResources.UNLIMITED);
            if (result != SquadEquipmentResult.APPLIED) {
                throw new IllegalStateException("Preflighted squad issue changed during commit: " + result);
            }
        }
        return new Result(plan.eligible(), plan.skipped(), true);
    }

    public record Entry(String squadId, String weapons, String armor, boolean eligible, String reason) { }
    public record Plan(List<Entry> entries, EquipmentTemplateCost cost, boolean affordable) {
        public int eligible() { return (int) entries.stream().filter(Entry::eligible).count(); }
        public int skipped() { return entries.size() - eligible(); }
    }
    public record Result(int issued, int skipped, boolean succeeded) { }
}
