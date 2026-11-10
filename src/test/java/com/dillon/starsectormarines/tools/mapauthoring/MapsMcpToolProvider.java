package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapJson;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolProvider;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page headless: list the owned maps and the worlds, build an owned map for one world or every world, check
 * it and draw it, and save it, so a session edits and looks at a map without the workbench. The same
 * {@link MapsSession} the page drives. Pictures go under {@code build/map-authoring/} unless told elsewhere inside the
 * project; only {@code map_save} writes into mod/, and only a map whose checks pass on every world.
 */
public final class MapsMcpToolProvider implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(new ListMaps(), new RenderMap(), new SaveMap());
    }

    private static final class ListMaps implements McpTool {
        @Override public String name() { return "map_list"; }
        @Override public String description() {
            return "List the owned mission maps the Maps page opens (data/world/maps/<id>.map.json, owned-maps.md) and "
                    + "the worlds a map is built for, with each world's drop style for a RIVER and a RIFT and its habitat.";
        }
        @Override public JSONObject inputSchema() { return McpSchema.object().build(); }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            JSONArray worlds = new JSONArray();
            StringBuilder text = new StringBuilder("Owned maps: " + MapsSession.maps() + "\nWorlds:\n");
            for (MapWorld w : MapWorld.values()) {
                String habitat = w.skin().habitat().name().toLowerCase(Locale.ROOT);
                worlds.put(new JSONObject().put("id", w.name()).put("planetType", w.planetType())
                        .put("river", w.dropStyle(OwnedMap.DropRole.RIVER)).put("rift", w.dropStyle(OwnedMap.DropRole.RIFT))
                        .put("habitat", habitat));
                text.append(String.format(Locale.ROOT, "  %-10s %-10s river %-16s rift %-12s habitat %s%n", w.name(),
                        w.planetType(), w.dropStyle(OwnedMap.DropRole.RIVER), w.dropStyle(OwnedMap.DropRole.RIFT), habitat));
            }
            return McpToolResult.of(text.toString(), new JSONObject().put("maps", new JSONArray(MapsSession.maps())).put("worlds", worlds));
        }
    }

    private static McpSchema documentArguments(McpSchema schema) {
        return schema.requiredString("map", "Owned map id from map_list")
                .object("document", "The owned map document to use instead of the saved one (OwnedMapJson form)", false)
                .object("roads", "The road document to use instead of the one the map names (RoadPlan form)", false);
    }

    /** The saved map, or the given document and roads in its place. */
    private static MapDocument document(MapsSession session, JSONObject arguments) throws Exception {
        MapDocument doc = session.open(arguments.getString("map"));
        JSONObject map = arguments.optJSONObject("document");
        JSONObject roads = arguments.optJSONObject("roads");
        if (map != null) doc = doc.withMap(OwnedMapJson.read(map));
        if (roads != null) doc = doc.withRoads(RoadPlan.read(roads));
        return doc;
    }

    private static final class RenderMap implements McpTool {
        @Override public String name() { return "map_render"; }
        @Override public String description() {
            return "Build an owned map for a world with the mission's own recipe, run the Maps page's checks (every pad, "
                    + "site, building and route reachable from the origin; every crossing joins its banks; nothing built on "
                    + "a road, crossing or gate) and draw it whole at 1920x1080 in the battle's look preset, with the page's "
                    + "marks. 'document' and 'roads' replace the saved ones, so edits can be tried without saving. 'world' "
                    + "is one of map_list's; ALL also draws the all-worlds contact sheet. Writes PNGs only, never into mod/.";
        }
        @Override public JSONObject inputSchema() {
            return documentArguments(McpSchema.object())
                    .string("world", "World to build it for (map_list); default ARID; ALL adds the contact sheet")
                    .bool("marked", "Draw the page's marks and the check results. Default true.")
                    .string("outputDir", "Project-relative folder for the PNGs. Default build/map-authoring/<map>.")
                    .build();
        }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String dir = arguments.optString("outputDir", "");
            String id = arguments.getString("map");
            Path out = dir.isBlank() ? context.projectRoot().resolve("build/map-authoring").resolve(id) : context.resolveInsideProject(dir);
            String asked = arguments.optString("world", "").trim().toUpperCase(Locale.ROOT);
            boolean all = asked.equals("ALL");
            MapWorld world = asked.isEmpty() || all ? MapWorld.ARID : MapWorld.valueOf(asked);
            boolean marked = arguments.optBoolean("marked", true);
            Files.createDirectories(out);
            try (MapsSession session = new MapsSession(context.projectRoot())) {
                MapDocument doc = document(session, arguments);
                MapsSession.Built built = session.build(doc, world);
                JSONObject result = new JSONObject().put("problems", new JSONArray(built.problems())).put("world", world.name());
                StringBuilder text = new StringBuilder();
                if (built.map() != null) {
                    Path picture = out.resolve(world.name().toLowerCase(Locale.ROOT) + (marked ? "-marked" : "") + ".png");
                    write(marked ? session.picture(built, doc, null) : session.frame(built).image(), picture);
                    result.put("picture", picture.toString());
                    text.append(picture).append('\n');
                }
                JSONObject adaptations = new JSONObject();
                for (var e : built.adaptations().entrySet()) adaptations.put(e.getKey(), MapWorld.words(e.getValue()));
                result.put("adaptations", adaptations);
                if (all) {
                    Path sheet = out.resolve("all-worlds.png");
                    write(session.contactSheet(doc), sheet);
                    result.put("contactSheet", sheet.toString());
                    text.append(sheet).append('\n');
                }
                text.append(built.problems().isEmpty() ? "All checks pass.\n" : String.join("\n", built.problems()) + "\n");
                return new McpToolResult(text.toString(), result, !built.problems().isEmpty());
            }
        }
    }

    private static final class SaveMap implements McpTool {
        @Override public String name() { return "map_save"; }
        @Override public String description() {
            return "Save an owned map and its road document into mod/ (data/world/maps/<id>.map.json and the roads it "
                    + "names), each replaced atomically, after building it for every world and running every check. "
                    + "Refused, writing nothing, while any check fails on any world; the reply names each failure.";
        }
        @Override public JSONObject inputSchema() { return documentArguments(McpSchema.object()).build(); }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            try (MapsSession session = new MapsSession(context.projectRoot())) {
                MapDocument doc = document(session, arguments);
                List<String> problems = session.checkEveryWorld(doc);
                if (!problems.isEmpty()) {
                    return new McpToolResult("Not saved:\n" + String.join("\n", problems), new JSONObject().put("problems", new JSONArray(problems)), true);
                }
                List<Path> written = session.save(doc, context.projectRoot());
                JSONArray paths = new JSONArray();
                for (Path p : written) paths.put(p.toString());
                return McpToolResult.of("Saved " + written, new JSONObject().put("written", paths));
            }
        }
    }

    private static void write(BufferedImage image, Path target) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "map-", ".png");
        ImageIO.write(image, "png", temp.toFile());
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
