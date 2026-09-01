package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reserves persistent campaign-squad slots before their transports approach. */
final class FieldPresenceGate {

    private final UnitRosterService roster;
    private final World world;
    private final Set<String> admittedSquads = new LinkedHashSet<>();
    private FieldPresencePolicy policy = FieldPresencePolicy.UNRESTRICTED;

    FieldPresenceGate(UnitRosterService roster, World world) {
        this.roster = roster;
        this.world = world;
    }

    void setPolicy(FieldPresencePolicy policy) {
        this.policy = policy != null ? policy : FieldPresencePolicy.UNRESTRICTED;
        admittedSquads.clear();
    }

    /** Reconciles reservations against living marines and admitted inbound sorties. */
    void refresh(List<Long> air) {
        if (!policy.limited()) return;
        Set<String> occupied = new LinkedHashSet<>();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = roster.get(i);
            if (roster.identity().faction(unit) != Faction.MARINE) continue;
            String squadId = roster.identity().campaignSquadId(unit);
            if (squadId != null) occupied.add(squadId);
        }
        for (long craft : air) {
            ShuttleMission mission = world.mission(craft);
            if (mission == null || !mission.fieldPresenceAdmitted
                    || !stillInbound(mission)) continue;
            occupied.addAll(squadIds(mission));
        }
        admittedSquads.retainAll(occupied);
    }

    /** True when this sortie may begin its approach under the current cap. */
    boolean admit(ShuttleMission mission) {
        if (!policy.limited()) return true;
        Set<String> requested = squadIds(mission);
        // Employer/generated troops do not carry persistent company squad
        // identity and therefore do not consume the player's covert footprint.
        if (requested.isEmpty()) return true;
        int newSlots = 0;
        for (String squadId : requested) {
            if (!admittedSquads.contains(squadId)) newSlots++;
        }
        if (admittedSquads.size() + newSlots > policy.activeSquadLimit()) {
            return false;
        }
        admittedSquads.addAll(requested);
        mission.fieldPresenceAdmitted = true;
        return true;
    }

    private static boolean stillInbound(ShuttleMission mission) {
        return mission.marinesRemaining > 0
                && mission.state != ShuttleState.DEPARTING
                && mission.state != ShuttleState.GONE;
    }

    private static Set<String> squadIds(ShuttleMission mission) {
        Set<String> ids = new LinkedHashSet<>();
        if (mission == null || mission.marineLoadout == null) return ids;
        for (MarineLoadout loadout : mission.marineLoadout) {
            CampaignSquadTag tag = loadout != null ? loadout.campaignSquad : null;
            if (tag != null && tag.squadId != null) ids.add(tag.squadId);
        }
        return ids;
    }
}
