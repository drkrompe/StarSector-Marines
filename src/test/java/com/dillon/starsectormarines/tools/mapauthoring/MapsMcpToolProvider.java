package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolProvider;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page headless: list the story maps and their owned seed, draw one dressed as a world or every world with
 * its patch marked, check it, and save its patch, so a session edits a pooled map without the workbench. The same
 * {@link StoryMapsSession} the page drives. Pictures go under {@code build/map-authoring/} unless told elsewhere
 * inside the project; only {@code map_save} writes into mod/, and only an entry whose acceptance passes.
 */
public final class MapsMcpToolProvider implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(new ListMaps(), new RenderMap(), new SaveMap());
    }

    private static final class ListMaps implements McpTool {
        @Override public String name() { return "map_list"; }
        @Override public String description() {
            return "List the story maps the Maps page opens (data/world/pools/<map>.pool.json, story-map-pools.md), "
                    + "each with its owned seed on the home biome and that entry's patch, and the worlds a map is dressed as.";
        }
        @Override public JSONObject inputSchema() { return McpSchema.object().build(); }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            JSONArray maps = new JSONArray();
            StringBuilder text = new StringBuilder("Story maps (home biome " + StoryMapsSession.HOME + "):\n");
            try (StoryMapsSession session = new StoryMapsSession(context.projectRoot())) {
                for (String id : StoryMapsSession.maps()) {
                    StoryMapsSession.Opened o = session.open(id);
                    String patch = o.entry().patch().isEmpty() ? "{}" : PoolEntryText.patch(o.entry().patch());
                    maps.put(new JSONObject().put("map", id).put("seed", o.entry().seed()).put("story", o.entry().story())
                            .put("patch", new JSONObject(patch)));
                    text.append(String.format(Locale.ROOT, "  %-18s seed %-4d%s patch %s%n", id, o.entry().seed(),
                            o.entry().story() ? " story" : "      ", patch));
                }
            }
            JSONArray worlds = new JSONArray();
            for (MapWorld w : MapWorld.values()) worlds.put(w.name());
            text.append("Worlds: ").append(worlds.join(", ").replace("\"", "")).append('\n');
            return McpToolResult.of(text.toString(), new JSONObject().put("maps", maps).put("worlds", worlds));
        }
    }

    private static McpSchema entryArguments(McpSchema schema) {
        return schema.requiredString("map", "Story map id from map_list")
                .object("patch", "The patch to use instead of the saved one (pool form: pin, ruin, place; lift, bores, holdouts)", false);
    }

    /** The map's home entry, with the given patch in place of its own. */
    private static StoryMapPool.Entry entry(StoryMapsSession.Opened o, JSONObject arguments) throws Exception {
        JSONObject patch = arguments.optJSONObject("patch");
        return patch == null ? o.entry() : StoryMapsSession.withPatch(o.entry(), MapPatch.read(patch));
    }

    private static final class RenderMap implements McpTool {
        @Override public String name() { return "map_render"; }
        @Override public String description() {
            return "Generate a story map's owned seed on its home biome with its patch, run the mission's acceptance "
                    + "(the storyMapPools check), and draw it whole at 1920x1080 in the battle's look preset dressed as a "
                    + "world, with the patch marked. 'patch' replaces the saved one, so edits can be tried without saving. "
                    + "'world' is one of map_list's; ALL also draws the all-worlds sheet. Writes PNGs only, never into mod/.";
        }
        @Override public JSONObject inputSchema() {
            return entryArguments(McpSchema.object())
                    .string("world", "World to dress it as (map_list); default ARID; ALL adds the contact sheet")
                    .bool("marked", "Draw the patch's marks and the check results. Default true.")
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
            try (StoryMapsSession session = new StoryMapsSession(context.projectRoot())) {
                StoryMapsSession.Opened o = session.open(id);
                StoryMapPool.Entry e = entry(o, arguments);
                List<String> problems = session.check(o.map(), e);
                StoryMapsSession.Stood stood = session.stand(o.map(), e);
                JSONObject result = new JSONObject().put("problems", new JSONArray(problems)).put("world", world.name())
                        .put("seed", e.seed());
                StringBuilder text = new StringBuilder();
                MapsRender.Frame f = session.frame(world, MapsRender.WIDTH, MapsRender.HEIGHT);
                if (f != null) {
                    BufferedImage image = f.image();
                    if (marked) {
                        Graphics2D g = image.createGraphics();
                        MapsRender.mark(g, f, stood, e.patch(), null);
                        MapsRender.problems(g, image.getHeight(), problems, "The mission's acceptance passes.");
                        g.dispose();
                    }
                    Path picture = out.resolve(world.name().toLowerCase(Locale.ROOT) + (marked ? "-marked" : "") + ".png");
                    write(image, picture);
                    result.put("picture", picture.toString());
                    text.append(picture).append('\n');
                }
                JSONArray places = new JSONArray();
                for (StoryMapsSession.Place p : stood.places()) places.put(new JSONObject().put("name", p.name()).put("x", p.x()).put("y", p.y()));
                JSONArray structures = new JSONArray();
                for (StoryMapsSession.LaidStructure s : stood.structures()) {
                    structures.put(new JSONObject().put("name", s.name()).put("box", new JSONArray(new int[]{s.x0(), s.y0(), s.x1(), s.y1()})));
                }
                result.put("places", places).put("structures", structures);
                text.append("Places a pin moves: ");
                for (StoryMapsSession.Place p : stood.places()) text.append(p.name()).append(' ').append(p.x()).append(',').append(p.y()).append("; ");
                text.append("\nStructures a placement names: ");
                for (StoryMapsSession.LaidStructure s : stood.structures()) {
                    text.append(s.name()).append(' ').append(s.x0()).append(',').append(s.y0()).append("; ");
                }
                text.append('\n');
                if (all) {
                    Path sheet = out.resolve("all-worlds.png");
                    write(session.contactSheet(o.map(), e), sheet);
                    result.put("contactSheet", sheet.toString());
                    text.append(sheet).append('\n');
                }
                text.append(problems.isEmpty() ? "The mission's acceptance passes.\n" : String.join("\n", problems) + "\n");
                return new McpToolResult(text.toString(), result, !problems.isEmpty());
            }
        }
    }

    private static final class SaveMap implements McpTool {
        @Override public String name() { return "map_save"; }
        @Override public String description() {
            return "Save a story map's patch into its pool file in mod/ (the home biome's owned entry only; every other "
                    + "entry and the candidates stay byte for byte), replaced atomically, after the mission's acceptance "
                    + "passes. Refused, writing nothing, while it fails; the reply names the failure.";
        }
        @Override public JSONObject inputSchema() { return entryArguments(McpSchema.object()).build(); }
        @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            try (StoryMapsSession session = new StoryMapsSession(context.projectRoot())) {
                StoryMapsSession.Opened o = session.open(arguments.getString("map"));
                StoryMapPool.Entry e = entry(o, arguments);
                List<String> problems = session.check(o.map(), e);
                if (!problems.isEmpty()) {
                    return new McpToolResult("Not saved:\n" + String.join("\n", problems), new JSONObject().put("problems", new JSONArray(problems)), true);
                }
                Path written = session.save(o, e, context.projectRoot());
                return McpToolResult.of("Saved " + written, new JSONObject().put("written", written.toString()));
            }
        }
    }

    private static void write(BufferedImage image, Path target) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "map-", ".png");
        ImageIO.write(image, "png", temp.toFile());
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
