package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Current twelve-billet formation, detached from the campaign roster. WIA
 * personnel retain their places; the historical MIA/KIA roll is counted beside
 * the formation rather than occupying a current fire-team position.
 */
public record SquadMuster(String squadId, String squadName, boolean stationed,
                          List<Billet> billets, int readyCount, int woundedCount,
                          int vacantCount, int missingCount, int killedCount,
                          float earliestRecoveryDays) {

    public enum State { READY, WOUNDED, VACANT }

    /** One current place, with plain value labels for ordinary retained hover UI. */
    public record Billet(int index, String marineId, String name, String rankLabel,
                         State state, float recoveryDays, boolean leader) {
        public Billet {
            Objects.requireNonNull(state, "billet state");
        }

        public int teamIndex() { return index / MarineSquad.TEAM_SIZE; }

        public String hoverText() {
            if (state == State.VACANT) return "Vacant billet";
            String identity = rankLabel + " " + name;
            if (state == State.WOUNDED) {
                return identity + " | WIA | Return in "
                        + String.format(Locale.ROOT, "%.1f days", recoveryDays);
            }
            return identity + " | Ready" + (leader ? " | Squad NCO" : "");
        }
    }

    public SquadMuster {
        Objects.requireNonNull(squadId, "squad identity");
        Objects.requireNonNull(squadName, "squad name");
        billets = List.copyOf(billets);
        if (billets.size() != MarineSquad.CAPACITY) {
            throw new IllegalArgumentException("a squad muster requires twelve billets");
        }
    }

    /**
     * Pure read projection using the roster's current billet order. A missing
     * identity or the reserve holding pool has no formation and returns null.
     * Recovery values are remaining campaign days; -1 means no recovery clock.
     */
    public static SquadMuster from(MarineRoster roster, String squadId, float currentDay) {
        Objects.requireNonNull(roster, "roster");
        if (!Float.isFinite(currentDay)) {
            throw new IllegalArgumentException("campaign day must be finite");
        }
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return null;

        List<String> members = roster.manningMemberIds(squad);
        List<Billet> billets = new ArrayList<>(MarineSquad.CAPACITY);
        int ready = 0;
        int wounded = 0;
        float earliest = -1f;
        for (int index = 0; index < MarineSquad.CAPACITY; index++) {
            MarineSoldier marine = index < members.size()
                    ? roster.soldierById(members.get(index)) : null;
            if (marine == null) {
                billets.add(new Billet(index, null, "Vacant", "", State.VACANT, -1f, false));
                continue;
            }
            boolean recovering = marine.status() == MarineSoldierStatus.WIA;
            float days = recovering ? Math.max(0f, marine.unavailableUntilDay() - currentDay) : -1f;
            if (recovering) {
                wounded++;
                earliest = earliest < 0f ? days : Math.min(earliest, days);
            } else {
                ready++;
            }
            billets.add(new Billet(index, marine.id(), marine.name(),
                    marine.enlistedRank().abbreviation(),
                    recovering ? State.WOUNDED : State.READY, days,
                    marine.id().equals(squad.leaderSoldierId())));
        }

        int missing = 0;
        int killed = 0;
        for (MarineSoldier marine : roster.squadMembers(squad)) {
            if (marine.status() == MarineSoldierStatus.MIA) missing++;
            else if (marine.status() == MarineSoldierStatus.KIA) killed++;
        }
        return new SquadMuster(squad.id(), squad.name(), squad.stationed(), billets,
                ready, wounded, MarineSquad.CAPACITY - ready - wounded,
                missing, killed, earliest);
    }
}
