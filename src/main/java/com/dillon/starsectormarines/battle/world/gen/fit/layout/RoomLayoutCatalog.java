package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Finding the authored rooms and installing them.
 *
 * <p>Authored rooms are a growing set rather than a fixed one — the workbench
 * adds a file every time somebody keeps a room — so they cannot be a constant
 * list the way the tilesets are. And mod code cannot list a directory: it reads
 * through {@code SettingsAPI} and nothing else. So the set is named by an index
 * the tool maintains, and both the game and the tool read the same index rather
 * than each discovering rooms their own way.
 *
 * <p>Loading is <b>defensive</b>. A room that will not parse is skipped with its
 * name in the log and the rest are installed, because the alternative is a
 * hand-edited file taking the whole ship down at startup. That is the opposite
 * of the rule the weapon catalog follows, and deliberately: a weapon that failed
 * to load reads zero damage and makes a battle silently unwinnable, whereas a
 * room that failed to load simply generates the way it did before anybody
 * authored it.
 */
public final class RoomLayoutCatalog {

    private static final Logger LOG = Logger.getLogger(RoomLayoutCatalog.class);

    private RoomLayoutCatalog() {}

    /** Where the index lives, relative to the mod folder. */
    public static final String INDEX = "data/world/rooms/rooms.json";

    /** The folder the index's entries are named relative to. */
    public static final String FOLDER = "data/world/rooms";

    /** How a caller gets at one document — off disk for a tool, through the game for the mod. */
    public interface Documents {
        JSONObject read(String path) throws Exception;
    }

    /**
     * Read every room the index names.
     *
     * @param index the index document, or null for none — which installs nothing
     *     and leaves every room on its procedural fitting
     */
    public static RoomLayouts read(JSONObject index, Documents documents) {
        List<RoomLayout> layouts = new ArrayList<>();
        if (index == null) return new RoomLayouts(layouts);

        JSONArray names = index.optJSONArray("rooms");
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i, "");
            if (name.isEmpty()) continue;
            try {
                layouts.add(RoomLayoutJson.parse(documents.read(FOLDER + "/" + name)));
            } catch (Exception failure) {
                LOG.error("RoomLayoutCatalog: skipping '" + name + "' — " + failure.getMessage());
            }
        }
        return new RoomLayouts(layouts);
    }

    /** The index naming these documents, for the tool to write back. */
    public static JSONObject index(List<String> names) throws Exception {
        JSONArray rooms = new JSONArray();
        for (String name : names) rooms.put(name);
        return new JSONObject().put("rooms", rooms);
    }

    /**
     * Install the authored rooms the game ships with.
     *
     * <p>Fully defensive, like the tile catalog: a failure here logs and leaves
     * the previous install alone rather than throwing out of
     * {@code onApplicationLoad}.
     */
    public static void loadBuiltins() {
        try {
            JSONObject index = Global.getSettings().loadJSON(INDEX, true);
            RoomLayouts layouts = read(index, path -> Global.getSettings().loadJSON(path, true));
            RoomLayouts.install(layouts);
            LOG.info("RoomLayoutCatalog: " + layouts.all().size() + " authored rooms");
        } catch (Exception failure) {
            // No index is the ordinary case until somebody authors a room, so
            // this is information rather than an error.
            LOG.info("RoomLayoutCatalog: no authored rooms installed ("
                    + failure.getMessage() + ")");
        }
    }
}
