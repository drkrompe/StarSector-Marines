package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.campaign.CommodityPresentation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Commodity names and icons read from the installed game's
 * {@code data/campaign/commodities.csv}, so a headless frame draws the same
 * cargo icons the live screen asks the game's settings for. Without it a
 * snapshot falls back to {@link CommodityPresentation#NONE} and every material
 * row draws without its icon.
 */
public final class HeadlessCommodityPresentation implements CommodityPresentation {
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, String> icons = new HashMap<>();

    public static CommodityPresentation from(Path starsectorCore) {
        Path csv = starsectorCore.resolve("data/campaign/commodities.csv");
        if (!Files.isRegularFile(csv)) return CommodityPresentation.NONE;
        try {
            return new HeadlessCommodityPresentation(Files.readAllLines(csv));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private HeadlessCommodityPresentation(List<String> lines) {
        List<String> header = split(lines.get(0));
        int name = header.indexOf("name"), id = header.indexOf("id"), icon = header.indexOf("icon");
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = split(line);
            if (cells.size() <= Math.max(icon, Math.max(name, id)) || cells.get(id).isBlank()) continue;
            names.put(cells.get(id), cells.get(name));
            icons.put(cells.get(id), cells.get(icon));
        }
    }

    @Override public String commodityName(String commodityId) {
        return names.getOrDefault(commodityId, CommodityPresentation.NONE.commodityName(commodityId));
    }

    @Override public String commodityIcon(String commodityId) { return icons.getOrDefault(commodityId, ""); }

    /** One CSV record; quoted cells may hold commas. */
    private static List<String> split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) { cells.add(cell.toString().trim()); cell.setLength(0); }
            else cell.append(c);
        }
        cells.add(cell.toString().trim());
        return cells;
    }
}
