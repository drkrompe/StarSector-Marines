package com.dillon.starsectormarines.ops;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@code rules.csv} the way the game lays one out: a header row, then one row per rule with the cells
 * {@code id, trigger, conditions, script, text, options, notes}. Cells may be quoted, hold commas and line breaks, and
 * escape a quote by doubling it. A row whose id is blank or starts with {@code #} is a comment and is skipped. Not a
 * class of the mod: {@code playBattle}'s planet menu ({@link PlayRules}) reads the real files with it.
 */
final class PlayRulesCsv {

    /** One rule row; every cell is never null. Multi-line cells keep their lines, without carriage returns. */
    record Row(String id, String trigger, String conditions, String script, String text, String options) {
    }

    private PlayRulesCsv() {}

    static List<Row> parse(String csv) {
        List<List<String>> records = records(csv);
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> cells = records.get(i);
            String id = cell(cells, 0).strip();
            if (id.isEmpty() || id.startsWith("#")) continue;
            rows.add(new Row(id, cell(cells, 1).strip(), cell(cells, 2), cell(cells, 3), cell(cells, 4),
                    cell(cells, 5)));
        }
        return rows;
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() ? cells.get(index).replace("\r", "") : "";
    }

    private static List<List<String>> records(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                record.add(cell.toString());
                cell.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !record.isEmpty()) {
            record.add(cell.toString());
            records.add(record);
        }
        return records;
    }
}
