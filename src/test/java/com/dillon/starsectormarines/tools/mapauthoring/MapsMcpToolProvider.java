package com.dillon.starsectormarines.tools.mapauthoring;

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
 * The Maps page headless: list the maps it opens, and lay a set of edits on one, check it and draw it under one world
 * or every world, so a session edits and looks at a map without the workbench. The same {@link MapsSession} the page
 * drives. Writes PNGs under {@code build/map-authoring/} unless told elsewhere inside the project; never into mod/.
 */
public final class MapsMcpToolProvider implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(new ListMaps(), new RenderMap());
    }

    private static final class ListMaps implements McpTool {
        @Override public String name() { return "map_list"; }
        @Override public String description() {
            return "List the maps the Maps page opens (existing mission maps, by id, with their named places) and the "
                    + "worlds a map can be skinned as, with each world's drop style and settlement adaptation.";
        }
        @Override public JSONObject inputSchema() { return McpSchema.object().build(); }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            JSONArray maps = new JSONArray();
            StringBuilder text = new StringBuilder("Maps:\n");
            for (MapBases.Base b : MapBases.all()) {
                maps.put(new JSONObject().put("id", b.id()).put("label", b.label()).put("builtAs", b.builtAs().name())
                        .put("places", b.sketch().json().getJSONArray("places")));
                text.append("  ").append(b.id()).append(" — ").append(b.label()).append('\n');
            }
            JSONArray skins = new JSONArray();
            text.append("Worlds:\n");
            for (BiomeSkin s : BiomeSkin.values()) {
                skins.put(new JSONObject().put("id", s.name()).put("planetType", s.planetType()).put("palette", s.palette().name())
                        .put("drop", s.drop().style().name()).put("towns", s.adaptation(MapSketch.Program.TOWN)));
                text.append(String.format(Locale.ROOT, "  %-10s %-10s drop %-11s towns %s%n", s.name(), s.planetType(),
                        s.drop().style(), s.adaptation(MapSketch.Program.TOWN)));
            }
            return McpToolResult.of(text.toString(), new JSONObject().put("maps", maps).put("skins", skins));
        }
    }

    private static final class RenderMap implements McpTool {
        @Override public String name() { return "map_render"; }
        @Override public String description() {
            return "Open a map by id (map_list), lay the given edits on it, run the Maps page's checks (every named place "
                    + "reachable from the landing zone, every crossing crosses, nothing built on a road, crossing, drop or "
                    + "rise) and draw it at 1920x1080 in the battle's look preset. 'edits' holds drops (path, width, depths "
                    + "per point in metres, terraces, crossings {at 0-1, width, kind BRIDGE|LAND_BRIDGE}), rises (path, "
                    + "width, heights, terraces), roads (path, width, material), places (id, label, x, y, role "
                    + "LANDING|SITE|LANDMARK; omit to keep the map's own) and settlements (id, x, y, w, h, program "
                    + "TOWN|COMPOUND|CREVASSE_TOWN, role). Points are [x, y] map cells, y up. 'skin' is one world, "
                    + "'ALL' draws the all-worlds contact sheet as well. Writes PNGs only, never into mod/.";
        }
        @Override public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("map", "Map id from map_list")
                    .object("edits", "The edits to lay: drops, rises, roads, places, settlements", false)
                    .string("skin", "World to draw it as (map_list); default the map's own; ALL adds the contact sheet")
                    .bool("bridgeRoads", "Add a bridge wherever a drop cuts one of the map's roads. Default false.")
                    .bool("marked", "Draw the page's marks and the check results over the picture. Default true.")
                    .string("outputDir", "Project-relative folder for the PNGs. Default build/map-authoring/<map>.")
                    .build();
        }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            MapBases.Base base = MapBases.find(arguments.getString("map"));
            String dir = arguments.optString("outputDir", "");
            Path out = dir.isBlank() ? context.projectRoot().resolve("build/map-authoring").resolve(base.id().replace('/', '-'))
                    : context.resolveInsideProject(dir);
            JSONObject edits = arguments.optJSONObject("edits");
            MapSketch sketch = sketch(base, edits);
            boolean bridge = arguments.optBoolean("bridgeRoads", false);
            String skinName = arguments.optString("skin", "").trim().toUpperCase(Locale.ROOT);
            boolean all = skinName.equals("ALL");
            BiomeSkin skin = skinName.isEmpty() || all ? BiomeSkin.of(base.builtAs()) : BiomeSkin.valueOf(skinName);
            boolean marked = arguments.optBoolean("marked", true);
            Files.createDirectories(out);
            try (MapsSession session = new MapsSession(context.projectRoot())) {
                if (bridge) sketch = session.bridgeRoads(base, sketch);
                MapsSession.Built built = session.build(base, sketch, skin);
                Path picture = out.resolve(skin.name().toLowerCase(Locale.ROOT) + (marked ? "-marked" : "") + ".png");
                write(session.picture(built, sketch, marked, null), picture);
                JSONObject result = new JSONObject().put("picture", picture.toString())
                        .put("problems", new JSONArray(built.problems())).put("skin", skin.name())
                        .put("towns", skin.adaptation(MapSketch.Program.TOWN)).put("sketch", sketch.json());
                StringBuilder text = new StringBuilder(picture + "\n");
                if (all) {
                    Path sheet = out.resolve("all-worlds.png");
                    write(session.contactSheet(base, sketch), sheet);
                    result.put("contactSheet", sheet.toString());
                    text.append(sheet).append('\n');
                }
                text.append(built.problems().isEmpty() ? "All checks pass.\n" : String.join("\n", built.problems()) + "\n");
                return built.problems().isEmpty() ? McpToolResult.of(text.toString(), result)
                        : new McpToolResult(text.toString(), result, false);
            }
        }
    }

    /** The base's sketch with {@code edits} laid over it: its own places unless the edits name places. */
    static MapSketch sketch(MapBases.Base base, JSONObject edits) throws Exception {
        if (edits == null) return base.sketch();
        JSONObject merged = new JSONObject(edits.toString()).put("base", base.id());
        if (!merged.has("places")) merged.put("places", base.sketch().json().getJSONArray("places"));
        return MapSketch.read(merged);
    }

    private static void write(BufferedImage image, Path target) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "map-", ".png");
        ImageIO.write(image, "png", temp.toFile());
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
