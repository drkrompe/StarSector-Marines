package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.SealDoor;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Picture options for the owner's {@code ruins-doors} pick: the ruins' hall joins, intake docks and works seals given
 * the ship's sliding leaf doors ({@code RoomDoor}: two leaves parting from a seam into their jambs) in place of today's
 * open gaps and one-cell seals. Each card is a real render through the look preset at 1920x1080, roofs off, with the
 * doors sketched over it in the ship door's own geometry, cut from today's door art as stand-ins for the ruins set the
 * art session would draw. Opt-in: {@code -Psnapshot=ruins-door-picks}.
 */
public final class RuinsDoorPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    /** What a door is: its leaf art, how thick it stands through the wall, and how many leaves part each way. */
    private enum Kind { HALL, CARGO, BLAST }

    /** How a door stands: answering the company, dead where its power died, sealed shut, or forced. */
    private enum State { ANSWERS, DEAD, SEALED, FORCED }

    /** A door across a run of cells: along x or y from {@code min} to {@code max}, its seam line at {@code across}. */
    private record Span(boolean spanX, float min, float max, float across) {
        float centreX() { return spanX ? (min + max) / 2f : across; }
        float centreY() { return spanX ? across : (min + max) / 2f; }
        Span widened(float to) {
            float mid = (min + max) / 2f;
            return new Span(spanX, mid - to / 2f, mid + to / 2f, across);
        }
    }

    /** A leaf's art: a region of a sheet, its jamb end left and its meeting end right, {@code cells} long. */
    private record Leaf(BufferedImage sheet, int x0, int y0, int w, int h, float cells) { }

    private Leaf hall, hallLocked, cargo, blast, cargoLocked;
    private BufferedImage clamp;

    @Override public String id() { return "ruins-door-picks"; }

    @Override public String label() { return "Ruins doors: picture options for the ruins-doors pick"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        loadArt(context.modRoot());
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), null);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = fixture.build()) {
            RuinsDelve delve = sim.getRuinsWorks().delve();
            List<Span> joins = joins(sim, delve);
            List<Span> docks = new ArrayList<>();
            for (RuinsDelve.Area e : delve.interior().entrances()) docks.add(span(sim, e));
            List<Span> seals = new ArrayList<>();
            for (SealDoor d : sim.getRuinsWorks().doors()) seals.add(span(d.cells()));

            // The plant: the intake hall and its joins.
            RuinsDelve.Area intake = hall(delve, RuinsDelve.PlantStage.INTAKE);
            BattleReviewFrameRenderer plant = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    intake.centreX() + .5f, intake.centreY() + .5f, 44f).withoutRoofs();
            BufferedImage plantBase = plant.render(sim, "");
            BattleCamera pc = plant.cameraOf(sim);
            out.add(card("today-plant-joins.png", plantBase, g ->
                    caption(g, "Today: the plant's halls meet at open three-cell gaps")));
            out.add(card("a-hall-and-dock-doors.png", plantBase, g -> {
                int k = 0;
                for (Span s : joins) {
                    State st = switch (k++ % 3) { case 0 -> State.ANSWERS; case 1 -> State.DEAD; default -> State.SEALED; };
                    door(g, pc, s, Kind.HALL, st);
                    tag(g, pc, s, "hall door: " + word(st), true);
                }
                caption(g, "a. Every hall join is a leaf door (recommended): the ship's sliding door, sized to its gap");
            }));
            out.add(card("c-joins-answer-only.png", plantBase, g -> {
                for (Span s : joins) door(g, pc, s, Kind.HALL, State.ANSWERS);
                caption(g, "c. The joins answer like a ship's, with no derelict states; seals stay as today");
            }));

            // An intake dock: the way in from open ground, a four-leaf cargo door.
            if (!docks.isEmpty()) {
                Span dock = docks.get(0);
                BattleReviewFrameRenderer dv = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        dock.centreX(), dock.centreY(), 26f).withoutRoofs();
                BufferedImage dockBase = dv.render(sim, "");
                BattleCamera dc = dv.cameraOf(sim);
                out.add(card("a-intake-cargo-door.png", dockBase, g -> {
                    door(g, dc, dock, Kind.CARGO, State.DEAD);
                    tag(g, dc, dock, "cargo door, " + Math.round(dock.max() - dock.min())
                            + " wide, four leaves: dead part-open", true);
                    caption(g, "a. An intake dock behind a cargo door: four telescoping leaves");
                }));
            }

            // The vault: its outer door and its hardened inner seal, as blast doors.
            RuinsDelve.Vault vault = delve.vault();
            RuinsDelve.Area vf = vault.footprint();
            float vx = (vault.door().x() + vault.innerDoor().x()) / 2f + .5f;
            float vy = (vault.door().y() + vault.innerDoor().y()) / 2f + .5f;
            BattleReviewFrameRenderer near = BattleReviewFrameRenderer.look(context.modRoot(), W, H, vx, vy, 26f)
                    .withoutRoofs();
            BufferedImage vaultBase = near.render(sim, "");
            BattleCamera vc = near.cameraOf(sim);
            Span inner = cellSpan(sim, vault.innerDoor().x(), vault.innerDoor().y());
            Span outer = cellSpan(sim, vault.door().x(), vault.door().y());
            out.add(card("today-vault.png", vaultBase, g ->
                    caption(g, "Today: the vault's doors are one cell, its seal a one-cell hardened plate")));
            out.add(card("a-vault-blast-doors.png", vaultBase, g -> {
                door(g, vc, inner.widened(3f), Kind.BLAST, State.SEALED);
                tag(g, vc, inner, "vault blast door, 3 wide: sealed, clamp on the seam", true);
                door(g, vc, outer.widened(2f), Kind.HALL, State.ANSWERS);
                tag(g, vc, outer, "secure door, 2 wide: answers", false);
                caption(g, "a. The vault and the secure stores behind blast doors, widened to three cells");
            }));

            // The derelict states, side by side on one join, close in.
            Span shown = joins.isEmpty() ? inner : joins.get(0);
            BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    shown.centreX(), shown.centreY(), 10f).withoutRoofs();
            BufferedImage closeBase = close.render(sim, "");
            BattleCamera cc = close.cameraOf(sim);
            BufferedImage states = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = graphics(states);
            State[] order = State.values();
            String[] notes = {
                    "Answers: powered, it parts for whoever comes through",
                    "Dead: stuck where its power died; the leaves narrow the way",
                    "Sealed: shut on its works' power, clamp across the seam",
                    "Forced: a breach tore a leaf out; open for good"};
            for (int q = 0; q < 4; q++) {
                BufferedImage cell = copy(closeBase);
                Graphics2D g = graphics(cell);
                door(g, cc, shown, Kind.HALL, order[q]);
                g.dispose();
                int ox = (q % 2) * W / 2, oy = (q / 2) * H / 2;
                sg.drawImage(cell, ox, oy, W / 2, H / 2, null);
                sg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                int tw = sg.getFontMetrics().stringWidth(notes[q]);
                sg.setColor(new Color(0, 0, 0, 200));
                sg.fillRoundRect(ox + 16, oy + H / 2 - 52, tw + 28, 38, 12, 12);
                sg.setColor(Color.WHITE);
                sg.drawString(notes[q], ox + 30, oy + H / 2 - 26);
            }
            sg.setColor(Color.BLACK);
            sg.fillRect(W / 2 - 2, 0, 4, H);
            sg.fillRect(0, H / 2 - 2, W, 4);
            caption(sg, "a. One door, four states: the derelict security story on every leaf");
            sg.dispose();
            out.add(new SnapshotArtifact("a-door-states.png", states));

            // (b) Only the works' seals become leaf doors; the joins stay the ruin's open gaps.
            SealDoor store = null;
            for (SealDoor d : sim.getRuinsWorks().doors()) {
                if (d.store()) {
                    store = d;
                    break;
                }
            }
            if (store != null) {
                Span ss = span(store.cells());
                Span sealed = ss.max() - ss.min() < 2f ? ss.widened(2f) : ss;
                BattleReviewFrameRenderer sv = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        ss.centreX(), ss.centreY(), 22f).withoutRoofs();
                BufferedImage storeBase = sv.render(sim, "");
                BattleCamera stc = sv.cameraOf(sim);
                out.add(card("today-sealed-store.png", storeBase, g ->
                        caption(g, "Today: a sealed store's door is one cell")));
                out.add(card("b-seals-only.png", storeBase, g -> {
                    door(g, stc, sealed, Kind.BLAST, State.SEALED);
                    tag(g, stc, sealed, "store seal, 2 wide: sealed", true);
                    caption(g, "b. Only the works' seals (gates, stores, bunker, vault) become leaf doors; joins stay open");
                }));
            }
        }
        return out;
    }

    // ----- where the doors go -----

    private static RuinsDelve.Area hall(RuinsDelve delve, RuinsDelve.PlantStage stage) {
        for (RuinsDelve.PlantHall h : delve.interior().halls()) if (h.stage() == stage) return h.footprint();
        return delve.interior().footprint();
    }

    /** Every gap in a plant hall's walls, as one span per run of open cells along a side. */
    private static List<Span> joins(BattleSimulation sim, RuinsDelve delve) {
        CellTopology topo = sim.getTopology();
        NavigationGrid grid = sim.getGrid();
        List<Span> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (RuinsDelve.PlantHall h : delve.interior().halls()) {
            RuinsDelve.Area f = h.footprint();
            // The ring of the hall's walls: its footprint's edge, or one cell out if the footprint is the floor.
            int ring = walls(topo, f, 0) >= walls(topo, f, 1) ? 0 : 1;
            int l = f.left() - ring, b = f.bottom() - ring, r = f.right() + ring, t = f.top() + ring;
            sides(out, seen, grid, topo, true, l + 1, r - 1, b);
            sides(out, seen, grid, topo, true, l + 1, r - 1, t);
            sides(out, seen, grid, topo, false, b + 1, t - 1, l);
            sides(out, seen, grid, topo, false, b + 1, t - 1, r);
        }
        return out;
    }

    private static int walls(CellTopology topo, RuinsDelve.Area f, int ring) {
        int n = 0;
        for (int x = f.left() - ring; x <= f.right() + ring; x++) {
            if (topo.inBounds(x, f.bottom() - ring) && topo.isWall(x, f.bottom() - ring)) n++;
            if (topo.inBounds(x, f.top() + ring) && topo.isWall(x, f.top() + ring)) n++;
        }
        return n;
    }

    private static void sides(List<Span> out, Set<Long> seen, NavigationGrid grid, CellTopology topo, boolean alongX,
                              int from, int to, int line) {
        int run = -1;
        for (int a = from; a <= to + 1; a++) {
            int x = alongX ? a : line, y = alongX ? line : a;
            boolean gap = a <= to && topo.inBounds(x, y) && !topo.isWall(x, y) && grid.isWalkable(x, y);
            if (gap && run < 0) run = a;
            if (!gap && run >= 0) {
                long key = ((long) (alongX ? run : line) << 32) ^ (alongX ? line : run) ^ (alongX ? 1L << 62 : 0L);
                if (a - run >= 2 && a - run <= 8 && seen.add(key)) out.add(new Span(alongX, run, a, line + .5f));
                run = -1;
            }
        }
    }

    /** The door across a way's area: spanning its longer side. */
    private static Span span(BattleSimulation sim, RuinsDelve.Area a) {
        boolean alongX = a.width() >= a.height();
        return alongX ? new Span(true, a.left(), a.right() + 1, a.centreY() + .5f)
                : new Span(false, a.bottom(), a.top() + 1, a.centreX() + .5f);
    }

    private static Span span(int[][] cells) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
        }
        return maxX - minX >= maxY - minY ? new Span(true, minX, maxX + 1, minY + .5f)
                : new Span(false, minY, maxY + 1, minX + .5f);
    }

    /** A one-cell door in a wall: spanning along the wall it stands in. */
    private static Span cellSpan(BattleSimulation sim, int x, int y) {
        CellTopology topo = sim.getTopology();
        boolean wallNorthSouth = topo.isWall(x, y - 1) || topo.isWall(x, y + 1);
        return wallNorthSouth ? new Span(false, y, y + 1, x + .5f) : new Span(true, x, x + 1, y + .5f);
    }

    // ----- drawing the doors -----

    private void loadArt(Path mod) throws Exception {
        BufferedImage ship = ImageIO.read(mod.resolve("graphics/doors/ship-leaf.png").toFile());
        BufferedImage shipLocked = ImageIO.read(mod.resolve("graphics/doors/ship-leaf-locked.png").toFile());
        BufferedImage forcible = ImageIO.read(mod.resolve("graphics/doors/forcible-4.png").toFile());
        BufferedImage hardened = ImageIO.read(mod.resolve("graphics/doors/hardened-2.png").toFile());
        clamp = ImageIO.read(mod.resolve("graphics/doors/ship-clamp.png").toFile());
        hall = new Leaf(ship, 0, 0, ship.getWidth(), ship.getHeight(), 1.5f);
        hallLocked = new Leaf(shipLocked, 0, 0, shipLocked.getWidth(), shipLocked.getHeight(), 1.5f);
        cargo = new Leaf(forcible, 0, 0, forcible.getWidth() / 2, forcible.getHeight(), 2f);
        cargoLocked = cargo;
        blast = new Leaf(hardened, 0, 0, hardened.getWidth() / 2, hardened.getHeight(), 1f);
    }

    private void door(Graphics2D g, BattleCamera c, Span s, Kind kind, State state) {
        float open = switch (state) { case ANSWERS -> .5f; case DEAD -> .35f; case SEALED -> 0f; case FORCED -> .2f; };
        float ease = open * open * (3f - 2f * open);
        Leaf leaf = switch (kind) {
            case HALL -> state == State.SEALED ? hallLocked : hall;
            case CARGO -> state == State.SEALED ? cargoLocked : cargo;
            case BLAST -> blast;
        };
        float depth = switch (kind) { case HALL -> .55f; case CARGO -> .7f; case BLAST -> .9f; };
        int perSide = kind == Kind.CARGO ? 2 : 1;
        float half = (s.max() - s.min()) / 2f;
        float reach = half * (1f - ease);
        float ppc = c.cellPxSize();
        float dim = state == State.DEAD ? .72f : 1f;
        for (int side = -1; side <= 1; side += 2) {
            if (state == State.FORCED && side < 0) {
                // The torn leaf lies on the floor beside the way, skewed.
                float lx = s.min() + half * .5f, off = 1.3f;
                drawPiece(g, c, s, leaf, half, lx, s.across() - off, depth, false, 24f, .85f);
                continue;
            }
            float jamb = side < 0 ? s.min() : s.max();
            float seg = reach / perSide;
            for (int k = 0; k < perSide; k++) {
                // Telescoping leaves: each covers its share of the reach, the inner one nearest the seam.
                float a0 = jamb - side * k * seg, a1 = a0 - side * seg;
                float mid = (a0 + a1) / 2f;
                if (seg * ppc < 1.5f) continue;
                drawPiece(g, c, s, leaf, seg, mid, s.across(), depth, side > 0, 0f, dim);
            }
            // The jamb's lamp: green answering, amber dead, red sealed.
            Color lamp = switch (state) {
                case ANSWERS -> new Color(80, 255, 120);
                case DEAD, FORCED -> new Color(255, 170, 40);
                case SEALED -> new Color(255, 50, 40);
            };
            float lx = s.spanX() ? jamb : s.across(), ly = s.spanX() ? s.across() : jamb;
            int px = Math.round(c.cellToScreenX(lx)), py = Math.round(H - c.cellToScreenY(ly));
            int rad = Math.max(3, Math.round(ppc * .08f));
            g.setColor(lamp);
            g.fillOval(px - rad, py - rad, rad * 2, rad * 2);
        }
        if (state == State.SEALED) {
            float seam = (s.min() + s.max()) / 2f;
            Leaf cl = new Leaf(clamp, 0, 0, clamp.getWidth(), clamp.getHeight(), .9f);
            drawPiece(g, c, s, cl, Math.min(.9f, half * 2f), seam, s.across(), depth * 1.05f, false, 0f, 1f);
        }
    }

    /**
     * One leaf's piece still in the opening: the art's meeting end, {@code length} cells long along the door, centred
     * at {@code along}, flipped for the far leaf and turned for a door spanning y.
     */
    private static void drawPiece(Graphics2D g, BattleCamera c, Span s, Leaf leaf, float length, float along,
                                  float across, float depth, boolean flip, float skewDegrees, float dim) {
        float ppc = c.cellPxSize();
        int srcW = Math.max(1, Math.min(leaf.w(), Math.round(leaf.w() * length / leaf.cells())));
        int sx0 = leaf.x0() + leaf.w() - srcW;
        int pw = Math.max(1, Math.round(length * ppc)), ph = Math.max(1, Math.round(depth * ppc));
        BufferedImage piece = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (flip) pg.drawImage(leaf.sheet(), pw, 0, 0, ph, sx0, leaf.y0(), sx0 + srcW, leaf.y0() + leaf.h(), null);
        else pg.drawImage(leaf.sheet(), 0, 0, pw, ph, sx0, leaf.y0(), sx0 + srcW, leaf.y0() + leaf.h(), null);
        if (dim < 1f) {
            pg.setComposite(AlphaComposite.SrcAtop);
            pg.setColor(new Color(20, 18, 16, Math.round((1f - dim) * 255)));
            pg.fillRect(0, 0, pw, ph);
        }
        pg.dispose();
        float cx = s.spanX() ? along : across, cy = s.spanX() ? across : along;
        float x = c.cellToScreenX(cx), y = H - c.cellToScreenY(cy);
        AffineTransform keep = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians((s.spanX() ? 0f : -90f) + skewDegrees));
        g.drawImage(piece, -pw / 2, -ph / 2, null);
        g.setTransform(keep);
    }

    // ----- cards -----

    private interface Paint { void on(Graphics2D g); }

    private static SnapshotArtifact card(String file, BufferedImage base, Paint paint) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        paint.on(g);
        g.dispose();
        return new SnapshotArtifact(file, card);
    }

    private static String word(State s) {
        return switch (s) { case ANSWERS -> "answers"; case DEAD -> "dead, part-open"; case SEALED -> "sealed";
            case FORCED -> "forced"; };
    }

    /** A label beside a door: above or to the right of it when {@code after}, else below or to the left. */
    private static void tag(Graphics2D g, BattleCamera c, Span s, String label, boolean after) {
        int cx = Math.round(c.cellToScreenX(s.centreX())), cy = Math.round(H - c.cellToScreenY(s.centreY()));
        int off = Math.round(c.cellPxSize() * 1.4f);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        int half = g.getFontMetrics().stringWidth(label) / 2 + 14;
        if (s.spanX()) cy += after ? -off : off;
        else cx += after ? off + half : -off - half;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        int tw = g.getFontMetrics().stringWidth(label);
        int x = Math.max(8, Math.min(W - tw - 22, cx - tw / 2 - 7));
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, cy - 16, tw + 14, 24, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(label, x + 7, cy + 2);
    }

    private static void caption(Graphics2D g, String text) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, 18, tw + 40, 52, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 53);
    }

    private static Graphics2D graphics(BufferedImage card) {
        Graphics2D g = card.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    private static BufferedImage copy(BufferedImage frame) {
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.dispose();
        return out;
    }
}
