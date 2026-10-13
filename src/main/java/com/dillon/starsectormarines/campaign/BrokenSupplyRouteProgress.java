package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Save-owned chapter progression and exactly-once job ledger. No campaign API handles. */
public final class BrokenSupplyRouteProgress implements Serializable {
    public static final String PREFIX = "supply_route_v1_";
    // LIFT remains a detached scenario identifier; RELAY identifies recurring Route Security.
    public static final int JUNCTION = 1, LIFT = 2, RELAY = 3, THUNDER = 4;
    private static final long serialVersionUID = 1L;
    private int version = 3;
    private final String planetId;
    private final String planetName;
    private final String systemName;
    private boolean junctionWon;
    private boolean closed;
    private boolean successful;
    private int attempt = 1;
    private int job;
    private String activeId;
    private FreightRunProgress freight;
    private int storyPayment;
    private int recurringPayment;
    private int jobsCompleted;
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
        return version == 3 && planetId != null && planetName != null && attempt > 0 && job >= 0
                && (junctionWon == (freight != null)) && (!closed || junctionWon)
                && (!successful || closed) && (job == 0 || successful);
    }
    public String planetId() { return planetId; }
    public String planetName() { return planetName; }
    public String systemName() { return systemName; }
    public boolean junctionWon() { return junctionWon; }
    public boolean closed() { return closed; }
    public boolean successful() { return successful; }
    public int job() { return job; }
    public int storyPayment() { return storyPayment; }
    public int recurringPayment() { return recurringPayment; }
    public int jobsCompleted() { return jobsCompleted; }
    public String lastMissionId() { return lastMissionId; }
    public boolean lastVictory() { return lastVictory; }
    public int lastPayment() { return lastPayment; }
    public FreightRunProgress freight() { return freight; }
    public int operation() { return !junctionWon ? JUNCTION : job > 0 ? RELAY : THUNDER; }
    public boolean available() {
        return readable() && (!closed || successful)
                && (operation() != THUNDER || freight.available());
    }
    public String missionId() {
        return available() ? PREFIX + operation() + "_" + job + "_" + attempt : null;
    }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }
    public boolean begin(String id) {
        if (!matches(id) || activeId != null) return false;
        if (operation() == THUNDER) freight.beginAttempt();
        activeId = id;
        return true;
    }

    /** Pure displayed award; settlement independently verifies the frozen attempt. */
    public int payment(String id, boolean victory, LastLiftProgress.Outcome rescue) {
        return payment(id, victory, rescue, null);
    }
    public int payment(String id, boolean victory, LastLiftProgress.Outcome rescue, FreightRunProgress.Outcome cargo) {
        if (!matches(id) || !victory || rescue != null) return 0;
        return switch (operation()) {
            case JUNCTION -> 12_000;
            case RELAY -> 12_000;
            case THUNDER -> cargo != null && freight.previewAttempt().equals(cargo.attempt()) && cargo.victory()
                    && freight.deliveredCount() + cargo.delivered().size() > 0
                    ? 6_000 + 4_000 * (freight.deliveredCount() + cargo.delivered().size()) : 0;
            default -> 0;
        };
    }

    /** -1 refuses every writeback; zero is a valid defeated attempt. */
    public int settle(String id, boolean victory, LastLiftProgress.Outcome rescue) {
        return settle(id, victory, rescue, null);
    }
    public int settle(String id, boolean victory, LastLiftProgress.Outcome rescue, FreightRunProgress.Outcome cargo) {
        if (!matches(id) || !id.equals(activeId) || rescue != null) return -1;
        int kind = operation();
        if (kind != THUNDER && cargo != null) return -1;
        if (kind == THUNDER && (cargo == null || cargo.victory() != victory
                || !freight.previewAttempt().equals(cargo.attempt())
                || victory && freight.deliveredCount() + cargo.delivered().size() == 0)) return -1;
        int paid = kind == THUNDER ? freight.settle(cargo) : payment(id, victory, null);
        lastMissionId = id;
        lastVictory = victory;
        lastPayment = paid;
        activeId = null;
        attempt++;
        if (kind == JUNCTION) {
            if (victory) {
                junctionWon = true;
                storyPayment += paid;
                freight = new FreightRunProgress(planetId + ":freight:story");
            }
        } else if (job == 0) {
            storyPayment += paid;
            if (victory || freight.isClosed()) {
                closed = true;
                successful = victory;
                if (successful) job++;
            }
        } else {
            recurringPayment += paid;
            if (victory) { jobsCompleted++; job++; }
        }
        return paid;
    }

    public String status() {
        if (!readable()) return "Unrecognized supply-route record; operations paused.";
        if (!junctionWon) return "Open the Junction / route access contested";
        if (!closed) return "Junction held / Thunder Run pending. " + freight.summary() + ".";
        return "Thunder Run: " + freight.summary() + ". "
                + (successful ? "Freight delivered / Mara's Route Security available." : "Freight lost / commercial route remains suspended.");
    }
}
