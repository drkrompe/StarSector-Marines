package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AbsentDefenceGrade.Outcome;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Composes the company's after-action account of a Garrison defence that settled without
 * the player — see {@code contracts-nouns.md} (stranded detachments).
 *
 * <p>The account has a <em>spine</em> and <em>colour</em>. Every claim in the spine is a
 * fact the settlement froze: who attacked, where, when, who stood the post, how it ended
 * and who was struck. The colour is drawn from authored pools keyed by the planet's
 * climate and the outcome, seeded from the defence's event key so the same defence always
 * reads the same. Colour is framing only ({@code narrative-nouns.md} law 1): its lines
 * name nobody, claim no deed and state no consequence, which is why they are pools of
 * fixed text with no arguments.
 *
 * <p>Pure: the text lookup is injected, so the composer reads no game state. The account
 * is composed once, when the report is written, and stored with it.
 */
public final class DefenceReport {

    /** Separator between interchangeable lines in one pool string. */
    static final String POOL_SEPARATOR = "\\|";

    /** Climate bucket the colour pools are keyed by, read off vanilla market conditions. */
    public enum Climate {
        TOXIC, AIRLESS, STORM, HEAT, COLD, TEMPERATE, UNKNOWN;

        /**
         * The most striking condition wins: a toxic world is toxic before it is hot.
         * {@code null} conditions mean the market could not be read.
         */
        public static Climate from(Collection<String> conditionIds) {
            if (conditionIds == null) return UNKNOWN;
            if (conditionIds.contains("toxic_atmosphere")) return TOXIC;
            if (conditionIds.contains("no_atmosphere")
                    || conditionIds.contains("thin_atmosphere")) {
                return AIRLESS;
            }
            if (conditionIds.contains("extreme_weather")) return STORM;
            if (conditionIds.contains("very_hot") || conditionIds.contains("hot")) return HEAT;
            if (conditionIds.contains("very_cold") || conditionIds.contains("cold")) return COLD;
            return TEMPERATE;
        }
    }

    /** Where the defence happened and who came, read from the live sector at settlement. */
    public record Place(String marketName, String systemName, Climate climate,
                        String attackerName) {
        public static final Place UNKNOWN = new Place(null, null, Climate.UNKNOWN, null);
    }

    /**
     * Everything the account may state. {@code landed} is false for a raid that turned
     * back before planetfall; {@code unnamedLost} carries losses from a count-only legacy
     * assignment, which has no marines to name. {@code goneToGroundDays} is the recovery
     * window of an overrun garrison whose survivors are stranded, or 0.
     */
    public record Facts(long eventKey, int day, Place place, boolean landed, Outcome outcome,
                        String captainName, List<String> squadNames,
                        List<String> killed, List<String> missing, int wounded,
                        int unnamedLost, int seatsStood, int seatsLiving,
                        float stationedStrength, float attackerStrength,
                        int goneToGroundDays) {
    }

    private DefenceReport() {}

    /**
     * The frozen account: spine and colour as one paragraph.
     *
     * @param text localized string lookup by key, {@code Strings::get} in game
     */
    public static String compose(Facts facts, Function<String, String> text) {
        Place place = facts.place() != null ? facts.place() : Place.UNKNOWN;
        Random seed = new Random(facts.eventKey());
        List<String> lines = new ArrayList<>();

        String attacker = orElse(place.attackerName(), text.apply("defenceReportUnknownAttacker"));
        String where = place.marketName() == null
                ? text.apply("defenceReportUnknownPlace")
                : place.systemName() == null ? place.marketName()
                : format(text.apply("defenceReportPlace"), place.marketName(), place.systemName());
        lines.add(format(text.apply(facts.landed() ? "defenceReportOpenLanded"
                : "defenceReportOpenTurned"), attacker, where, facts.day()));
        lines.add(pick(text.apply("defenceReportColour" + place.climate().name()), seed));

        String squads = String.join(", ", facts.squadNames());
        if (facts.captainName() != null) {
            lines.add(squads.isEmpty()
                    ? format(text.apply("defenceReportStoodCaptain"), facts.captainName())
                    : format(text.apply("defenceReportStoodCaptainSquads"),
                            facts.captainName(), squads));
        } else if (!squads.isEmpty()) {
            lines.add(format(text.apply("defenceReportStoodSquads"), squads));
        }

        if (facts.landed()) {
            lines.add(text.apply("defenceReportOutcome" + facts.outcome().name()));
            boolean losses = false;
            if (!facts.killed().isEmpty()) {
                lines.add(format(text.apply("defenceReportKilled"),
                        String.join(", ", facts.killed())));
                losses = true;
            }
            if (!facts.missing().isEmpty()) {
                lines.add(format(text.apply("defenceReportMissing"),
                        String.join(", ", facts.missing())));
                losses = true;
            }
            if (facts.wounded() > 0) {
                lines.add(format(text.apply("defenceReportWounded"), facts.wounded()));
                losses = true;
            }
            if (facts.unnamedLost() > 0) {
                lines.add(format(text.apply("defenceReportUnnamedLost"), facts.unnamedLost()));
                losses = true;
            }
            if (!losses) lines.add(text.apply("defenceReportNoLosses"));
            lines.add(format(text.apply("defenceReportLiving"),
                    facts.seatsLiving(), facts.seatsStood()));
            if (facts.goneToGroundDays() > 0) {
                lines.add(format(text.apply("defenceReportGoneToGround"),
                        facts.goneToGroundDays()));
            }
        }

        int stationed = Math.round(facts.stationedStrength());
        int raiders = Math.round(facts.attackerStrength());
        if (stationed > 0) {
            lines.add(raiders > 0
                    ? format(text.apply("defenceReportContributionAgainst"), stationed, raiders)
                    : format(text.apply("defenceReportContribution"), stationed));
        }
        lines.add(pick(text.apply("defenceReportCloser"
                + (facts.landed() ? facts.outcome().name() : "TURNED")), seed));
        return String.join(" ", lines);
    }

    /** One line of a pool, chosen by the seed. A single-line pool is its own answer. */
    static String pick(String pool, Random seed) {
        String[] options = pool.split(POOL_SEPARATOR);
        return options[seed.nextInt(options.length)].trim();
    }

    private static String format(String pattern, Object... arguments) {
        return MessageFormat.format(pattern, arguments);
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
