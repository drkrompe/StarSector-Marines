package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimLane;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlPanel;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owner mockups for showing a primary's magazine and readiness at the direct-control reticle: four candidate designs,
 * each in four moments, drawn over a real look frame (generated ground, play zoom) under the real active HUD. The
 * candidate geometry lives only here, as Java2D strokes; {@link DirectControlReticle} stays the production reticle
 * until a design is picked. A 4x inset of the cursor sits at the top of each frame, since the reticle is 14 px.
 */
public final class ReticleAmmoMockupSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "reticle-ammo-mockups"; }
    @Override public String label() { return "Reticle ammo and readiness mockups"; }

    /** The candidate designs. */
    private enum Design { ARC, PIPS, CLOCK, HYBRID }

    /**
     * One moment of the controlled Marine's primary: {@code readiness} is how far the trigger is back from its reload
     * or cycle (1 when it would fire now).
     */
    private record Moment(String name, String weaponId, EquipmentGrade grade, int rounds, float readiness,
                          boolean reloading) {}

    private static final List<Moment> MOMENTS = List.of(
            new Moment("smg-full", "weapon.smg", EquipmentGrade.MILSPEC, 10, 1f, false),
            new Moment("smg-low", "weapon.smg", EquipmentGrade.MILSPEC, 2, 1f, false),
            new Moment("smg-reloading", "weapon.smg", EquipmentGrade.MILSPEC, 0, .6f, true),
            new Moment("pulse-cycling", "weapon.pulse-rifle", EquipmentGrade.SERVICE, 0, .4f, false));

    private static final int W = LookReview.WIDTH;
    private static final int H = LookReview.HEIGHT;
    /**
     * Where the cursor sits from the controlled Marine, in cells: up and to the right, inside the SMG's 3.1-cell
     * optimal range, so the tick is white and only the ammo cue changes between frames.
     */
    private static final float AIM_DX = 2.2f;
    private static final float AIM_DY = 1.9f;

    private static final Color WHITE = DirectControlReticle.GOOD;
    private static final Color DIM = new Color(0xF4, 0xF6, 0xF8, 46);
    private static final Color REFILL = new Color(0xF4, 0xF6, 0xF8, 140);

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(context.modRoot().resolve(path)));
        try {
            SceneRegistries.installArmoury(context.projectRoot());
            HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
            HeadlessUiRenderer hud = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
            LookGround ground = LookGround.verdant();
            MapResult map = ground.generate();
            LookGround.Site site = LookGround.openSite(map, 64, 36);
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            for (String weaponId : List.of("weapon.smg", "weapon.pulse-rifle")) {
                Staged staged = stage(context, ground, map, site, weaponId);
                for (Moment moment : MOMENTS) {
                    if (!moment.weaponId().equals(weaponId)) continue;
                    var snapshot = snapshot(moment);
                    BufferedImage base = DirectControlUiSnapshotSuite.activeComposite(hud, context.modRoot(), W, H,
                            1f, snapshot, DirectControlUiSnapshotSuite.marinePlate(context.modRoot(), true).document(),
                            false, false, graphics -> graphics.drawImage(staged.frame(), 0, 0, null));
                    for (Design design : Design.values()) {
                        BufferedImage frame = copy(base);
                        Graphics2D graphics = frame.createGraphics();
                        smooth(graphics);
                        reticle(graphics, design, moment, staged);
                        inset(graphics, base, design, moment, staged);
                        graphics.dispose();
                        artifacts.add(new SnapshotArtifact(design.name().toLowerCase(Locale.ROOT) + "-"
                                + moment.name() + ".png", frame));
                    }
                }
            }
            return artifacts;
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    /** The look frame of one weapon's scene, and where the controlled Marine and the cursor fall on it. */
    private record Staged(BufferedImage frame, float bodyX, float bodyY, float aimX, float aimY, Mark leftMark) {}

    /**
     * A fire team with {@code weaponId} on real ground, a militia picket up the field, and the first Marine under
     * direct control, turned to the cursor for a second and a half before the frame is taken.
     */
    private static Staged stage(SnapshotContext context, LookGround ground, MapResult map, LookGround.Site site,
                                String weaponId) throws Exception {
        var placement = new LookReview.Placement(ground, map, site);
        float[] at = placement.openNear(26f, 14f);
        float[] enemy = placement.openNear(26f + AIM_DX + 4f, 14f + AIM_DY + 6f);
        SceneBuilder builder = placement.builder();
        builder.squad("team").faction(Faction.MARINE).type(UnitType.MARINE).size(4)
                .inFile((int) at[0] - site.x0(), (int) at[1] - site.y0(), -2, -1)
                .primary(weaponId).stationary().done();
        builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                .inFile((int) enemy[0] - site.x0(), (int) enemy[1] - site.y0(), 2, 0)
                .primary("weapon.smg").stationary().done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            sim.random().setSeed(20261027L);
            long marine = world.members("team")[0];
            sim.directControl().enter(marine);
            float bodyX = sim.world().renderX(marine);
            float bodyY = sim.world().renderY(marine);
            float aimX = bodyX + AIM_DX;
            float aimY = bodyY + AIM_DY;
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            for (int tick = 0; tick < 90; tick++) {
                sim.directControl().submit(new ManualIntent(0f, 0f, aimX, aimY, false, false));
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
            }
            bodyX = sim.world().renderX(marine);
            bodyY = sim.world().renderY(marine);
            // Centred a little above the Marine, so the HUD along the bottom leaves the team and picket in view.
            float focusX = bodyX + 4f;
            float focusY = bodyY + 3f;
            BufferedImage frame = BattleReviewFrameRenderer.look(context.modRoot(), W, H, focusX, focusY,
                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "", ReviewAnnotations.NONE, fx);
            BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
            camera.setViewport(0, 0, W, H, (float) Math.floor(W / BattleReviewFrameRenderer.PLAY_ZOOM_CELLS));
            camera.centerOn(focusX, focusY);
            WeaponDef weapon = WeaponRegistry.require(weaponId);
            float distance = (float) Math.hypot(AIM_DX, AIM_DY);
            Mark mark = DirectControlFireMarks.of(true, distance, ManualPointFire.optimalRangeCells(weapon),
                    weapon.range);
            return new Staged(frame, camera.cellToScreenX(bodyX), H - camera.cellToScreenY(bodyY),
                    camera.cellToScreenX(aimX), H - camera.cellToScreenY(aimY), mark);
        }
    }

    /** The status row the HUD would publish for {@code moment}. */
    private static BattleDirectControlStatus.Snapshot snapshot(Moment moment) {
        WeaponDef weapon = WeaponRegistry.require(moment.weaponId());
        float cycle = InfantryCombatStats.cooldown(weapon, moment.grade(), SoldierProfile.REGULAR);
        BattleDirectControlStatus.WeaponStatus status;
        if (weapon.magazine() == null) {
            status = BattleDirectControlStatus.marineWeapon(weapon, moment.grade(),
                    cycle * (1f - moment.readiness()), cycle, 0, 0f);
        } else {
            float reload = InfantryCombatStats.reload(weapon, moment.grade(), SoldierProfile.REGULAR);
            status = BattleDirectControlStatus.marineWeapon(weapon, moment.grade(), 0f, cycle, 0, 0f,
                    moment.rounds(), moment.reloading() ? reload * (1f - moment.readiness()) : 0f, reload);
        }
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MARINE, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), base.name(), base.carrier(),
                base.durability(), List.of(status), List.of(), MarineSprint.EMPTY);
    }

    private static final float TICK_INNER = 5f;
    private static final float TICK_OUTER = 14f;
    private static final float TICK_WIDTH = 1.5f;
    private static final float ARC_RADIUS = 22f;
    /** The arc spans this many degrees either side of the left button's side. */
    private static final float ARC_HALF_SPAN = 70f;

    /**
     * The production right tick and centre dot, then the left button's tick and its ammo as {@code design} draws
     * them. All in screen pixels, y down.
     */
    private static void reticle(Graphics2D graphics, Design design, Moment moment, Staged staged) {
        float x = staged.aimX();
        float y = staged.aimY();
        float fx = x - staged.bodyX();
        float fy = y - staged.bodyY();
        float length = (float) Math.hypot(fx, fy);
        float ux = fx / length;
        float uy = fy / length;
        // The body's left of its line of fire, on a y-down screen.
        float lx = uy;
        float ly = -ux;
        var marks = new DirectControlFireMarks.Marks(Mark.NONE, Mark.GOOD);
        // The production reticle is laid out y up; build it mirrored and flip it back, as the HUD suite does.
        for (var stroke : DirectControlReticle.strokes(marks, -1f, -1f, x, -y, fx, -fy,
                DirectControlPanel.controlledColor())) {
            stroke(graphics, new DirectControlAimLane.Stroke(stroke.x(), -stroke.y(), stroke.endX(), -stroke.endY(),
                    stroke.width(), stroke.color()));
        }
        WeaponDef weapon = WeaponRegistry.require(moment.weaponId());
        boolean magazine = weapon.magazine() != null;
        int capacity = magazine ? weapon.magazine().size(moment.grade()) : weapon.burstCount;
        boolean ready = moment.readiness() >= 1f;
        Color tick = DirectControlReticle.color(staged.leftMark());
        switch (design) {
            case ARC, PIPS -> {
                if (ready) {
                    line(graphics, x, y, lx, ly, TICK_INNER, TICK_OUTER, tick);
                } else {
                    // The production "will not fire" tick: two wide dashes in grey.
                    line(graphics, x, y, lx, ly, TICK_INNER, TICK_INNER + 3.5f, DirectControlReticle.HELD);
                    line(graphics, x, y, lx, ly, TICK_OUTER - 3.5f, TICK_OUTER, DirectControlReticle.HELD);
                }
                if (design == Design.ARC) arc(graphics, x, y, lx, ly, ux, uy, moment, magazine, capacity);
                else pips(graphics, x, y, moment, magazine, capacity);
            }
            case CLOCK, HYBRID -> {
                float grown = TICK_INNER + (TICK_OUTER - TICK_INNER) * Math.min(1f, moment.readiness());
                if (!ready) line(graphics, x, y, lx, ly, grown, TICK_OUTER, DIM);
                line(graphics, x, y, lx, ly, TICK_INNER, grown, tick);
                if (design == Design.HYBRID) {
                    if (magazine) arc(graphics, x, y, lx, ly, ux, uy, moment, true, capacity);
                } else if (magazine && ready && moment.rounds() * 4 <= capacity) {
                    count(graphics, x + lx * (TICK_OUTER + 8f), y + ly * (TICK_OUTER + 8f), moment.rounds());
                }
            }
        }
    }

    /**
     * A half ring on the left button's side, one segment a round, the first to fire nearest the line of fire. A
     * reload refills the segments in order, paler; a weapon with no magazine draws one segment that fills over its
     * cycle.
     */
    private static void arc(Graphics2D graphics, float x, float y, float lx, float ly, float ux, float uy,
                            Moment moment, boolean magazine, int capacity) {
        double side = Math.toDegrees(Math.atan2(ly, lx));
        double forward = Math.toDegrees(Math.atan2(uy, ux));
        // Sweep from the end nearest the line of fire.
        double delta = Math.IEEEremainder(forward - side, 360.0);
        double sign = delta >= 0 ? 1 : -1;
        double from = side + sign * ARC_HALF_SPAN;
        double span = -sign * 2 * ARC_HALF_SPAN;
        if (!magazine) {
            arcPiece(graphics, x, y, from, span, DIM, 2.5f);
            arcPiece(graphics, x, y, from, span * moment.readiness(), WHITE, 2.5f);
            return;
        }
        double step = span / capacity;
        double gap = Math.signum(span) * 3.0;
        int refilled = moment.reloading() ? (int) Math.floor(capacity * moment.readiness()) : 0;
        int spent = capacity - moment.rounds();
        for (int i = 0; i < capacity; i++) {
            Color color;
            if (moment.reloading()) color = i < refilled ? REFILL : DIM;
            else color = i < spent ? DIM : WHITE;
            arcPiece(graphics, x, y, from + i * step, step - gap, color, 2.5f);
        }
    }

    private static void arcPiece(Graphics2D graphics, float x, float y, double fromDeg, double spanDeg,
                                 Color color, float width) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(spanDeg) / 4.0));
        Path2D.Float path = new Path2D.Float();
        for (int s = 0; s <= steps; s++) {
            double a = Math.toRadians(fromDeg + spanDeg * s / steps);
            float px = x + (float) (Math.cos(a) * ARC_RADIUS);
            float py = y + (float) (Math.sin(a) * ARC_RADIUS);
            if (s == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        graphics.draw(path);
    }

    /**
     * A row of upright pips under the dot, one a round (one a burst round for a weapon with no magazine), and a
     * hairline beneath that fills over a reload or cycle.
     */
    private static void pips(Graphics2D graphics, float x, float y, Moment moment, boolean magazine, int capacity) {
        float pitch = 5f;
        float left = x - (capacity - 1) * pitch / 2f;
        int loaded = magazine ? moment.rounds() : (moment.readiness() >= 1f ? capacity : 0);
        for (int i = 0; i < capacity; i++) {
            graphics.setColor(i < loaded ? WHITE : DIM);
            graphics.fill(new Rectangle2D.Float(left + i * pitch - 1.2f, y + 18f, 2.4f, 6f));
        }
        if (moment.readiness() < 1f) {
            float width = (capacity - 1) * pitch + 2.4f;
            stroke(graphics, new DirectControlAimLane.Stroke(left - 1.2f, y + 28f, left - 1.2f + width, y + 28f,
                    1f, DIM));
            stroke(graphics, new DirectControlAimLane.Stroke(left - 1.2f, y + 28f,
                    left - 1.2f + width * moment.readiness(), y + 28f, 1f, WHITE));
        }
    }

    /** The rounds left, in small figures beside the left tick, once the load is a quarter or less. */
    private static void count(Graphics2D graphics, float x, float y, int rounds) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        String text = Integer.toString(rounds);
        var metrics = graphics.getFontMetrics();
        graphics.setColor(WHITE);
        graphics.drawString(text, x - metrics.stringWidth(text) / 2f, y + metrics.getAscent() / 2f - 1f);
    }

    private static void line(Graphics2D graphics, float x, float y, float dx, float dy, float from, float to,
                             Color color) {
        stroke(graphics, new DirectControlAimLane.Stroke(x + dx * from, y + dy * from, x + dx * to, y + dy * to,
                TICK_WIDTH, color));
    }

    private static void stroke(Graphics2D graphics, DirectControlAimLane.Stroke stroke) {
        graphics.setColor(stroke.color());
        graphics.setStroke(new BasicStroke(stroke.width(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.draw(new Line2D.Float(stroke.x(), stroke.y(), stroke.endX(), stroke.endY()));
    }

    /** Inset size and magnification: a 4x close-up of the cursor, top centre. */
    private static final int INSET_SOURCE = 100;
    private static final int INSET_SCALE = 4;

    /**
     * The cursor's surroundings from {@code base} scaled up, with the reticle drawn again at the inset's scale rather
     * than its pixels enlarged, so the close-up is as sharp as the strokes are, and a hairline round the source.
     */
    private static void inset(Graphics2D graphics, BufferedImage base, Design design, Moment moment, Staged staged) {
        int size = INSET_SOURCE * INSET_SCALE;
        int sx = Math.round(staged.aimX()) - INSET_SOURCE / 2;
        int sy = Math.round(staged.aimY()) - INSET_SOURCE / 2;
        int dx = (W - size) / 2;
        int dy = 24;
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(base, dx, dy, dx + size, dy + size, sx, sy, sx + INSET_SOURCE, sy + INSET_SOURCE, null);
        var saved = graphics.getTransform();
        var clip = graphics.getClip();
        graphics.clipRect(dx, dy, size, size);
        graphics.translate(dx, dy);
        graphics.scale(INSET_SCALE, INSET_SCALE);
        graphics.translate(-sx, -sy);
        reticle(graphics, design, moment, staged);
        graphics.setTransform(saved);
        graphics.setClip(clip);
        graphics.setColor(new Color(0xA4, 0xAA, 0xB0, 160));
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRect(dx, dy, size, size);
        graphics.drawRect(sx, sy, INSET_SOURCE, INSET_SOURCE);
    }

    private static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return out;
    }
}
