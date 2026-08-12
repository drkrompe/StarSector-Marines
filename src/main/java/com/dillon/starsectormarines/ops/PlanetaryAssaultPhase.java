package com.dillon.starsectormarines.ops;

/** Pure phase sequence and staged-economy policy for Planetary Assault. */
public final class PlanetaryAssaultPhase {

    private static final int NON_FINAL_PAYOUT_PERCENT = 15;

    public final int index;
    public final int total;
    public final MissionType missionType;
    public final String title;
    public final int payout;
    public final byte salvageBaseline;
    public final byte salvageNegotiated;

    private PlanetaryAssaultPhase(int index, int total, MissionType missionType,
                                  String title, int payout, int salvageBaseline,
                                  int salvageNegotiated) {
        this.index = index;
        this.total = total;
        this.missionType = missionType;
        this.title = title;
        this.payout = payout;
        this.salvageBaseline = (byte) salvageBaseline;
        this.salvageNegotiated = (byte) salvageNegotiated;
    }

    public boolean isFinal() {
        return index == total - 1;
    }

    public static PlanetaryAssaultPhase create(int phaseIndex, int phasesTotal,
                                               int totalPayout,
                                               int contractSalvageBaseline,
                                               int contractSalvageNegotiated) {
        if (phasesTotal < 3 || phasesTotal > 5
                || phaseIndex < 0 || phaseIndex >= phasesTotal
                || totalPayout < 0) {
            return null;
        }
        boolean finalPhase = phaseIndex == phasesTotal - 1;
        int payout = finalPhase
                ? totalPayout - stagedPayout(totalPayout) * (phasesTotal - 1)
                : stagedPayout(totalPayout);
        int baseline = Math.max(0, Math.min(255, contractSalvageBaseline));
        int phaseBaseline = finalPhase
                ? baseline : baseline * (phaseIndex + 1) / phasesTotal;
        int negotiated = Math.max(0, Math.min(phaseBaseline,
                Math.min(255, contractSalvageNegotiated)));

        MissionType missionType;
        String title;
        if (phaseIndex == 0) {
            missionType = MissionType.SABOTAGE;
            title = "Recon";
        } else if (phaseIndex == 1) {
            missionType = MissionType.RAID;
            title = "Softening Strike";
        } else if (phaseIndex == 2) {
            missionType = MissionType.CONQUEST;
            title = "Main Assault";
        } else if (finalPhase) {
            missionType = MissionType.ASSAULT;
            title = "Consolidation";
        } else {
            missionType = MissionType.ASSAULT;
            title = "Mop-up";
        }
        return new PlanetaryAssaultPhase(phaseIndex, phasesTotal, missionType,
                title, Math.max(0, payout), phaseBaseline, negotiated);
    }

    private static int stagedPayout(int totalPayout) {
        return (int) ((long) totalPayout * NON_FINAL_PAYOUT_PERCENT / 100L);
    }
}
