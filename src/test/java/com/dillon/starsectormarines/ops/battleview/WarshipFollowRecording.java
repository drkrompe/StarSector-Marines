package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipWreck;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One warship fight recorded through a {@link DuelCamera}: which hulls it frames, how often a frame is taken, a
 * close-up inset of a hull that has just been hit or killed, and a locator inset that says where on the map the view
 * is.
 *
 * <p><b>Framed:</b> every hull over the map that has neither left it nor is still inbound beyond its edge, and a hull
 * destroyed in the last few seconds at the place it was last drawn, so the kill and the burning wreck stay in shot.
 * With more than two hulls up, one engagement: the closest hostile pair and the hulls near them.
 * A hull past the edge is clamped to it rather than dragging the view off the map.
 *
 * <p><b>Cadence:</b> while two hostile hulls are over the map and rounds are flying (or have in the last five
 * seconds) a frame is taken every {@link #denseTicks()} ticks, so rounds are seen in flight; otherwise, on the
 * approach and between passes, the scene's own cadence applies, so a long approach plays fast; after the last hostile
 * pair is broken it
 * records a short tail, and then nothing until another pair meets, so a duel decided early does not record minutes
 * of empty sky. A long fight halves its rate after every {@link #FRAMES_PER_DOUBLING} frames (twice at most), except
 * while a kill is being framed.
 *
 * <p><b>Close-up:</b> a hull whose structure has just dropped is shown about twice as close in an inset for a couple
 * of seconds (the dying hull for longer), so breaches, lost mounts and fires can be read; when the main view already
 * draws the hull nearly that large there is no close-up. The inset takes a corner no
 * framed hull is in, and keeps it while it stays clear.
 *
 * <p>Everything is read from the simulation and this recording's own history, so it is deterministic.
 * {@code -Dsnapshot.duelCamera=false} turns the whole thing off and the suite records the whole map at the scene's
 * cadence, as before; {@code -Dsnapshot.duelCamera.everyTicks=N} sets the dense cadence (default 6, five frames a
 * simulated second); {@code -Dsnapshot.duelCamera.inset=false} drops the close-up.
 */
public final class WarshipFollowRecording {

    /** Ticks a destroyed hull stays framed: the kill, the wreck going down, and a beat after. */
    static final int KILL_HOLD_TICKS = 150;
    /** Ticks the dense cadence holds after the last round in flight or structure hit, so it does not flicker between volleys. */
    static final int ENGAGED_HOLD_TICKS = 150;
    /** Ticks recorded densely after the last hostile pair is broken. */
    static final int TAIL_TICKS = 150;
    /** Ticks a hit hull stays in the close-up after its last structure loss. */
    static final int HIT_HOLD_TICKS = 60;
    /** The close-up never changes hull more often than this. */
    static final int INSET_MIN_TICKS = 30;
    private static final int DEFAULT_DENSE_TICKS = 6;
    /** A hull within this share of the focus pair's separation of either of them is framed with them. */
    static final float CLUSTER_SHARE = 0.5f;
    /** The focus pair is kept while its separation is within this multiple of the closest hostile pair's. */
    static final float PAIR_SWITCH_SHARE = 1.25f;
    /** Frames recorded before the dense interval doubles (two minutes of a fight at the default cadence). */
    static final int FRAMES_PER_DOUBLING = 600;
    /** {@code -Dsnapshot.duelCamera.trace=true} prints every frame's view, for reading the camera's motion. */
    private static final boolean TRACE = Boolean.getBoolean("snapshot.duelCamera.trace");
    private static final float TICK_SECONDS = 1f / 30f;
    /** The closest main view, in hull lengths of the largest hull, and never closer than this many cells. */
    private static final float MIN_ACROSS_HULLS = 5f;
    private static final float MIN_ACROSS_CELLS = 70f;
    /** The close-up's width in hull lengths of the hull it shows. */
    private static final float INSET_ACROSS_HULLS = 1.8f;
    private static final float INSET_MIN_ACROSS_CELLS = 24f;
    /** The tightest close-up, in hull lengths: closer than this crops a hull turned across the frame. */
    private static final float INSET_TIGHTEST_HULLS = 1.15f;
    /** The close-up aims at this many times the main view's scale... */
    private static final float INSET_ZOOM = 2f;
    /** ...and is left out when it cannot reach this many. */
    private static final float INSET_MIN_ZOOM = 1.4f;

    private static final int LOCATOR_WIDTH = 240;
    private static final int MARGIN = 14;
    /** The close-up's share of the frame width. */
    private static final float INSET_SHARE = 0.36f;

    private final int width;
    private final int height;
    private final int denseTicks;
    private final boolean insetEnabled;
    // Insertion-ordered rather than identity-hashed: ties and drawing order must not depend on identity hash codes.
    private final Map<Warship, Integer> destroyedAt = new LinkedHashMap<>();
    private final Map<Warship, float[]> lastSeen = new LinkedHashMap<>();
    private final Map<Warship, Float> lastHp = new LinkedHashMap<>();
    private final Map<Warship, Integer> lastHit = new LinkedHashMap<>();
    private DuelCamera camera;
    private DuelCamera.View view;
    private int lastFrameTick = -1;
    private int frames;
    private int contestEndedTick = -1;
    private int observedTick = -1;
    private boolean everContested;
    private int engagedUntil = -1;
    private Warship insetHull;
    private Warship focusA;
    private Warship focusB;
    private int insetSince;
    private int insetCorner;

    public WarshipFollowRecording(int width, int height) {
        this.width = width;
        this.height = height;
        this.denseTicks = Math.max(1, Integer.getInteger("snapshot.duelCamera.everyTicks", DEFAULT_DENSE_TICKS));
        this.insetEnabled = !"false".equalsIgnoreCase(System.getProperty("snapshot.duelCamera.inset", "true"));
    }

    /** Whether review GIFs of warship fights follow the fight ({@code -Dsnapshot.duelCamera}, default true). */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("snapshot.duelCamera", "true"));
    }

    /** The renderer a follow recording draws its main view with; it is pointed anew every frame. */
    public static BattleReviewFrameRenderer mainRenderer(Path modRoot, int width, int height) {
        return new BattleReviewFrameRenderer(modRoot, width, height, 0f, 0f, 1f).withImpactEffects()
                .withUnitShadows();
    }

    /** The renderer for the close-up inset (its caption band is cropped off). */
    public static BattleReviewFrameRenderer insetRenderer(Path modRoot, int width, int height) {
        int w = Math.round(width * INSET_SHARE);
        int h = Math.round(w * (height - BattleReviewFrameRenderer.HEADER_HEIGHT) / (float) width);
        return new BattleReviewFrameRenderer(modRoot, w, h + BattleReviewFrameRenderer.HEADER_HEIGHT, 0f, 0f, 1f)
                .withImpactEffects().withUnitShadows();
    }

    public int denseTicks() { return denseTicks; }

    /** Whether to take a frame on {@code tick}; called every tick, in order. */
    public boolean records(BattleSimulation sim, int tick, int sceneEveryTicks) {
        observe(sim, tick);
        boolean contested = contested(sim);
        if (contested) {
            everContested = true;
            contestEndedTick = -1;
        } else if (everContested && contestEndedTick < 0) {
            contestEndedTick = tick;
        }
        boolean shooting = !sim.warships().shells().isEmpty();
        for (Integer hit : lastHit.values()) shooting |= hit == tick;
        if (shooting) engagedUntil = tick + ENGAGED_HOLD_TICKS;
        boolean dense = (contested && tick <= engagedUntil) || (everContested && !contested && tick - contestEndedTick <= TAIL_TICKS);
        int since = lastFrameTick < 0 ? Integer.MAX_VALUE : tick - lastFrameTick;
        if (dense) return since >= interval(tick);
        if (everContested && !contested) return false;
        return since >= sceneEveryTicks;
    }

    /**
     * The dense interval for a frame at {@code tick}: {@link #denseTicks()}, doubled after every
     * {@link #FRAMES_PER_DOUBLING} frames (at most twice) so a long fight stays a GIF of reasonable size, but always
     * the dense interval while a kill is being framed, so the moment that decides the fight is never sampled coarsely.
     */
    private int interval(int tick) {
        for (Warship s : destroyedAt.keySet()) if (killHeld(s, tick)) return denseTicks;
        return denseTicks << Math.min(2, frames / FRAMES_PER_DOUBLING);
    }

    /** Moves the camera for a frame of {@code tick} and points {@code renderer} at it. */
    public void aim(BattleReviewFrameRenderer renderer, BattleSimulation sim, int tick) {
        observe(sim, tick);
        int mapW = sim.getGrid().getWidth();
        int mapH = sim.getGrid().getHeight();
        List<DuelCamera.Subject> subjects = subjects(tick, mapW, mapH);
        if (camera == null) {
            float longest = 0f;
            for (Warship s : sim.warships().ships()) longest = Math.max(longest, Math.max(s.lengthCells, s.widthCells));
            camera = new DuelCamera(width, height, mapW, mapH,
                    Math.max(MIN_ACROSS_CELLS, MIN_ACROSS_HULLS * longest));
        }
        float seconds = lastFrameTick < 0 ? 0f : (tick - lastFrameTick) * TICK_SECONDS;
        lastFrameTick = tick;
        frames++;
        view = camera.update(subjects, seconds);
        renderer.refocus(view.centerX(), view.centerY(), view.cellsAcross());
        if (TRACE) {
            System.out.printf(Locale.ROOT, "duel-camera tick=%d x=%.2f y=%.2f across=%.1f cellPx=%.0f subjects=%d%n", tick,
                    view.centerX(), view.centerY(), view.cellsAcross(), Math.floor(width / view.cellsAcross()),
                    subjects.size());
        }
        chooseInset(tick);
    }

    /**
     * Draws the close-up (when a hull has just been hit) and the locator over a frame {@link #aim} pointed the main
     * renderer for. {@code inset} may be null to draw no close-up.
     */
    public void decorate(BufferedImage image, BattleSimulation sim, int tick, BattleReviewFrameRenderer inset,
                         ReviewImpactFx fx) throws IOException {
        if (insetEnabled && inset != null && insetHull != null) {
            float[] at = lastSeen.get(insetHull);
            if (at != null) {
                float length = Math.max(insetHull.lengthCells, insetHull.widthCells);
                // About twice the main view's scale, but never so close that the hull is cropped; a hull the main
                // view already draws that large gets no close-up, since it would show nothing the frame does not.
                float twice = view.cellsAcross() * inset.width() / (float) width / INSET_ZOOM;
                float across = Math.max(Math.max(INSET_MIN_ACROSS_CELLS, INSET_TIGHTEST_HULLS * length),
                        Math.min(INSET_ACROSS_HULLS * length, twice));
                if (across <= twice * INSET_ZOOM / INSET_MIN_ZOOM) {
                    inset.refocus(at[0], at[1], across);
                    BufferedImage close = inset.render(sim, "", ReviewAnnotations.NONE, fx);
                    paintInset(image, close, insetLabel(tick));
                }
            }
        }
        paintLocator(image, sim);
    }

    /** Whether two hulls of hostile sides are over the map at once. */
    static boolean contested(BattleSimulation sim) {
        List<Warship> present = new ArrayList<>();
        for (Warship s : sim.warships().ships()) if (present(s)) present.add(s);
        for (int i = 0; i < present.size(); i++) {
            for (int j = i + 1; j < present.size(); j++) {
                if (present.get(i).faction.hostileTo(present.get(j).faction)) return true;
            }
        }
        return false;
    }

    private static boolean present(Warship s) {
        return !s.destroyed() && !s.departed() && !s.offMapInbound();
    }

    /** Once per tick: kills, structure losses and where each hull was last drawn. */
    private void observe(BattleSimulation sim, int tick) {
        if (tick == observedTick) return;
        observedTick = tick;
        for (Warship s : sim.warships().ships()) {
            if (s.destroyed()) destroyedAt.putIfAbsent(s, tick);
            seen(s, s.bodyScale(), tick);
        }
        // A destroyed hull leaves the ship list for the wreck list, and falls and skids there as its wreck.
        for (WarshipWreck wreck : sim.warships().wrecks()) {
            destroyedAt.putIfAbsent(wreck.ship, tick);
            seen(wreck.ship, wreck.bodyScale(), tick);
        }
    }

    private void seen(Warship s, float bodyScale, int tick) {
        float hp = Math.max(0f, s.hitPoints());
        Float before = lastHp.put(s, hp);
        if (before != null && hp < before) lastHit.put(s, tick);
        if (!s.departed() && !s.offMapInbound()) {
            float r = 0.5f * Math.max(s.lengthCells, s.widthCells) * Math.max(0.2f, bodyScale);
            lastSeen.put(s, new float[]{s.centerX(), s.centerY(), r});
        }
    }

    private boolean killHeld(Warship s, int tick) {
        Integer at = destroyedAt.get(s);
        return at != null && tick - at <= KILL_HOLD_TICKS;
    }

    private List<DuelCamera.Subject> subjects(int tick, int mapW, int mapH) {
        List<Warship> live = new ArrayList<>();
        for (Warship s : lastSeen.keySet()) if (present(s)) live.add(s);
        choosePair(live);
        List<DuelCamera.Subject> out = new ArrayList<>();
        float reach = focusA == null ? 0f : CLUSTER_SHARE * distance(focusA, focusB);
        for (Map.Entry<Warship, float[]> e : lastSeen.entrySet()) {
            Warship s = e.getKey();
            boolean framed = killHeld(s, tick)
                    || (present(s) && (focusA == null || s == focusA || s == focusB
                    || Math.min(distance(s, focusA), distance(s, focusB)) <= reach));
            if (!framed) continue;
            float[] p = e.getValue();
            out.add(new DuelCamera.Subject(Math.max(0f, Math.min(mapW, p[0])), Math.max(0f, Math.min(mapH, p[1])), p[2]));
        }
        return out;
    }

    /**
     * With more than two hulls over the map, the camera frames one engagement rather than all of them: the closest
     * hostile pair, and every hull near either of them. The pair is kept while it stays within
     * {@link #PAIR_SWITCH_SHARE} of the closest, so the shot does not cut back and forth between two engagements at
     * similar range. Hulls left out are still on the locator.
     */
    private void choosePair(List<Warship> live) {
        if (live.size() <= 2) {
            focusA = focusB = null;
            return;
        }
        Warship bestA = null, bestB = null;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < live.size(); i++) {
            for (int j = i + 1; j < live.size(); j++) {
                Warship a = live.get(i), b = live.get(j);
                if (!a.faction.hostileTo(b.faction)) continue;
                float d = distance(a, b);
                if (d < best) {
                    best = d;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        boolean keep = focusA != null && live.contains(focusA) && live.contains(focusB)
                && distance(focusA, focusB) <= PAIR_SWITCH_SHARE * best;
        if (!keep) {
            focusA = bestA;
            focusB = bestB;
        }
    }

    private float distance(Warship a, Warship b) {
        float[] p = lastSeen.get(a), q = lastSeen.get(b);
        return (float) Math.hypot(p[0] - q[0], p[1] - q[1]);
    }

    /** The hull the close-up shows: the latest kill while it is held, otherwise the latest hull hit, held a while. */
    private void chooseInset(int tick) {
        Warship candidate = null;
        int best = Integer.MIN_VALUE;
        for (Map.Entry<Warship, Integer> e : destroyedAt.entrySet()) {
            if (killHeld(e.getKey(), tick) && e.getValue() > best && lastSeen.containsKey(e.getKey())) {
                best = e.getValue();
                candidate = e.getKey();
            }
        }
        if (candidate == null) {
            for (Map.Entry<Warship, Integer> e : lastHit.entrySet()) {
                if (tick - e.getValue() <= HIT_HOLD_TICKS && e.getValue() > best && present(e.getKey())) {
                    best = e.getValue();
                    candidate = e.getKey();
                }
            }
        }
        boolean currentLive = insetHull != null
                && (killHeld(insetHull, tick)
                || (present(insetHull) && tick - lastHit.getOrDefault(insetHull, Integer.MIN_VALUE / 2) <= HIT_HOLD_TICKS));
        boolean kill = candidate != null && destroyedAt.containsKey(candidate);
        if (candidate == insetHull) return;
        // A live close-up is held for its minimum (a kill cuts in at once) and until another hull is hit.
        if (currentLive && !kill && (candidate == null || tick - insetSince < INSET_MIN_TICKS)) return;
        insetHull = candidate;
        insetSince = tick;
    }

    private String insetLabel(int tick) {
        Warship s = insetHull;
        if (s.destroyed()) return "KILL / " + s.hullId;
        return String.format(Locale.ROOT, "HIT / %s / %d%% hull / %d mounts out", s.hullId,
                Math.round(100f * Math.max(0f, s.hullFraction())), s.mountsKnockedOut());
    }

    /** The close-up's corner: the first, from the lower left clockwise, that no framed hull is drawn in. */
    private Rectangle insetRect(int w, int h, int imageW, int imageH) {
        Rectangle[] corners = {
                new Rectangle(MARGIN, imageH - MARGIN - h, w, h),
                new Rectangle(MARGIN, BattleReviewFrameRenderer.HEADER_HEIGHT + MARGIN, w, h),
                new Rectangle(imageW - MARGIN - w, BattleReviewFrameRenderer.HEADER_HEIGHT + MARGIN, w, h)};
        if (!occupied(corners[insetCorner], imageW, imageH)) return corners[insetCorner];
        for (int i = 0; i < corners.length; i++) {
            if (!occupied(corners[i], imageW, imageH)) {
                insetCorner = i;
                return corners[i];
            }
        }
        return corners[insetCorner];
    }

    private boolean occupied(Rectangle r, int imageW, int imageH) {
        if (view == null) return false;
        float cellPx = (float) Math.floor(imageW / view.cellsAcross());
        float mapPxH = imageH - BattleReviewFrameRenderer.HEADER_HEIGHT;
        for (Map.Entry<Warship, float[]> e : lastSeen.entrySet()) {
            if (!(present(e.getKey()) || killHeld(e.getKey(), observedTick))) continue;
            float[] p = e.getValue();
            float sx = imageW * 0.5f + (p[0] - view.centerX()) * cellPx;
            float sy = BattleReviewFrameRenderer.HEADER_HEIGHT + mapPxH * 0.5f - (p[1] - view.centerY()) * cellPx;
            float rr = p[2] * cellPx;
            if (r.intersects(sx - rr, sy - rr, 2 * rr, 2 * rr)) return true;
        }
        return false;
    }

    private void paintInset(BufferedImage image, BufferedImage close, String label) {
        int header = BattleReviewFrameRenderer.HEADER_HEIGHT;
        int w = close.getWidth();
        int h = close.getHeight() - header;
        Rectangle r = insetRect(w, h, image.getWidth(), image.getHeight());
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(close.getSubimage(0, header, w, h), r.x, r.y, null);
        g.setColor(new Color(0, 0, 0, 200));
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r.x - 2, r.y - 2, w + 3, h + 3);
        g.setColor(new Color(235, 235, 235, 230));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(r.x - 1, r.y - 1, w + 1, h + 1);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        int textW = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(r.x, r.y, textW + 14, 21);
        g.setColor(label.startsWith("KILL") ? new Color(255, 140, 60) : Color.WHITE);
        g.drawString(label, r.x + 7, r.y + 15);
        g.dispose();
    }

    /**
     * A small map in the lower right corner: the ground's outline, every hull over it as a dot in its side's colour
     * (orange once destroyed), and the main view's rectangle, so a reader always knows where on the map the fight is.
     */
    private void paintLocator(BufferedImage image, BattleSimulation sim) {
        if (view == null) return;
        int mapW = sim.getGrid().getWidth();
        int mapH = sim.getGrid().getHeight();
        float scale = (float) LOCATOR_WIDTH / mapW;
        int boxW = LOCATOR_WIDTH;
        int boxH = Math.max(1, Math.round(mapH * scale));
        if (boxH > image.getHeight() / 4) {
            scale = (float) (image.getHeight() / 4) / mapH;
            boxH = Math.round(mapH * scale);
            boxW = Math.round(mapW * scale);
        }
        int left = image.getWidth() - MARGIN - boxW;
        int top = image.getHeight() - MARGIN - boxH;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(left - 4, top - 4, boxW + 8, boxH + 8);
        g.setColor(new Color(150, 160, 150, 200));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(left, top, boxW, boxH);
        float halfX = view.cellsAcross() * 0.5f;
        float halfY = view.cellsDown(camera.aspect()) * 0.5f;
        int vx0 = left + Math.round(Math.max(0f, view.centerX() - halfX) * scale);
        int vx1 = left + Math.round(Math.min(mapW, view.centerX() + halfX) * scale);
        int vy0 = top + boxH - Math.round(Math.min(mapH, view.centerY() + halfY) * scale);
        int vy1 = top + boxH - Math.round(Math.max(0f, view.centerY() - halfY) * scale);
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(vx0, vy0, Math.max(1, vx1 - vx0), Math.max(1, vy1 - vy0));
        for (Map.Entry<Warship, float[]> e : lastSeen.entrySet()) {
            Warship s = e.getKey();
            if (!(present(s) || killHeld(s, observedTick))) continue;
            float[] p = e.getValue();
            int px = left + Math.round(Math.max(0f, Math.min(mapW, p[0])) * scale);
            int py = top + boxH - Math.round(Math.max(0f, Math.min(mapH, p[1])) * scale);
            g.setColor(s.destroyed() ? new Color(255, 120, 40, 220) : sideColour(s.faction));
            g.fillOval(px - 3, py - 3, 7, 7);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.setColor(new Color(230, 230, 230, 220));
        g.drawString("MAP", left + 4, top + 13);
        g.dispose();
    }

    private static Color sideColour(Faction faction) {
        return switch (faction) {
            case MARINE -> new Color(68, 214, 255);
            case ALLY -> new Color(130, 240, 120);
            case DEFENDER -> new Color(255, 83, 83);
            default -> new Color(255, 218, 73);
        };
    }
}
