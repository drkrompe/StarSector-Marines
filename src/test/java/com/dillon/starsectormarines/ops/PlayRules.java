package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.ExpressionAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.Option;
import com.fs.starfarer.api.campaign.rules.RuleAPI;
import com.fs.starfarer.api.campaign.rules.RuleTokenReplacementGeneratorPlugin;
import com.fs.starfarer.api.campaign.rules.RulesAPI;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@code playBattle}'s rule book: real {@code rules.csv} rows, matched the way the game matches them. A row's
 * conditions all hold or it does not match; its score is the sum of its condition lines' scores (one each, or a line's
 * {@code score:N}); the best score wins. Each line is the game's own ({@link PlayRuleLines}).
 *
 * <p>Rows come from named sources, each filtered to the rows the planet menu stands in for, so a vanilla row that
 * needs the running game is never loaded. Every match is written to the trace with each candidate's score, so the
 * menu can show why a row won. Ties go to the row loaded first; the game breaks them at random, so a tie is traced as
 * one.
 */
final class PlayRules implements RulesAPI {

    /** One file of rows and the rows of it this book holds. */
    record Source(String label, Path file, Charset charset, Predicate<String> keep) {
    }

    private final List<Rule> rules = new ArrayList<>();
    private final Consumer<String> trace;

    PlayRules(List<Source> sources, Consumer<String> trace) {
        this.trace = trace;
        for (Source source : sources) {
            String csv;
            try {
                csv = Files.readString(source.file(), source.charset());
            } catch (IOException failure) {
                throw new IllegalStateException("rules source " + source.label() + " at " + source.file(), failure);
            }
            for (PlayRulesCsv.Row row : PlayRulesCsv.parse(csv)) {
                if (source.keep().test(row.id())) rules.add(new Rule(source.label(), row));
            }
        }
    }

    int size() {
        return rules.size();
    }

    @Override
    public List<RuleAPI> getAllMatching(String currentRule, String trigger, InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {
        List<RuleAPI> matches = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.trigger.equals(trigger)) continue;
            int score = rule.score(dialog, memoryMap);
            if (score < 0) continue;
            matches.add(rule);
            names.add(rule.label());
        }
        if (!matches.isEmpty()) trace.accept("all " + trigger + ": " + String.join(", ", names));
        return matches;
    }

    @Override
    public RuleAPI getBestMatching(String currentRule, String trigger, InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        Rule best = null;
        int bestScore = -1;
        boolean tied = false;
        List<String> candidates = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.trigger.equals(trigger)) continue;
            int score = rule.score(dialog, memoryMap);
            if (score < 0) continue;
            candidates.add(rule.label() + " " + score);
            if (score > bestScore) {
                best = rule;
                bestScore = score;
                tied = false;
            } else if (score == bestScore) {
                tied = true;
            }
        }
        if (best != null) {
            trace.accept("best " + trigger + describe(memoryMap) + " -> " + best.label()
                    + (tied ? " (TIE: the game picks at random)" : "") + "  [" + String.join(", ", candidates) + "]");
        }
        return best;
    }

    private static String describe(Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
        String option = local != null && local.contains("$option") ? local.getString("$option") : null;
        return option != null ? " $option=" + option : "";
    }

    @Override
    public void addTokenReplacementGenerator(RuleTokenReplacementGeneratorPlugin generator) {
    }

    @Override
    public Map<String, String> getTokenReplacements(String ruleId, SectorEntityToken target,
                                                    Map<String, MemoryAPI> memoryMap) {
        Map<String, String> tokens = new HashMap<>();
        if (target != null) {
            tokens.put("$entityName", target.getName());
            if (target.getMarket() != null) tokens.put("$marketName", target.getMarket().getName());
        }
        return tokens;
    }

    @Override
    public String performTokenReplacement(String ruleId, String text, SectorEntityToken entity,
                                          Map<String, MemoryAPI> memoryMap) {
        String result = text;
        for (Map.Entry<String, String> token : getTokenReplacements(ruleId, entity, memoryMap).entrySet()) {
            if (token.getValue() != null) result = result.replace(token.getKey(), token.getValue());
        }
        return result;
    }

    @Override
    public void setRandomForNextRulePick(Random random) {
    }

    /** One row: its conditions and script as the game's own lines, and its options and text as written. */
    private static final class Rule implements RuleAPI {
        private final String source;
        private final String id;
        private final String trigger;
        private final List<PlayRuleLines.Line> conditions = new ArrayList<>();
        private final List<PlayRuleLines.Line> script = new ArrayList<>();
        private final List<Option> options = new ArrayList<>();
        private final List<String> text = new ArrayList<>();

        Rule(String source, PlayRulesCsv.Row row) {
            this.source = source;
            this.id = row.id();
            this.trigger = row.trigger();
            for (String line : lines(row.conditions())) conditions.add(PlayRuleLines.line(line));
            for (String line : lines(row.script())) script.add(PlayRuleLines.line(line));
            for (String line : lines(row.options())) options.add(option(line, options.size()));
            if (!row.text().isBlank()) {
                for (String variant : row.text().split("\\n\\s*OR\\s*\\n")) text.add(variant.strip());
            }
        }

        String label() {
            return source + ":" + id;
        }

        /** The row's score when every condition holds, otherwise -1. */
        int score(InteractionDialogAPI dialog, Map<String, MemoryAPI> memory) {
            int score = 0;
            for (PlayRuleLines.Line condition : conditions) {
                if (!condition.test(id, dialog, memory)) return -1;
                score += condition.score();
            }
            return score;
        }

        private static List<String> lines(String cell) {
            List<String> lines = new ArrayList<>();
            for (String line : cell.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) lines.add(trimmed);
            }
            return lines;
        }

        /** {@code order:id:text} or {@code id:text}; an option without an order keeps its place in the row. */
        private static Option option(String line, int index) {
            Option option = new Option();
            String[] parts = line.split(":", 3);
            if (parts.length == 3 && isNumber(parts[0])) {
                option.order = Float.parseFloat(parts[0]);
                option.id = parts[1].strip();
                option.text = parts[2].strip();
            } else {
                String[] pair = line.split(":", 2);
                option.order = index;
                option.id = pair[0].strip();
                option.text = pair.length > 1 ? pair[1].strip() : pair[0].strip();
            }
            return option;
        }

        private static boolean isNumber(String text) {
            try {
                Float.parseFloat(text.strip());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override public List<String> getText() { return text; }

        @Override public String pickText() { return text.isEmpty() ? null : text.get(0); }

        @Override public String getId() { return id; }

        @Override public String getTrigger() { return trigger; }

        @Override public List<Option> getOptions() { return options; }

        @Override
        public void runScript(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
            // A line that adds options has already run through FireBest's option adder, as in the game.
            for (PlayRuleLines.Line line : script) {
                if (!line.expression().doesCommandAddOptions()) line.run(id, dialog, memoryMap);
            }
        }

        @Override
        public List<ExpressionAPI> getScriptCopy() {
            List<ExpressionAPI> copy = new ArrayList<>();
            for (PlayRuleLines.Line line : script) copy.add(line.expression());
            return copy;
        }
    }
}
