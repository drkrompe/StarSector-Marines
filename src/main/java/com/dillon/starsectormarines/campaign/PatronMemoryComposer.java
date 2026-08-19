package com.dillon.starsectormarines.campaign;

import java.util.Locale;
import java.util.Random;

/** Renders one honest comms-officer callback from persisted patron history. */
public final class PatronMemoryComposer {

    private static final long SEED_MIXER = 0x6A09E667F3BCC909L;

    private PatronMemoryComposer() {}

    public static String compose(CampaignState state, long patronHouseId,
                                 long currentContractId,
                                 String patronDisplayName) {
        PatronEngagementMemory.Snapshot memory =
                PatronEngagementMemory.latest(state, patronHouseId);
        if (memory == null || memory.sourceContractId == currentContractId) {
            return null;
        }
        String[] pool = PatronMemoryVoice.forOutcome(memory.outcome);
        if (pool.length == 0) return null;
        long seed = currentContractId * 0x9E3779B97F4A7C15L
                + memory.id + SEED_MIXER;
        int index = Math.floorMod(new Random(seed).nextInt(), pool.length);
        String patron = patronDisplayName != null
                && !patronDisplayName.trim().isEmpty()
                ? patronDisplayName : "this patron";
        return pool[index]
                .replace("{patron}", patron)
                .replace("{contract}", contractLabel(memory.contractType))
                .replace("{priorCount}",
                        String.valueOf(memory.priorEngagementCount));
    }

    private static String contractLabel(ContractType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
