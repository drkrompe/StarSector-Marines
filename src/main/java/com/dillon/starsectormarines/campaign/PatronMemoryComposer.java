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
        PatronEngagementMemory.History history =
                PatronEngagementMemory.history(state, patronHouseId);
        if (history == null
                || history.latest.sourceContractId == currentContractId) {
            return null;
        }
        if (history.previous != null) {
            PatronRelationshipPattern pattern = classify(
                    history.previous.outcome, history.latest.outcome);
            String[] continuityPool = PatronMemoryVoice.forPattern(pattern);
            if (continuityPool.length > 0) {
                long seed = currentContractId * 0x9E3779B97F4A7C15L
                        + history.latest.id * 31L + history.previous.id
                        + SEED_MIXER;
                return render(pick(continuityPool, seed),
                        patronDisplayName, history);
            }
        }
        return composeLatest(history, currentContractId, patronDisplayName);
    }

    static PatronRelationshipPattern classify(
            PatronEngagementOutcome previous,
            PatronEngagementOutcome latest) {
        if (latest == PatronEngagementOutcome.COMPLETED) {
            return previous == PatronEngagementOutcome.COMPLETED
                    ? PatronRelationshipPattern.SUCCESS_STREAK
                    : PatronRelationshipPattern.RECOVERY;
        }
        if (latest == PatronEngagementOutcome.EMPLOYER_BREACHED) {
            if (previous == PatronEngagementOutcome.COMPLETED) {
                return PatronRelationshipPattern.BREACH_AFTER_SUCCESS;
            }
            return previous == PatronEngagementOutcome.EMPLOYER_BREACHED
                    ? PatronRelationshipPattern.REPEATED_PATRON_BREACH
                    : PatronRelationshipPattern.MUTUAL_TROUBLE;
        }
        if (previous == PatronEngagementOutcome.COMPLETED) {
            return PatronRelationshipPattern.PLAYER_SETBACK;
        }
        return previous == PatronEngagementOutcome.EMPLOYER_BREACHED
                ? PatronRelationshipPattern.MUTUAL_TROUBLE
                : PatronRelationshipPattern.REPEATED_PLAYER_TROUBLE;
    }

    private static String composeLatest(PatronEngagementMemory.History history,
                                        long currentContractId,
                                        String patronDisplayName) {
        PatronEngagementMemory.Snapshot memory = history.latest;
        String[] pool = PatronMemoryVoice.forOutcome(memory.outcome);
        if (pool.length == 0) return null;
        long seed = currentContractId * 0x9E3779B97F4A7C15L
                + memory.id + SEED_MIXER;
        return render(pick(pool, seed), patronDisplayName, history);
    }

    private static String pick(String[] pool, long seed) {
        int index = Math.floorMod(new Random(seed).nextInt(), pool.length);
        return pool[index];
    }

    private static String render(String template, String patronDisplayName,
                                 PatronEngagementMemory.History history) {
        PatronEngagementMemory.Snapshot latest = history.latest;
        PatronEngagementMemory.Snapshot previous = history.previous;
        String patron = patronDisplayName != null
                && !patronDisplayName.trim().isEmpty()
                ? patronDisplayName : "this patron";
        return template
                .replace("{patron}", patron)
                .replace("{contract}", contractLabel(latest.contractType))
                .replace("{latestContract}",
                        contractLabel(latest.contractType))
                .replace("{latestOutcome}", outcomeLabel(latest.outcome))
                .replace("{previousContract}", previous != null
                        ? contractLabel(previous.contractType) : "")
                .replace("{previousOutcome}", previous != null
                        ? outcomeLabel(previous.outcome) : "")
                .replace("{priorCount}",
                        String.valueOf(history.engagementCount))
                .replace("{engagementNumber}",
                        String.valueOf(history.engagementCount + 1));
    }

    private static String contractLabel(ContractType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String outcomeLabel(PatronEngagementOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return "completion";
            case FAILED: return "failure";
            case WITHDREW: return "withdrawal";
            case EMPLOYER_BREACHED: return "employer breach";
            default: return "unknown result";
        }
    }
}
