package com.dillon.starsectormarines.campaign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/** Recent learned Chronicle entries projected for the Company HQ situation board. */
public final class CompanyNews {

    /** One validated learned fact. Display prose remains the Company HQ's concern. */
    public record Entry(
            long id,
            ChronicleEventType type,
            ChronicleConfidence confidence,
            ChronicleBand band,
            String actorName,
            String targetName,
            String marketName,
            ChainState chainOutcome,
            AbandonedColonyArchiveOutcome archiveOutcome,
            int survivorsAtRisk,
            int survivorsRescued,
            int happenedDay,
            int learnedDay) {
    }

    private CompanyNews() {
    }

    /**
     * Returns newest learned entries first. Rows learned in the future, malformed enum
     * values, and entries with no usable subject are skipped rather than guessed at.
     */
    public static List<Entry> latest(CampaignState state, int asOfDay, int limit,
                                     IntFunction<String> marketName) {
        if (state == null || asOfDay < 0 || limit <= 0) return List.of();
        IntFunction<String> markets = marketName != null ? marketName : ignored -> null;
        List<Entry> entries = new ArrayList<>();
        for (int row = 0; row < state.chronicleCount; row++) {
            Entry entry = read(state, row, asOfDay, markets);
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(Entry::learnedDay).reversed()
                .thenComparing(Comparator.comparingInt(Entry::happenedDay).reversed())
                .thenComparing(Comparator.comparingLong(Entry::id).reversed()));
        return entries.size() <= limit
                ? List.copyOf(entries)
                : List.copyOf(entries.subList(0, limit));
    }

    private static Entry read(CampaignState state, int row, int asOfDay,
                              IntFunction<String> marketName) {
        long id = state.chronicleId[row];
        int happened = state.chronicleHappenedTick[row];
        int learned = state.chronicleLearnedTick[row];
        if (id <= 0L || happened < 0 || learned < happened || learned > asOfDay) return null;

        ChronicleEventType type = safeEventType(state.chronicleEventType[row]);
        ChronicleConfidence confidence = safeConfidence(state.chronicleConfidence[row]);
        ChronicleBand band = safeBand(state.chronicleBand[row]);
        ChainState outcome = safeChainState(state.chronicleChainOutcome[row]);
        AbandonedColonyArchiveOutcome archive = safeArchive(
                state.chronicleColonyArchiveOutcome[row]);
        if (type == null || confidence == null || band == null) return null;

        String actor = houseName(state, state.chronicleActorHouseId[row]);
        String target = houseName(state, state.chronicleTargetHouseId[row]);
        if (type != ChronicleEventType.SILENT_COLONY
                && actor == null && target == null) {
            return null;
        }
        return new Entry(id, type, confidence, band, actor, target,
                marketName.apply(state.chronicleMarketId[row]), outcome, archive,
                state.chronicleSurvivorsAtRisk[row],
                state.chronicleSurvivorsRescued[row], happened, learned);
    }

    private static String houseName(CampaignState state, long houseId) {
        int row = state.houseIndex(houseId);
        if (row < 0) return null;
        String name = state.houseDisplayName[row];
        return name == null || name.isBlank() ? null : name;
    }

    private static ChronicleEventType safeEventType(byte value) {
        try {
            return ChronicleEventType.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ChronicleConfidence safeConfidence(byte value) {
        try {
            return ChronicleConfidence.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ChronicleBand safeBand(byte value) {
        try {
            return ChronicleBand.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ChainState safeChainState(byte value) {
        try {
            return ChainState.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AbandonedColonyArchiveOutcome safeArchive(byte value) {
        try {
            return AbandonedColonyArchiveOutcome.fromByte(value);
        } catch (RuntimeException ignored) {
            return AbandonedColonyArchiveOutcome.NONE;
        }
    }
}
