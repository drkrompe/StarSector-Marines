package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Save-owned commercial commitment and exactly-once job ledger. No campaign API handles. */
public final class BrokenSupplyRouteProgress implements Serializable {
    public static final String PREFIX = "supply_route_v1_";
    public static final int JUNCTION = 1, LIFT = 2, RELAY = 3, THUNDER = 4;
    private int version = 1;
    private final String planetId;
    private final String planetName;
    private final String systemName;
    private boolean junctionWon;
    private int branch;
    private boolean closed;
    private boolean successful;
    private int attempt = 1;
    private int job;
    private String activeId;
    private boolean relayWon;
    private FreightRunProgress freight;
    private LastLiftProgress storyCrew;
    private LastLiftProgress recurringCrew;
    private int storyPayment;
    private int recurringPayment;
    private int jobsCompleted;
    private int recurringRescued;
    private int recurringDead;
    private String lastMissionId;
    private boolean lastVictory;
    private int lastPayment;

    public BrokenSupplyRouteProgress(String planetId, String planetName, String systemName) {
        if (planetId == null || planetId.isBlank() || planetName == null || planetName.isBlank())
            throw new IllegalArgumentException("A frozen operation site is required");
        this.planetId = planetId;
        this.planetName = planetName;
        this.systemName = systemName == null ? "" : systemName;
    }

    public boolean readable() {
        return version == 1 && planetId != null && planetName != null && attempt > 0 && job >= 0
                && (branch == 0 || branch == LIFT || branch == RELAY)
                && (branch == 0 || junctionWon) && (!closed || junctionWon && branch != 0)
                && (!successful || closed) && (job == 0 || successful)
                && (!relayWon || branch == RELAY && freight != null)
                && (branch != LIFT || storyCrew != null) && (job == 0 || branch != LIFT || recurringCrew != null);
    }
    public String planetId() { return planetId; }
    public String planetName() { return planetName; }
    public String systemName() { return systemName; }
    public boolean choicePending() { return readable() && junctionWon && branch == 0; }
    public boolean junctionWon() { return junctionWon; }
    public int branch() { return branch; }
    public boolean closed() { return closed; }
    public boolean successful() { return successful; }
    public int job() { return job; }
    public int storyPayment() { return storyPayment; }
    public int recurringPayment() { return recurringPayment; }
    public int jobsCompleted() { return jobsCompleted; }
    public int recurringRescued() { return recurringRescued; }
    public int recurringDead() { return recurringDead; }
    public String lastMissionId() { return lastMissionId; }
    public boolean lastVictory() { return lastVictory; }
    public int lastPayment() { return lastPayment; }
    public LastLiftProgress storyCrew() { return storyCrew; }
    public LastLiftProgress crew() { return job == 0 ? storyCrew : recurringCrew; }
    public FreightRunProgress freight() { return freight; }
    public int operation() { return !junctionWon ? JUNCTION : branch == RELAY && relayWon && !closed ? THUNDER : branch; }
    public boolean available() {
        return readable() && !choicePending() && (!closed || successful)
                && (operation() != LIFT || crew() != null && crew().available())
                && (operation() != THUNDER || freight != null && freight.available());
    }

    /** Only an explicit support order commits. Inspection and generation never call this. */
    public boolean commit(int selected) {
        if (!choicePending() || (selected != LIFT && selected != RELAY)) return false;
        branch = selected;
        if (selected == LIFT) storyCrew = new LastLiftProgress(planetId + ":rill:story");
        return true;
    }

    public String missionId() {
        return available() ? PREFIX + operation() + "_" + job + "_" + attempt : null;
    }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }
    public boolean begin(String id) {
        if (!matches(id) || activeId != null) return false;
        if (operation() == LIFT) crew().beginAttempt();
        if (operation() == THUNDER) freight.beginAttempt();
        activeId = id;
        return true;
    }

    /** Pure displayed award; settlement independently verifies the frozen attempt. */
    public int payment(String id, boolean victory, LastLiftProgress.Outcome rescue) {
        return payment(id, victory, rescue, null);
    }
    public int payment(String id, boolean victory, LastLiftProgress.Outcome rescue, FreightRunProgress.Outcome cargo) {
        if (!matches(id) || !victory) return 0;
        return switch (operation()) {
            case JUNCTION -> 12_000;
            case RELAY -> job == 0 ? 16_000 : 12_000;
            case THUNDER -> cargo != null && freight.previewAttempt().equals(cargo.attempt()) && cargo.victory()
                    && freight.deliveredCount() + cargo.delivered().size() > 0
                    ? 6_000 + 4_000 * (freight.deliveredCount() + cargo.delivered().size()) : 0;
            case LIFT -> rescue != null && crew().previewAttempt().equals(rescue.attempt()) && rescue.victory()
                    ? 4_000 + 500 * (crew().rescuedCount() + rescue.rescued().size()) : 0;
            default -> 0;
        };
    }

    /** -1 refuses every writeback; zero is a valid defeated attempt. */
    public int settle(String id, boolean victory, LastLiftProgress.Outcome rescue) {
        return settle(id, victory, rescue, null);
    }
    public int settle(String id, boolean victory, LastLiftProgress.Outcome rescue, FreightRunProgress.Outcome cargo) {
        if (!matches(id) || !id.equals(activeId)) return -1;
        int kind = operation();
        if (kind != LIFT && rescue != null) return -1;
        if (kind == LIFT && (rescue == null || rescue.victory() != victory
                || !crew().previewAttempt().equals(rescue.attempt()))) return -1;
        if (kind != THUNDER && cargo != null) return -1;
        if (kind == THUNDER && (cargo == null || cargo.victory() != victory
                || !freight.previewAttempt().equals(cargo.attempt())
                || victory && freight.deliveredCount() + cargo.delivered().size() == 0)) return -1;
        int paid = payment(id, victory, rescue, cargo);
        if (kind == THUNDER) paid = freight.settle(cargo);
        if (kind == LIFT) paid = crew().settle(rescue);
        lastMissionId = id;
        lastVictory = victory;
        lastPayment = paid;
        if (job > 0 && rescue != null) {
            recurringRescued += rescue.rescued().size();
            recurringDead += rescue.dead().size();
        }
        activeId = null;
        attempt++;
        if (kind == JUNCTION) {
            if (victory) { junctionWon = true; storyPayment += paid; }
            return paid;
        }
        // Old completed records have job > 0 and retain their existing recurring work.
        if (kind == RELAY && job == 0 && victory) {
            relayWon = true; storyPayment += paid;
            freight = new FreightRunProgress(planetId + ":freight:story");
            return paid;
        }
        boolean resolved = victory || kind == LIFT && crew().isClosed() || kind == THUNDER && freight.isClosed();
        if (job == 0) {
            storyPayment += paid;
            if (resolved) { closed = true; successful = victory; }
        } else {
            recurringPayment += paid;
            if (resolved) jobsCompleted++;
        }
        if (resolved && successful) {
            job++;
            if (branch == LIFT) recurringCrew = new LastLiftProgress(planetId + ":crew:" + job);
        }
        return paid;
    }

    public String status() {
        if (!readable()) return "Unrecognized supply-route record; operations paused.";
        if (!junctionWon) return "Open the Junction / route access contested";
        if (branch == 0) return "Junction held / choose one support mobilization";
        if (!closed && relayWon) return "Relay Nine secured / Thunder Run pending. " + freight.summary() + ".";
        if (!closed) return branch == LIFT ? "Evacuation committed / recovery pending" : "Freight committed / relay contested";
        if (branch == LIFT) return "Last Lift: " + storyCrew.summary() + ". Relay Nine not undertaken; route suspended. "
                + (successful ? "Iona's Crew Recovery available." : "No recoverable people remain; recovery arrangement not established.");
        if (freight != null) return "Thunder Run: " + freight.summary() + ". "
                + (successful ? "Freight delivered / Mara's Route Security available." : "Freight lost / commercial route remains suspended.")
                + " Rill rescue not undertaken.";
        return "Relay Nine secured / route reopened. Mara's Route Security available. Rill rescue not undertaken.";
    }
}
