package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.world.gen.GarrisonStructureTable;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the {@link GarrisonStructureTable} from {@value #PATH}, merged across every enabled mod, so a mod that adds a
 * defence industry can say what it adds to a garrison by shipping the same file. Read once and kept; a missing or
 * unreadable table is logged and reads as {@link GarrisonStructureTable#EMPTY}, which leaves every garrison its size's
 * base mix rather than failing a launch.
 */
public final class GarrisonStructureCatalog {

    /** The shipped table's path, relative to a mod root. */
    public static final String PATH = "data/world/garrison/garrison_structures.json";

    private static final Logger log = Global.getLogger(GarrisonStructureCatalog.class);

    private static volatile GarrisonStructureTable loaded;

    private GarrisonStructureCatalog() {}

    /** The merged table, loaded on first use. */
    public static GarrisonStructureTable get() {
        GarrisonStructureTable table = loaded;
        if (table != null) return table;
        synchronized (GarrisonStructureCatalog.class) {
            if (loaded == null) loaded = load();
            return loaded;
        }
    }

    private static GarrisonStructureTable load() {
        try {
            return parse(Global.getSettings().getMergedJSON(PATH));
        } catch (Exception e) {
            log.warn("Garrison structure table unreadable at " + PATH + "; garrisons use their size base", e);
            return GarrisonStructureTable.EMPTY;
        }
    }

    /**
     * Reads a table document: {@code {"structures": {"<industry id>": {"bulk": n, "elite": n, "heavy": n}}}}. A weight
     * left out is zero; an entry that is not an object is skipped.
     */
    public static GarrisonStructureTable parse(JSONObject document) {
        if (document == null) return GarrisonStructureTable.EMPTY;
        JSONObject structures = document.optJSONObject("structures");
        if (structures == null) return GarrisonStructureTable.EMPTY;
        Map<String, GarrisonStructureTable.Contribution> out = new LinkedHashMap<>();
        Iterator<?> keys = structures.keys();
        while (keys.hasNext()) {
            String id = String.valueOf(keys.next());
            JSONObject entry = structures.optJSONObject(id);
            if (entry == null) continue;
            out.put(id, new GarrisonStructureTable.Contribution(
                    (float) entry.optDouble("bulk", 0d),
                    (float) entry.optDouble("elite", 0d),
                    (float) entry.optDouble("heavy", 0d)));
        }
        return new GarrisonStructureTable(out);
    }
}
