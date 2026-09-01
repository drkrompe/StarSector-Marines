package com.dillon.starsectormarines.marine;

import java.util.List;

/** Atomic campaign authority for spending cargo and founding a complete line squad. */
public final class SquadFoundingWorkshop {

    private final MarineRoster roster;
    private final SquadFoundingResources resources;

    public SquadFoundingWorkshop(MarineRoster roster, SquadFoundingResources resources) {
        if (roster == null || resources == null) {
            throw new IllegalArgumentException("roster and founding resources are required");
        }
        this.roster = roster;
        this.resources = resources;
    }

    public Result foundSquad() {
        if (!resources.spend(SquadFoundingCost.STANDARD)) {
            return new Result(Status.INSUFFICIENT_RESOURCES, null);
        }
        MarineSquad squad = roster.createSquad();
        List<MarineSoldier> recruits = roster.recruitToSquad(
                squad.id(), MarineSquad.CAPACITY);
        if (recruits.size() != MarineSquad.CAPACITY) {
            throw new IllegalStateException(
                    "validated founding transaction did not fill its line squad");
        }
        return new Result(Status.FOUNDED, squad);
    }

    public enum Status { FOUNDED, INSUFFICIENT_RESOURCES }

    public record Result(Status status, MarineSquad squad) {
        public boolean succeeded() { return status == Status.FOUNDED; }
    }
}
