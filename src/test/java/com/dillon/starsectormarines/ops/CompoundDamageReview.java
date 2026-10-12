package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.AnimatedGifWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Optional read-only observer of the real Cold Account replay; never supplies targets or damage. */
final class CompoundDamageReview implements AutoCloseable {
    private record Tracked(int index, Doodad original, String damaged, String destroyed) {
        boolean paired() { return damaged != null && destroyed != null; }
    }
    private final BattleSimulation sim;
    private final Path directory;
    private final List<Tracked> props = new ArrayList<>();
    private final BattleReviewFrameRenderer overview, detail;
    private final ReviewImpactFx impacts;
    private AnimatedGifWriter animation;
    private JSONObject beforeBombardment;
    private int peakSmokeEvents, peakFireEvents, peakDustEvents;
    private int frames;

    CompoundDamageReview(BattleSimulation sim, Path parent) throws Exception {
        this.sim = sim;
        directory = parent.resolve("prop-damage"); Files.createDirectories(directory);
        var layout = sim.getColdAccount().layout();
        int left = sim.getGrid().getWidth(), right = 0, bottom = sim.getGrid().getHeight(), top = 0;
        for (int y = 0; y < sim.getGrid().getHeight(); y++) for (int x = 0; x < sim.getGrid().getWidth(); x++)
            if (sim.getTopology().isWall(x,y) && Math.hypot(x+.5f-layout.strikeX(),y+.5f-layout.strikeY()) <= layout.strikeRadius()) {
                left = Math.min(left,x); right = Math.max(right,x); bottom = Math.min(bottom,y); top = Math.max(top,y);
            }
        for (int i = 0; i < sim.getDoodads().size(); i++) {
            Doodad p = sim.getDoodads().get(i);
            if (p.definitionId() == null || p.cover <= 0 || Math.hypot(p.cellX+.5f-layout.strikeX(),p.cellY+.5f-layout.strikeY()) > layout.strikeRadius()) continue;
            props.add(new Tracked(i,p,variant(p.definitionId(),"damaged"),variant(p.definitionId(),"destroyed")));
        }
        float cx = (left+right+1)*.5f, cy = (bottom+top+1)*.5f;
        float across = Math.max(right-left+9,(top-bottom+9)*1296f/834f);
        overview = new BattleReviewFrameRenderer(Path.of("mod"),1296,864,cx,cy,across).withImpactEffects();
        // The densest 24-cell neighborhood supplies a stable, game-scale detail camera.
        Tracked focus = props.stream().max((a,b) -> Integer.compare(neighbors(a),neighbors(b))).orElse(null);
        detail = new BattleReviewFrameRenderer(Path.of("mod"),960,640,
                focus == null ? cx : focus.original.cellX+.5f,
                focus == null ? cy : focus.original.cellY+.5f,32).withImpactEffects();
        impacts = new ReviewImpactFx(Path.of("mod"));
        stills("initial");
        writeReport();
    }
    /** Cover barriers insert the state before their shape; other damage families append it. */
    private static String variant(String id, String state) {
        String candidate = id + "-" + state;
        for (String prefix : List.of("doodad.industrial-fence-","doodad.sandbag-"))
            if (id.startsWith(prefix)) candidate = prefix + state + "-" + id.substring(prefix.length());
        return TileRegistry.installed().doodad(candidate) == null ? null : candidate;
    }
    private int neighbors(Tracked anchor) {
        int n = 0;
        for (Tracked p : props) if (Math.abs(p.original.cellX-anchor.original.cellX) < 12
                && Math.abs(p.original.cellY-anchor.original.cellY) < 8) n++;
        return n;
    }
    void tick() throws Exception {
        impacts.feed(sim);
        var intervention = sim.getColdAccount();
        if (intervention.bombardTick() < 0) return;
        if (beforeBombardment == null) {
            beforeBombardment = inventory(false);
            stills("before-bombardment");
            animation = new AnimatedGifWriter(directory.resolve("bombardment.gif"),200,AnimatedGifWriter.Palette.ADAPTIVE);
        }
        peakSmokeEvents = Math.max(peakSmokeEvents,sim.getSmokePuffsThisFrame().size());
        peakFireEvents = Math.max(peakFireEvents,sim.getFireBurstsThisFrame().size());
        peakDustEvents = Math.max(peakDustEvents,sim.getWallDustsThisFrame().size());
        int elapsed = sim.getSimTickIndex()-intervention.bombardTick();
        // Record the complete strike, plus a smoke-settling tail; departure is covered by the main replay.
        if (elapsed % 30 == 0 && (intervention.patrolTick() < 0 || sim.getSimTickIndex()-intervention.patrolTick() <= 180)) {
            animation.append(frame(overview)); frames++;
            if (elapsed % 150 == 0) {
                stills(String.format("strike-%05d",elapsed)); writeReport();
            }
        }
    }
    private BufferedImage frame(BattleReviewFrameRenderer renderer) throws Exception {
        var source = renderer.render(sim,"Cold Account / " + sim.getSimTickIndex()/30 + "s / "
                + sim.getColdAccount().displayName(),ReviewAnnotations.NONE,impacts);
        BufferedImage opaque = new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics(); g.drawImage(source,0,0,null); g.dispose(); return opaque;
    }
    private void stills(String name) throws Exception {
        ImageIO.write(frame(overview),"png",directory.resolve(name+".png").toFile());
        ImageIO.write(frame(detail),"png",directory.resolve(name+"-detail.png").toFile());
    }
    private JSONObject inventory(boolean positions) throws Exception {
        Map<String,int[]> totals = new TreeMap<>(); JSONArray rows = new JSONArray();
        for (Tracked tracked : props) {
            Doodad original = tracked.original, current = sim.getDoodads().get(tracked.index);
            String id = original.definitionId(), now = current.definitionId();
            String state = id.equals(now) ? "intact" : now != null && now.equals(tracked.destroyed) ? "destroyed"
                    : now != null && now.equals(tracked.damaged) ? "damaged" : "changed";
            int[] counts = totals.computeIfAbsent(id,k -> new int[5]); counts[0]++;
            counts[state.equals("intact") ? 1 : state.equals("damaged") ? 2 : state.equals("destroyed") ? 3 : 4]++;
            if (positions) rows.put(new JSONObject().put("original",id).put("current",now).put("state",state)
                    .put("x",original.cellX).put("y",original.cellY).put("damagePair",tracked.paired())
                    .put("groundDamaged",sim.getTopology().hasTag(original.cellX,original.cellY,CellTopology.Tag.GROUND_DAMAGED))
                    .put("blocking",!sim.getGrid().isWalkable(original.cellX,original.cellY)).put("cover",current.cover));
        }
        JSONArray families = new JSONArray();
        for (var e : totals.entrySet()) {
            int[] n = e.getValue();
            families.put(new JSONObject().put("id",e.getKey()).put("total",n[0]).put("intact",n[1])
                    .put("damagePair",variant(e.getKey(),"damaged") != null && variant(e.getKey(),"destroyed") != null)
                    .put("damaged",n[2]).put("destroyed",n[3]).put("changed",n[4]));
        }
        return new JSONObject().put("tick",sim.getSimTickIndex()).put("families",families).put("props",rows);
    }
    private void writeReport() throws Exception {
        var c = sim.getColdAccount();
        var report = new JSONObject().put("scope","Initial positive-cover doodads centered within the authored strike circle")
                .put("phase",c.phase().name()).put("missionComplete",sim.isComplete()).put("frames",frames)
                .put("initialWalls",c.initialWalls()).put("remainingWalls",c.remainingWalls()).put("shots",c.shotsFired())
                .put("beforeBombardment",beforeBombardment == null ? JSONObject.NULL : beforeBombardment)
                .put("current",inventory(true)).put("peakSmokeEventsPerTick",peakSmokeEvents)
                .put("peakFireEventsPerTick",peakFireEvents).put("peakDustEventsPerTick",peakDustEvents);
        Files.writeString(directory.resolve("inventory.json"),report.toString(2));
    }
    @Override public void close() throws Exception {
        try { stills("aftermath"); writeReport(); }
        finally { if (animation != null) animation.close(); }
    }
}
