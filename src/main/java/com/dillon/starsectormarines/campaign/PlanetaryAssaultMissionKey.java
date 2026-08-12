package com.dillon.starsectormarines.campaign;

/** Stable mission identity for one Planetary Assault phase attempt. */
public final class PlanetaryAssaultMissionKey {

    public final long contractId;
    public final int phaseIndex;
    public final int attempt;

    private PlanetaryAssaultMissionKey(long contractId, int phaseIndex, int attempt) {
        this.contractId = contractId;
        this.phaseIndex = phaseIndex;
        this.attempt = attempt;
    }

    public static String encode(long contractId, int phaseIndex, int attempt) {
        return "contract:" + contractId + ":phase:" + phaseIndex + ":attempt:" + attempt;
    }

    public static PlanetaryAssaultMissionKey parse(String missionId) {
        if (missionId == null) return null;
        String[] parts = missionId.split(":", -1);
        if (parts.length != 6 || !"contract".equals(parts[0])
                || !"phase".equals(parts[2]) || !"attempt".equals(parts[4])) {
            return null;
        }
        try {
            long contractId = Long.parseLong(parts[1]);
            int phaseIndex = Integer.parseInt(parts[3]);
            int attempt = Integer.parseInt(parts[5]);
            if (contractId < 0L || phaseIndex < 0 || attempt < 0) return null;
            return new PlanetaryAssaultMissionKey(contractId, phaseIndex, attempt);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
