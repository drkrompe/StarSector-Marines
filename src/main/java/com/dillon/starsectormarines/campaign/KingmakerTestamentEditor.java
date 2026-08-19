package com.dillon.starsectormarines.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure deterministic editor for one sealed Last Testament snapshot. */
public final class KingmakerTestamentEditor {

    private static final int VERDICT_SIGNAL = 10;

    /** Presentation-owned display-name resolver; no runtime API enters the editor. */
    public interface Names {
        String house(long houseId);

        String market(int marketRegistryId);
    }

    /** One source-backed remembered deed in final player-facing prose. */
    public record Witness(MoralChoiceSource source, long sourceId, String text) {}

    /** Final immutable three-movement content consumed by a later intel surface. */
    public record Draft(String accusation, List<Witness> witnesses,
                        String verdict) {
        public Draft {
            witnesses = List.copyOf(witnesses);
        }
    }

    private enum Family {
        CIVIL_WAR,
        CIVILIAN_RESCUE,
        DEFECTOR_ASYLUM
    }

    private enum Axis {
        INTEGRITY,
        MERCY,
        STEWARDSHIP,
        INSTITUTIONALISM
    }

    private static final class Candidate {
        final Family family;
        final MoralChoiceSource source;
        final long sourceId;
        final long choiceId;
        final int happenedTick;
        final int weight;
        final String text;

        Candidate(Family family, MoralChoiceSource source, long sourceId,
                  long choiceId, int happenedTick, int weight, String text) {
            this.family = family;
            this.source = source;
            this.sourceId = sourceId;
            this.choiceId = choiceId;
            this.happenedTick = happenedTick;
            this.weight = weight;
            this.text = text;
        }
    }

    private KingmakerTestamentEditor() {}

    /** Returns an empty result instead of inventing copy from malformed facts. */
    public static Optional<Draft> edit(CampaignState state, int testamentRow,
                                       Names names) {
        if (!validTestament(state, testamentRow) || names == null) {
            return Optional.empty();
        }

        String claimant = nameOfHouse(names,
                state.kingmakerTestamentClaimantHouseId[testamentRow]);
        String deposed = nameOfHouse(names,
                state.kingmakerTestamentDeposedHouseId[testamentRow]);
        String market = nameOfMarket(names,
                state.kingmakerTestamentMarketId[testamentRow]);
        if (claimant == null || deposed == null || market == null) {
            return Optional.empty();
        }

        String accusation = "You helped " + claimant + " take " + market
                + " and cast " + deposed + " from power. Do not pretend you "
                + "were merely present when the old order fell.";
        List<Witness> witnesses = witnesses(state, testamentRow, names);
        String verdict = verdict(
                state.kingmakerTestamentMercy[testamentRow],
                state.kingmakerTestamentIntegrity[testamentRow],
                state.kingmakerTestamentStewardship[testamentRow],
                state.kingmakerTestamentInstitutionalism[testamentRow]);
        return Optional.of(new Draft(accusation, witnesses, verdict));
    }

    private static boolean validTestament(CampaignState state, int row) {
        if (state == null || row < 0 || row >= state.kingmakerTestamentCount
                || state.kingmakerTestamentId[row] <= 0L
                || state.kingmakerTestamentThroneClaimId[row] <= 0L
                || state.kingmakerTestamentSourceChainId[row] <= 0L
                || state.kingmakerTestamentClaimantHouseId[row] <= 0L
                || state.kingmakerTestamentDeposedHouseId[row] <= 0L
                || state.kingmakerTestamentClaimantHouseId[row]
                    == state.kingmakerTestamentDeposedHouseId[row]
                || state.kingmakerTestamentSourceFactionId[row] < 0
                || state.kingmakerTestamentResultFactionId[row] < 0
                || state.kingmakerTestamentSourceFactionId[row]
                    == state.kingmakerTestamentResultFactionId[row]
                || state.kingmakerTestamentMarketId[row] < 0
                || state.kingmakerTestamentSealedTick[row] < 0
                || (state.kingmakerTestamentPlayerContribution[row] & 0xFFFF)
                    < 60
                || !validAxis(state.kingmakerTestamentMercy[row])
                || !validAxis(state.kingmakerTestamentIntegrity[row])
                || !validAxis(state.kingmakerTestamentStewardship[row])
                || !validAxis(state.kingmakerTestamentInstitutionalism[row])) {
            return false;
        }
        KingmakerTestamentState status = KingmakerTestamentState.fromByte(
                state.kingmakerTestamentState[row]);
        if (status != KingmakerTestamentState.SEALED
                && status != KingmakerTestamentState.REVEALED) {
            return false;
        }
        int boundary = state.kingmakerTestamentMoralChoiceCount[row];
        return boundary > 0 && boundary <= state.moralChoiceCount
                && validMoralCapacity(state, boundary)
                && hasCoronationChoice(state, row, boundary);
    }

    private static boolean hasCoronationChoice(CampaignState state,
                                                int testamentRow,
                                                int boundary) {
        long sourceChain = state.kingmakerTestamentSourceChainId[testamentRow];
        int sealedTick = state.kingmakerTestamentSealedTick[testamentRow];
        for (int row = 0; row < boundary; row++) {
            if (state.moralChoiceId[row] > 0L
                    && MoralChoiceSource.fromByte(state.moralChoiceSourceType[row])
                    == MoralChoiceSource.CIVIL_WAR_CLAIMANT
                    && state.moralChoiceSourceId[row] == sourceChain
                    && state.moralChoiceHappenedTick[row] >= 0
                    && state.moralChoiceHappenedTick[row] <= sealedTick
                    && state.moralChoiceRecordedTick[row]
                        >= state.moralChoiceHappenedTick[row]
                    && state.moralChoiceRecordedTick[row] <= sealedTick
                    && consistentDeltas(state, row,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                        sourceChain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean validMoralCapacity(CampaignState state, int boundary) {
        return state.moralChoiceId != null
                && boundary <= state.moralChoiceId.length
                && state.moralChoiceSourceType != null
                && boundary <= state.moralChoiceSourceType.length
                && state.moralChoiceSourceId != null
                && boundary <= state.moralChoiceSourceId.length
                && state.moralChoiceMercyDelta != null
                && boundary <= state.moralChoiceMercyDelta.length
                && state.moralChoiceIntegrityDelta != null
                && boundary <= state.moralChoiceIntegrityDelta.length
                && state.moralChoiceStewardshipDelta != null
                && boundary <= state.moralChoiceStewardshipDelta.length
                && state.moralChoiceInstitutionalismDelta != null
                && boundary <= state.moralChoiceInstitutionalismDelta.length
                && state.moralChoiceHappenedTick != null
                && boundary <= state.moralChoiceHappenedTick.length
                && state.moralChoiceRecordedTick != null
                && boundary <= state.moralChoiceRecordedTick.length;
    }

    private static boolean validAxis(int value) {
        return value >= -100 && value <= 100;
    }

    private static List<Witness> witnesses(CampaignState state, int testamentRow,
                                           Names names) {
        Candidate[] best = new Candidate[Family.values().length];
        int boundary = state.kingmakerTestamentMoralChoiceCount[testamentRow];
        int sealedTick = state.kingmakerTestamentSealedTick[testamentRow];
        long sourceChain = state.kingmakerTestamentSourceChainId[testamentRow];
        for (int row = 0; row < boundary; row++) {
            Candidate candidate = candidate(state, row, sealedTick,
                    sourceChain, names);
            if (candidate == null) continue;
            int family = candidate.family.ordinal();
            if (better(candidate, best[family])) best[family] = candidate;
        }

        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : best) {
            if (candidate != null) selected.add(candidate);
        }
        selected.sort((left, right) -> {
            int byDay = Integer.compare(left.happenedTick, right.happenedTick);
            if (byDay != 0) return byDay;
            return Long.compare(left.choiceId, right.choiceId);
        });

        List<Witness> result = new ArrayList<>(selected.size());
        for (Candidate candidate : selected) {
            result.add(new Witness(candidate.source, candidate.sourceId,
                    candidate.text));
        }
        return result;
    }

    private static Candidate candidate(CampaignState state, int row,
                                       int sealedTick, long sourceChain,
                                       Names names) {
        long choiceId = state.moralChoiceId[row];
        long sourceId = state.moralChoiceSourceId[row];
        int happened = state.moralChoiceHappenedTick[row];
        int recorded = state.moralChoiceRecordedTick[row];
        if (choiceId <= 0L || sourceId <= 0L || happened < 0
                || happened > sealedTick || recorded < happened
                || recorded > sealedTick) {
            return null;
        }
        MoralChoiceSource source = MoralChoiceSource.fromByte(
                state.moralChoiceSourceType[row]);
        if (!consistentDeltas(state, row, source, sourceId)) return null;
        int weight = weight(state, row);
        if (source == MoralChoiceSource.CIVIL_WAR_CLAIMANT
                || source == MoralChoiceSource.CIVIL_WAR_INCUMBENT) {
            if (sourceId == sourceChain) return null;
            String text = civilWarText(state, source, sourceId, happened, names);
            return text == null ? null : new Candidate(Family.CIVIL_WAR,
                    source, sourceId, choiceId, happened, weight, text);
        }
        if (source == MoralChoiceSource.CIVILIAN_RESCUE_REFUSED
                || source == MoralChoiceSource.CIVILIAN_RESCUE_SAVED) {
            String text = rescueText(state, source, sourceId, happened, names);
            return text == null ? null : new Candidate(Family.CIVILIAN_RESCUE,
                    source, sourceId, choiceId, happened, weight, text);
        }
        if (source == MoralChoiceSource.DEFECTOR_ASYLUM) {
            String text = defectorText(state, sourceId, happened, names);
            return text == null ? null : new Candidate(Family.DEFECTOR_ASYLUM,
                    source, sourceId, choiceId, happened, weight, text);
        }
        return null;
    }

    private static int weight(CampaignState state, int row) {
        return Math.abs((int) state.moralChoiceMercyDelta[row])
                + Math.abs((int) state.moralChoiceIntegrityDelta[row])
                + Math.abs((int) state.moralChoiceStewardshipDelta[row])
                + Math.abs((int) state.moralChoiceInstitutionalismDelta[row]);
    }

    private static boolean consistentDeltas(CampaignState state, int row,
                                            MoralChoiceSource source,
                                            long sourceId) {
        int mercy = state.moralChoiceMercyDelta[row];
        int integrity = state.moralChoiceIntegrityDelta[row];
        int stewardship = state.moralChoiceStewardshipDelta[row];
        int institutionalism = state.moralChoiceInstitutionalismDelta[row];
        if (source == MoralChoiceSource.CIVIL_WAR_CLAIMANT) {
            return mercy == 0 && integrity == 0 && stewardship == 0
                    && institutionalism <= 0;
        }
        if (source == MoralChoiceSource.CIVIL_WAR_INCUMBENT) {
            return mercy == 0 && integrity == 0 && stewardship == 0
                    && institutionalism >= 0;
        }
        if (source == MoralChoiceSource.CIVILIAN_RESCUE_REFUSED) {
            return mercy <= 0 && integrity == 0 && stewardship <= 0
                    && institutionalism == 0;
        }
        if (source == MoralChoiceSource.CIVILIAN_RESCUE_SAVED) {
            return mercy >= 0 && integrity == 0 && stewardship >= 0
                    && institutionalism == 0;
        }
        if (source != MoralChoiceSource.DEFECTOR_ASYLUM) return false;
        int eventRow = state.eventIndex(sourceId);
        if (eventRow < 0) return false;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[eventRow]);
        if (eventState == CampaignEventState.REFUSED) {
            return mercy <= 0 && integrity == 0 && stewardship == 0
                    && institutionalism == 0;
        }
        DefectorAsylumOutcome outcome = DefectorAsylumOutcome.fromByte(
                state.eventDefectorOutcome[eventRow]);
        if (outcome == DefectorAsylumOutcome.PROTECTED) {
            return mercy == 0 && integrity >= 0 && stewardship >= 0
                    && institutionalism == 0;
        }
        if (outcome == DefectorAsylumOutcome.BETRAYED) {
            return mercy == 0 && integrity <= 0 && stewardship <= 0
                    && institutionalism == 0;
        }
        return false;
    }

    private static boolean better(Candidate candidate, Candidate current) {
        if (current == null || candidate.weight != current.weight) {
            return current == null || candidate.weight > current.weight;
        }
        if (candidate.happenedTick != current.happenedTick) {
            return candidate.happenedTick > current.happenedTick;
        }
        return candidate.choiceId < current.choiceId;
    }

    private static String civilWarText(CampaignState state,
                                       MoralChoiceSource source, long chainId,
                                       int happened, Names names) {
        int chainRow = state.chainIndex(chainId);
        if (chainRow < 0
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR) {
            return null;
        }
        String actor = nameOfHouse(names, state.chainActorHouseId[chainRow]);
        String target = nameOfHouse(names, state.chainTarget[chainRow]);
        String market = nameOfMarket(names, state.chainMarketId[chainRow]);
        if (actor == null || target == null || market == null
                || state.chainActorHouseId[chainRow]
                    == state.chainTarget[chainRow]) {
            return null;
        }
        if (source == MoralChoiceSource.CIVIL_WAR_CLAIMANT) {
            int claimRow = throneClaimForChain(state, chainId);
            if (claimRow < 0
                    || ThroneClaimState.fromByte(state.throneClaimState[claimRow])
                        != ThroneClaimState.APPLIED
                    || state.throneClaimAppliedTick[claimRow] != happened) {
                return null;
            }
            return "Before this throne, you had already helped " + actor
                    + " cast " + target + " from power at " + market + ".";
        }
        if (ChainState.fromByte(state.chainState[chainRow]) != ChainState.FAILED
                || CivilWarAllegiance.fromByte(
                    state.chainPlayerAllegiance[chainRow])
                    != CivilWarAllegiance.INCUMBENT
                || CivilWarPlayerConsequenceState.fromByte(
                    state.chainPlayerConsequenceState[chainRow])
                    != CivilWarPlayerConsequenceState.APPLIED
                || state.chainResolvedTick[chainRow] != happened) {
            return null;
        }
        return "When " + actor + " moved against " + target + " at " + market
                + ", you stood with the old ruler and helped their order survive.";
    }

    private static int throneClaimForChain(CampaignState state, long chainId) {
        for (int row = 0; row < state.throneClaimCount; row++) {
            if (state.throneClaimSourceChainId[row] == chainId) return row;
        }
        return -1;
    }

    private static String rescueText(CampaignState state,
                                     MoralChoiceSource source, long eventId,
                                     int happened, Names names) {
        int eventRow = state.eventIndex(eventId);
        if (eventRow < 0
                || CampaignEventType.fromByte(state.eventType[eventRow])
                    != CampaignEventType.CIVILIAN_RESCUE) {
            return null;
        }
        String market = nameOfMarket(names, state.eventMarketId[eventRow]);
        if (market == null) return null;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[eventRow]);
        if (source == MoralChoiceSource.CIVILIAN_RESCUE_REFUSED) {
            if (eventState != CampaignEventState.REFUSED
                    || state.eventDecisionTick[eventRow] != happened) {
                return null;
            }
            return "At " + market + ", you heard a transport's distress call "
                    + "and left the civilians aboard to their fate.";
        }
        int rescued = state.eventCiviliansRescued[eventRow];
        int atRisk = state.eventCiviliansAtRisk[eventRow];
        if (eventState != CampaignEventState.RESOLVED
                || state.eventResolvedTick[eventRow] != happened
                || rescued <= 0 || atRisk <= 0 || rescued > atRisk) {
            return null;
        }
        if (rescued == atRisk) {
            return "At " + market + ", you sent your marines into a dying "
                    + "transport and brought everyone still aboard out alive.";
        }
        if ((long) rescued * 2L >= atRisk) {
            return "At " + market + ", you sent your marines into a dying "
                    + "transport and brought many of its civilians back.";
        }
        return "At " + market + ", you sent your marines into a dying transport "
                + "and brought some back, though more were lost than saved.";
    }

    private static String defectorText(CampaignState state, long eventId,
                                       int happened, Names names) {
        int eventRow = state.eventIndex(eventId);
        if (eventRow < 0
                || CampaignEventType.fromByte(state.eventType[eventRow])
                    != CampaignEventType.DEFECTOR_ASYLUM) {
            return null;
        }
        String actor = nameOfHouse(names, state.eventActorHouseId[eventRow]);
        String target = nameOfHouse(names, state.eventTargetHouseId[eventRow]);
        String market = nameOfMarket(names, state.eventMarketId[eventRow]);
        if (actor == null || target == null || market == null
                || state.eventActorHouseId[eventRow]
                    == state.eventTargetHouseId[eventRow]) {
            return null;
        }
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[eventRow]);
        if (eventState == CampaignEventState.REFUSED) {
            if (state.eventDecisionTick[eventRow] != happened) return null;
            return "When a defector carrying " + actor + "'s secrets asked for "
                    + "shelter at " + market
                    + ", you refused them before any promise was made.";
        }
        if (eventState != CampaignEventState.RESOLVED
                || state.eventResolvedTick[eventRow] != happened) {
            return null;
        }
        DefectorAsylumOutcome outcome = DefectorAsylumOutcome.fromByte(
                state.eventDefectorOutcome[eventRow]);
        if (outcome == DefectorAsylumOutcome.PROTECTED) {
            return "You gave a defector your protection, and when " + actor
                    + " tried to buy that promise back, you kept your word.";
        }
        if (outcome == DefectorAsylumOutcome.BETRAYED) {
            return "You gave a defector your protection, then sold them back to "
                    + actor + " when the price was named.";
        }
        return null;
    }

    static String verdict(int mercy, int integrity, int stewardship,
                          int institutionalism) {
        int[] values = {integrity, mercy, stewardship, institutionalism};
        Axis[] axes = Axis.values();
        int positive = strongestByValence(axes, values, 1);
        int negative = strongestByValence(axes, values, -1);
        int first;
        int second;
        if (positive >= 0 && negative >= 0) {
            if (stronger(positive, negative, values)) {
                first = positive;
                second = negative;
            } else {
                first = negative;
                second = positive;
            }
        } else {
            first = strongest(axes, values, -1);
            second = strongest(axes, values, first);
        }
        if (first < 0) {
            return "In the end, your record denied me any simple name for what "
                    + "you became.";
        }
        String firstClause = clause(axes[first], values[first]);
        if (second < 0) {
            return "In the end, you became a commander who " + firstClause + ".";
        }
        boolean opposed = valence(axes[first], values[first])
                * valence(axes[second], values[second]) < 0;
        return "You became a commander who " + firstClause
                + (opposed ? ", yet " : " and ")
                + clause(axes[second], values[second]) + ".";
    }

    private static int strongestByValence(Axis[] axes, int[] values,
                                           int wanted) {
        int best = -1;
        for (int i = 0; i < axes.length - 1; i++) {
            if (Math.abs(values[i]) >= VERDICT_SIGNAL
                    && valence(axes[i], values[i]) == wanted
                    && (best < 0 || stronger(i, best, values))) {
                best = i;
            }
        }
        return best;
    }

    private static int strongest(Axis[] axes, int[] values, int excluded) {
        int best = -1;
        for (int i = 0; i < axes.length; i++) {
            if (i == excluded || Math.abs(values[i]) < VERDICT_SIGNAL) continue;
            if (best < 0 || stronger(i, best, values)) best = i;
        }
        return best;
    }

    private static boolean stronger(int left, int right, int[] values) {
        int leftMagnitude = Math.abs(values[left]);
        int rightMagnitude = Math.abs(values[right]);
        return leftMagnitude > rightMagnitude
                || (leftMagnitude == rightMagnitude && left < right);
    }

    private static int valence(Axis axis, int value) {
        if (axis == Axis.INSTITUTIONALISM || value == 0) return 0;
        return value > 0 ? 1 : -1;
    }

    private static String clause(Axis axis, int value) {
        return switch (axis) {
            case MERCY -> value > 0
                    ? "answered when the helpless called"
                    : "let the helpless bear the cost";
            case INTEGRITY -> value > 0
                    ? "kept faith when betrayal would have paid"
                    : "sold promises when the price was high enough";
            case STEWARDSHIP -> value > 0
                    ? "treated lives in your keeping as a charge"
                    : "treated lives in your keeping as expendable";
            case INSTITUTIONALISM -> value > 0
                    ? "defended the order that gave you authority"
                    : "broke the order that stood in your way";
        };
    }

    private static String nameOfHouse(Names names, long id) {
        try {
            return clean(names.house(id));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String nameOfMarket(Names names, int id) {
        try {
            return clean(names.market(id));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
