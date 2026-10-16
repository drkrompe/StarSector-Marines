package com.dillon.starsectormarines.cinematic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Pages a caption's text for whatever language is active: sentence by sentence into pages of at
 * most two balanced rows within the caption width. A sentence that needs more than two rows is cut
 * at its commas, and a piece that still does is cut by width. Pure text and a measuring function,
 * so it needs neither a font nor a screen.
 */
public final class CaptionPager {
    /** Rows on one page. */
    static final int MAX_ROWS = 2;

    /** A page is worth its length plus this, so a one-word page still gets time to be read. */
    private static final int WEIGHT_PAD = 6;

    private CaptionPager() { }

    /** The pages of {@code text}, each one or two rows. Empty for blank text. */
    public static List<List<String>> pages(String text, ToDoubleFunction<String> measure, double maxWidth) {
        List<String> pieces = new ArrayList<>();
        for (String sentence : text.strip().split("(?<=[.?!])\s+")) {
            if (sentence.isBlank()) continue;
            if (greedyRows(sentence, measure, maxWidth).size() <= MAX_ROWS) {
                pieces.add(sentence);
                continue;
            }
            for (String clause : sentence.split("(?<=,)\s+")) {
                if (greedyRows(clause, measure, maxWidth).size() <= MAX_ROWS) pieces.add(clause);
                else pieces.addAll(cutByWidth(clause, measure, maxWidth));
            }
        }
        List<String> merged = new ArrayList<>();
        String current = "";
        for (String piece : pieces) {
            String trial = current.isEmpty() ? piece : current + " " + piece;
            if (!current.isEmpty() && greedyRows(trial, measure, maxWidth).size() > MAX_ROWS) {
                merged.add(current);
                current = piece;
            } else {
                current = trial;
            }
        }
        if (!current.isEmpty()) merged.add(current);
        List<List<String>> pages = new ArrayList<>();
        for (String page : merged) pages.add(balance(page, measure, maxWidth));
        return pages;
    }

    /** The share of the take each page holds: its length plus a pad, over the sum. */
    public static double[] shares(List<List<String>> pages) {
        double[] weights = new double[pages.size()];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = String.join(" ", pages.get(i)).length() + WEIGHT_PAD;
            total += weights[i];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= total;
        return weights;
    }

    /** Words packed left to right; a word wider than the row sits alone on one. */
    static List<String> greedyRows(String text, ToDoubleFunction<String> measure, double maxWidth) {
        List<String> rows = new ArrayList<>();
        String row = "";
        for (String word : text.trim().split("\s+")) {
            if (word.isEmpty()) continue;
            String trial = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && measure.applyAsDouble(trial) > maxWidth) {
                rows.add(row);
                row = word;
            } else {
                row = trial;
            }
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /** Splits an over-long run into the longest word prefixes that each fit two rows. */
    private static List<String> cutByWidth(String text, ToDoubleFunction<String> measure, double maxWidth) {
        String[] words = text.trim().split("\s+");
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = start + 1;
            while (end < words.length
                    && greedyRows(String.join(" ", Arrays.copyOfRange(words, start, end + 1)), measure, maxWidth).size() <= MAX_ROWS)
                end++;
            out.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            start = end;
        }
        return out;
    }

    /** One row stays one; two rows are re-cut where the wider of them is narrowest. */
    private static List<String> balance(String page, ToDoubleFunction<String> measure, double maxWidth) {
        List<String> rows = greedyRows(page, measure, maxWidth);
        if (rows.size() != 2) return rows;
        String[] words = page.trim().split("\s+");
        int best = 1;
        double bestWidth = Double.MAX_VALUE;
        for (int i = 1; i < words.length; i++) {
            double w = Math.max(measure.applyAsDouble(String.join(" ", Arrays.copyOfRange(words, 0, i))),
                    measure.applyAsDouble(String.join(" ", Arrays.copyOfRange(words, i, words.length))));
            if (w < bestWidth) {
                bestWidth = w;
                best = i;
            }
        }
        return List.of(String.join(" ", Arrays.copyOfRange(words, 0, best)),
                String.join(" ", Arrays.copyOfRange(words, best, words.length)));
    }
}
