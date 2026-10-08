package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Whole-squad command rules shared by briefing and deployment UI.
 *
 * <p>Command is scoped <b>per officer</b>, not per operation: a selected
 * squad counts against its own home officer's {@code Rank.squadCommandCap},
 * and only a squad with no home officer counts against the operation's
 * commander. See {@link TaskForce} for why, and for the compatibility
 * argument — with nothing assigned, every squad falls to the commander and
 * these rules reduce exactly to the single-officer cap they replaced.
 */
public final class CaptainDeploymentPolicy {

    private CaptainDeploymentPolicy() {}

    /** Named home formation, or all available squads when using acting NCOs. */
    public static List<String> defaultSquadIds(MarineRoster roster,
                                               MarineCaptain captain) {
        if (roster == null) return Collections.emptyList();
        if (!canLead(captain)) {
            return roster.squads().stream().filter(s -> !s.reserve()
                    && roster.isSquadAvailable(s.id())).map(MarineSquad::id).toList();
        }
        List<String> result = new ArrayList<>();
        for (MarineSquad squad : roster.squadsCommandedBy(captain.id())) {
            if (result.size() >= captain.rank().squadCommandCap()) break;
            if (!roster.isSquadAvailable(squad.id())) continue;
            result.add(squad.id());
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean canLead(MarineCaptain captain) {
        return captain != null && captain.status() == Status.ACTIVE;
    }

    public static int selectedCount(MarineRoster roster, Set<String> squadIds) {
        if (roster == null || squadIds == null) return 0;
        int count = 0;
        for (String squadId : squadIds) {
            MarineSquad squad = roster.squadById(squadId);
            if (squad != null && !squad.reserve()) count++;
        }
        return count;
    }

    /**
     * Can this squad join the selection? Bounded by whoever would lead it —
     * its home officer, or the commander when it has none — rather than by
     * one officer's cap over the whole operation.
     */
    public static boolean canAdd(MarineRoster roster, MarineCaptain commander,
                                 Set<String> selectedIds, String squadId) {
        if (roster == null || squadId == null) return false;
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()
                || !roster.isSquadAvailable(squadId)) return false;
        if (selectedIds != null && selectedIds.contains(squadId)) return true;
        return true;
    }

    /**
     * The officer who would take this squad into the field: its home officer
     * when it has a fit one, otherwise the operation's commander.
     */
    public static MarineCaptain leaderFor(MarineRoster roster, MarineCaptain commander,
                                          String squadId) {
        if (roster == null || squadId == null) return commander;
        MarineCaptain home = roster.captainForSquad(squadId);
        return canLead(home) ? home : commander;
    }

    /** Stationing retains its requirement for fit named leadership. */
    public static boolean isValidNamedCommand(MarineRoster roster, MarineCaptain commander,
                                               Set<String> selectedIds) {
        if (!canLead(commander)) return false;
        TaskForce force = TaskForce.of(roster, commander, selectedIds);
        return force.isValid() && force.unledSquads() == 0;
    }

    /** Ordinary deployments allow independent acting NCO leadership. */
    public static boolean isValidCommand(MarineRoster roster,
                                         MarineCaptain commander,
                                         Set<String> selectedIds) {
        TaskForce force = TaskForce.of(roster, commander, selectedIds);
        if (force.squadCount() == 0) return true;
        return force.isValid();
    }
}
