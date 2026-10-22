package com.dillon.starsectormarines.campaign.bloom;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.systems.CampaignEventLifecycleSystem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rav Bloom campaign evidence ({@code colony-breach-nouns.md}, Campaign: blooms): runs {@link RavBloomSystem} with the
 * real event lifecycle over N cycles of a synthetic vanilla-shaped sector and reports, per preset
 * and player policy, how many blooms there were, how they ended, how far they spread and what
 * they cost. The sector is synthetic: forty core markets shaped like vanilla's sizes, factions,
 * industries and reach, a dozen outposts beyond the core, and three player colonies founded in
 * the first years. Markets the bloom shrinks regrow one size every two cycles towards their old
 * size, standing in for immigration.
 *
 * <p>Asserts only that the run completed and every bloom that ended has an ending; the numbers are
 * readings for the owner, not acceptance.
 */
@Tag("bloom-evidence")
class BloomEvidence {

    enum Policy {
        /** Never answers a call. */
        IGNORES,
        /** Answers about half the calls, late, with a middling evacuation; collapses the mother bore 30% of the time. */
        ANSWERS_HALF,
        /** Answers every call quickly; collapses the mother bore half the time. */
        ANSWERS_ALL
    }

    @Test
    void simulateYearsOfBlooms() throws IOException {
        int years = Integer.getInteger("bloom.evidence.years", 20);
        int seeds = Integer.getInteger("bloom.evidence.seeds", 8);
        Path out = Path.of(System.getProperty("bloom.evidence.outputDir", "build/reports/blooms"));
        Files.createDirectories(out);

        List<Row> rows = new ArrayList<>();
        for (BloomTuning.Severity severity : BloomTuning.Severity.values()) {
            for (BloomTuning.Frequency frequency : BloomTuning.Frequency.values()) {
                BloomTuning tuning = new BloomTuning(severity, frequency, BloomTuning.PlayerColonies.EXPOSED);
                for (Policy policy : Policy.values()) rows.add(run(tuning, policy, years, seeds));
            }
        }
        for (BloomTuning.PlayerColonies colonies : BloomTuning.PlayerColonies.values()) {
            if (colonies == BloomTuning.PlayerColonies.EXPOSED) continue;
            BloomTuning tuning = new BloomTuning(BloomTuning.DEFAULT.severity, BloomTuning.DEFAULT.frequency, colonies);
            for (Policy policy : Policy.values()) rows.add(run(tuning, policy, years, seeds));
        }
        Files.writeString(out.resolve("summary.md"), markdown(rows, years, seeds), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.json"), json(rows, years, seeds), StandardCharsets.UTF_8);
        System.out.println(markdown(rows, years, seeds));
        System.out.println("Rav Bloom evidence written to " + out.toAbsolutePath());
        assertTrue(rows.stream().allMatch(r -> r.unendedEnds == 0));
    }

    private static Row run(BloomTuning tuning, Policy policy, int years, int seeds) {
        Row row = new Row(tuning, policy, seeds, years);
        for (int seed = 1; seed <= seeds; seed++) runOne(row, tuning, policy, years, seed);
        return row;
    }

    private static void runOne(Row row, BloomTuning tuning, Policy policy, int years, long seed) {
        CampaignState state = new CampaignState();
        SyntheticSector world = new SyntheticSector(seed);
        RavBloomSystem system = new RavBloomSystem(world, tuning);
        CampaignEventLifecycleSystem lifecycle = new CampaignEventLifecycleSystem();
        SplittableRandom player = new SplittableRandom(seed * 7919L + policy.ordinal());
        Map<Long, int[]> arrivals = new HashMap<>(); // rescue event -> {day, evacuated share per mille, mother}
        int lastDay = years * 365;
        for (int day = 0; day <= lastDay; day++) {
            world.advance(day);
            lifecycle.tick(state, day);
            answer(state, policy, player, arrivals, day);
            system.tick(state, day);
        }
        RavBloomLedger ledger = state.ravBlooms();
        int lastWake = -1;
        Map<String, Integer> perWorld = new HashMap<>();
        for (RavBloom bloom : ledger.blooms()) {
            row.blooms++;
            if (bloom.generation == 0) {
                row.woken++;
                if (lastWake >= 0) row.wakeGaps.add(bloom.wakeDay - lastWake);
                lastWake = bloom.wakeDay;
            } else {
                row.seeded++;
            }
            row.maxGeneration = Math.max(row.maxGeneration, bloom.generation);
            perWorld.merge(bloom.marketId, 1, Integer::sum);
            if (bloom.rescueEventId() > 0L) row.callsRaised++;
            else if (bloom.eruptDay() >= 0) row.callsHeld++;
            if (bloom.quarantined()) row.quarantined++;
            if (bloom.evacuated() >= 0) {
                row.evacuated += bloom.evacuated();
                row.atRisk += bloom.atRisk();
            }
            row.sizeLost += bloom.sizeLost();
            if (world.get(bloom.marketId) != null && world.get(bloom.marketId).playerOwned) row.playerBlooms++;
            if (bloom.active()) row.running++;
            else if (bloom.end() == BloomEnd.NONE) row.unendedEnds++;
            else row.ends.merge(bloom.end(), 1, Integer::sum);
        }
        row.decivilized += world.decivilized.size();
        // What reaches the player's intel feed: a stage-change message at eruption, dark, starved
        // and end. The rumour while a bloom stirs is shown in the intel but sends no message.
        List<Integer> noticeDays = new ArrayList<>();
        for (RavBloom bloom : ledger.blooms()) {
            for (int day : new int[] {bloom.eruptDay(), bloom.darkDay(), bloom.starvedDay(), bloom.endDay()}) {
                if (day >= 0) noticeDays.add(day);
            }
        }
        noticeDays.sort(Integer::compare);
        row.notices += noticeDays.size();
        for (int i = 0, j = 0; i < noticeDays.size(); i++) {
            while (noticeDays.get(i) - noticeDays.get(j) >= 30) j++;
            row.peakNotices30 = Math.max(row.peakNotices30, i - j + 1);
        }
        for (int count : perWorld.values()) if (count > 1) row.repeatWorlds++;
    }

    /** The player's side: commit to calls the policy answers and report the evacuation on arrival. */
    private static void answer(CampaignState state, Policy policy, SplittableRandom random,
                               Map<Long, int[]> arrivals, int day) {
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row]) != CampaignEventType.CIVILIAN_RESCUE) continue;
            long id = state.eventId[row];
            CampaignEventState eventState = CampaignEventState.fromByte(state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE && !arrivals.containsKey(id)) {
                boolean answers = policy == Policy.ANSWERS_ALL
                        || (policy == Policy.ANSWERS_HALF && random.nextBoolean());
                if (!answers) {
                    arrivals.put(id, null);
                    if (policy != Policy.IGNORES) CivilianRescueEvent.refuse(state, id, day);
                    continue;
                }
                state.eventState[row] = CampaignEventState.COMMITTED.toByte();
                state.eventDecisionTick[row] = day;
                boolean fast = policy == Policy.ANSWERS_ALL;
                int travel = fast ? 10 + random.nextInt(21) : 15 + random.nextInt(31);
                int share = fast ? 500 + random.nextInt(501) : 300 + random.nextInt(651);
                int mother = random.nextDouble() < (fast ? 0.5 : 0.3) ? 1 : 0;
                arrivals.put(id, new int[] {day + travel, share, mother});
            } else if (eventState == CampaignEventState.COMMITTED) {
                int[] plan = arrivals.get(id);
                if (plan == null || day < plan[0]) continue;
                int rescued = (int) ((long) state.eventCiviliansAtRisk[row] * plan[1] / 1000L);
                CivilianRescueEvent.resolve(state, id, rescued, day);
                if (plan[2] == 1) state.ravBlooms().recordMotherBoreCollapsed(id);
            }
        }
    }

    /** One preset under one policy, summed over seeds. */
    static final class Row {
        final BloomTuning tuning;
        final Policy policy;
        final int seeds;
        final int years;
        int blooms, woken, seeded, maxGeneration, callsRaised, callsHeld, quarantined, sizeLost;
        int playerBlooms, running, unendedEnds, decivilized, repeatWorlds, notices, peakNotices30;
        long evacuated, atRisk;
        final List<Integer> wakeGaps = new ArrayList<>();
        final Map<BloomEnd, Integer> ends = new HashMap<>();

        Row(BloomTuning tuning, Policy policy, int seeds, int years) {
            this.tuning = tuning;
            this.policy = policy;
            this.seeds = seeds;
            this.years = years;
        }

        double perYear(int count) {
            return count / (double) (seeds * years);
        }

        int medianGap() {
            if (wakeGaps.isEmpty()) return -1;
            List<Integer> sorted = new ArrayList<>(wakeGaps);
            sorted.sort(Integer::compare);
            return sorted.get(sorted.size() / 2);
        }

        int end(BloomEnd end) {
            return ends.getOrDefault(end, 0);
        }
    }

    private static String markdown(List<Row> rows, int years, int seeds) {
        StringBuilder md = new StringBuilder();
        md.append("# Rav Bloom campaign evidence\n\n");
        md.append(String.format(Locale.ROOT, "%d cycles x %d save seeds per row, synthetic vanilla-shaped sector "
                + "(%d markets, %d of them core). Counts are per cycle, averaged over seeds; outcomes are totals.\n\n",
                years, seeds, SyntheticSector.marketCount(), SyntheticSector.coreCount()));
        md.append(volume(rows));
        md.append("## Every preset\n\n");
        md.append("| Severity | Frequency | Player colonies | Policy | Blooms/yr | Woken/yr | Seeded/yr | Median days between wakes | "
                + "Contained | Starved out | Put down | Burned out | Decivilised | Running at end | Size lost/yr | Max generation | "
                + "Worlds bloomed twice+ | Calls held at gate | Quarantined | Evacuated | Player colony blooms |\n");
        md.append("|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Row r : rows) {
            md.append(String.format(Locale.ROOT,
                    "| %s | %s | %s | %s | %.2f | %.2f | %.2f | %d | %d | %d | %d | %d | %d | %d | %.2f | %d | %d | %d | %d | %s | %d |\n",
                    r.tuning.severity, r.tuning.frequency, r.tuning.playerColonies, r.policy,
                    r.perYear(r.blooms), r.perYear(r.woken), r.perYear(r.seeded), r.medianGap(),
                    r.end(BloomEnd.CONTAINED), r.end(BloomEnd.STARVED_OUT), r.end(BloomEnd.PUT_DOWN),
                    r.end(BloomEnd.BURNED_OUT), r.end(BloomEnd.DECIVILIZED), r.running, r.perYear(r.sizeLost), r.maxGeneration,
                    r.repeatWorlds, r.callsHeld, r.quarantined,
                    r.atRisk > 0 ? Math.round(100.0 * r.evacuated / r.atRisk) + "%" : "-", r.playerBlooms));
        }
        return md.toString();
    }

    /** What the player is offered and told under the shipped defaults, per player policy. */
    private static String volume(List<Row> rows) {
        StringBuilder md = new StringBuilder("## Shipped defaults: what reaches the player\n\n");
        md.append("Calls are Distress Net offers (one open campaign event at a time, so they never overlap); "
                + "a call held at the gate never reached the player. Messages are the intel's stage-change "
                + "updates (eruption, dark, starved, ended).\n\n");
        md.append("| Policy | Blooms/yr | On player colonies | Calls offered/yr | Held at gate/yr | Messages/yr | "
                + "Most messages in any 30 days |\n|---|---:|---:|---:|---:|---:|---:|\n");
        for (Row r : rows) {
            if (r.tuning.severity != BloomTuning.DEFAULT.severity
                    || r.tuning.frequency != BloomTuning.DEFAULT.frequency
                    || r.tuning.playerColonies != BloomTuning.DEFAULT.playerColonies) continue;
            md.append(String.format(Locale.ROOT, "| %s | %.2f | %s | %.2f | %.2f | %.2f | %d |\n",
                    r.policy, r.perYear(r.blooms),
                    r.blooms > 0 ? Math.round(100.0 * r.playerBlooms / r.blooms) + "%" : "-",
                    r.perYear(r.callsRaised), r.perYear(r.callsHeld), r.perYear(r.notices), r.peakNotices30));
        }
        return md.append('\n').toString();
    }

    private static String json(List<Row> rows, int years, int seeds) {
        StringBuilder js = new StringBuilder();
        js.append("{\"years\":").append(years).append(",\"seeds\":").append(seeds).append(",\"rows\":[");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i > 0) js.append(',');
            js.append(String.format(Locale.ROOT,
                    "{\"severity\":\"%s\",\"frequency\":\"%s\",\"playerColonies\":\"%s\",\"policy\":\"%s\","
                            + "\"blooms\":%d,\"woken\":%d,\"seeded\":%d,\"medianWakeGapDays\":%d,"
                            + "\"contained\":%d,\"starvedOut\":%d,\"putDown\":%d,\"burnedOut\":%d,\"decivilized\":%d,\"running\":%d,"
                            + "\"sizeLost\":%d,\"maxGeneration\":%d,\"repeatWorlds\":%d,\"callsRaised\":%d,"
                            + "\"callsHeld\":%d,\"quarantined\":%d,\"evacuated\":%d,\"atRisk\":%d,\"playerBlooms\":%d,"
                            + "\"messages\":%d,\"peakMessages30Days\":%d}",
                    r.tuning.severity, r.tuning.frequency, r.tuning.playerColonies, r.policy,
                    r.blooms, r.woken, r.seeded, r.medianGap(), r.end(BloomEnd.CONTAINED),
                    r.end(BloomEnd.STARVED_OUT), r.end(BloomEnd.PUT_DOWN), r.end(BloomEnd.BURNED_OUT), r.end(BloomEnd.DECIVILIZED),
                    r.running, r.sizeLost, r.maxGeneration, r.repeatWorlds, r.callsRaised, r.callsHeld,
                    r.quarantined, r.evacuated, r.atRisk, r.playerBlooms, r.notices, r.peakNotices30));
        }
        return js.append("]}\n").toString();
    }
}
