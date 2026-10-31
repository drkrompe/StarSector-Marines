package com.dillon.starsectormarines.battle.story;

/**
 * District Escort's physical facts: the company links up with a fresh consignment at the
 * Low Quay loading lane and covers it, with its two-person handover party, to the western
 * apron. Nothing here is inferred from enemy casualties; a receipt is only what happened
 * to the carrier, the party and the company.
 */
public final class DistrictEscortProgress {
    public enum Phase { LINK_UP, ESCORT, COMPLETE, FAILED }
    /** The two named people who travel with each consignment. */
    public static final int PARTY = 2;
    /** Constables who hold the western apron; identical on either agreement. */
    public static final int CONSTABLES = 4;
    /** Uncontested company cover at the carrier before it moves. */
    public static final float LINK_SECONDS = 4;

    /** The frozen receipt one attempt hands its settlement. */
    public record Report(boolean linkedUp, boolean cargoDelivered, boolean partyDelivered, boolean companyAtEgress,
                         boolean cargoLost, int partyLost, int constablesLost) {
        public boolean complete() {
            return linkedUp && cargoDelivered && partyDelivered && companyAtEgress && !cargoLost && partyLost < PARTY;
        }
    }

    private Phase phase = Phase.LINK_UP;
    private float link;
    private boolean linkedUp, delivered, partyDelivered, companyAtEgress, cargoLost, covered;

    public Phase phase() { return phase; }
    public float link() { return link; }
    public boolean covered() { return covered; }
    public boolean linkedUp() { return linkedUp; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    /** An outright win, every objective met and nobody lost: what the Debug key's Win reports. */
    public Report wonReport() { return new Report(true, true, true, true, false, 0, 0); }
    public Report report(int partyLost, int constablesLost) {
        return new Report(linkedUp, delivered, partyDelivered, companyAtEgress, cargoLost, partyLost, constablesLost);
    }

    /**
     * One tick of physical facts. {@code cover} means company infantry is with the carrier and
     * no hostile combatant has it in sight; delivery needs the carrier, a living party member and
     * the company together at the apron under that same cover.
     */
    public void advance(float dt, boolean companyInPlay, int partyAlive, boolean cargoAlive, boolean cover,
                        boolean cargoAtEgress, boolean partyAtEgress, boolean companyAtApron) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (partyAlive < 0 || partyAlive > PARTY) throw new IllegalArgumentException("Unknown party count");
        if (terminal()) return;
        covered = cover;
        cargoLost = !cargoAlive;
        if (!companyInPlay || cargoLost || partyAlive == 0) { phase = Phase.FAILED; return; }
        if (phase == Phase.LINK_UP) {
            link = cover ? link + dt : 0;
            if (link >= LINK_SECONDS) { linkedUp = true; phase = Phase.ESCORT; }
            return;
        }
        if (cargoAtEgress && partyAtEgress && companyAtApron && cover) {
            delivered = true; partyDelivered = true; companyAtEgress = true; phase = Phase.COMPLETE;
        }
    }
}
