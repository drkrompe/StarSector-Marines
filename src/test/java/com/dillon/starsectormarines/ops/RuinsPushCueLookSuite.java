package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.comms.BattleCommsFeed;
import com.dillon.starsectormarines.battle.ui.comms.RuinsPressureCommsPresenter;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The cue a stir push gives ({@code ruins-pressure.md}, Cues) through the look preset with the HUD: the comms line in
 * the occupant's voice as the push comes up. A scavenger crew's hunt, the same crew going round a company that has
 * dug in, and derelict security's hunt, each with marines stood at a layer's stand as the controlled stand-in for the
 * company, and the stir lifted a step by hand. The plate is drawn to {@code BattleCommsPanel}'s geometry and colours
 * (the panel draws in GL only). 1920x1080 at 100%. Look evidence, opt-in; it judges nothing.
 */
public final class RuinsPushCueLookSuite implements SnapshotSuite {
    static final String SCAVENGERS = "src/test/resources/battle-fixtures/ruins-scavengers-widespread-v1.json";
    /** Deep enough that a layer inside lies behind the company's deepest stand. */
    static final String SCAVENGERS_DEEP = "src/test/resources/battle-fixtures/ruins-scavengers-extensive-v1.json";
    static final String DERELICT = "src/test/resources/battle-fixtures/ruins-derelict-extensive-v1.json";

    // BattleCommsPanel's plate.
    private static final int PANEL_W = 560;
    private static final int PANEL_H = 76;
    private static final int PAD = 10;
    private static final int TOP = 104;
    private static final Color PANEL_BG = new Color(0x10, 0x14, 0x1E, 0xEC);
    private static final Color TEXT = new Color(0xE6, 0xEC, 0xF2);
    private static final Color WARNING = new Color(0xFF, 0xC0, 0x40);
    private static final Color DANGER = new Color(0xF0, 0x58, 0x48);

    @Override public String id() { return "ruins-push-cue-look"; }

    @Override public String label() { return "Ruins pressure: the comms cue of a stir push"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            RuinsRailLookSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA, Fonts.ORBITRON_20);
            try (BattleSimulation sim = delve(context, SCAVENGERS)) {
                out.add(push(context, sim, "01-crew-hunt", Aim.HUNT));
            }
            try (BattleSimulation sim = delve(context, SCAVENGERS_DEEP)) {
                // Held still past the stillness window, before any push: the first goes round behind.
                // The company told to hold where it stands, the stand-in for a player who digs in.
                for (int t = 0; t < 10; t++) {
                    holdTheCompany(sim);
                    RuinsRailLookSuite.advance(sim, 5f);
                }
                out.add(push(context, sim, "02-crew-way-back", Aim.WAY_BACK));
            }
            try (BattleSimulation sim = delve(context, DERELICT)) {
                out.add(push(context, sim, "03-security-hunt", Aim.HUNT));
            }
            try (BattleSimulation sim = delve(context, SCAVENGERS_DEEP)) {
                out.addAll(wakePoint(context, sim));
            }
        }
        return out;
    }

    /** The fixture's battle with the entry taken and marines at the stand of the deepest layer. */
    static BattleSimulation delve(SnapshotContext context, String fixturePath) throws Exception {
        JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(fixturePath)));
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        RuinsVaultObjective vault = RuinsPullOut.vault(sim);
        if (vault == null || sim.ruinsPressure() == null || sim.ruinsPressure().fielded() == null) {
            throw new IllegalStateException("no occupied Ruins battle with its pushes fielded");
        }
        RuinsRailLookSuite.advance(sim, 8f);
        // Each entrance in turn until one is held: an apron the occupant contests never counts.
        for (int e = 0; e < vault.liftPoints().size() && vault.entry() < 0; e++) {
            if (!vault.liftPoints().get(e).entrance()) continue;
            int ex = vault.liftPointCellX(e), ey = vault.liftPointCellY(e);
            for (int i = 0; i < 6; i++) {
                sim.spawn(new EntitySpec("Pointman " + (e + 1) + "-" + (i + 1), Faction.MARINE, UnitType.MARINE,
                        ex + i % 3 - 1, ey + i / 3));
            }
            for (int i = 0; i < 80 && vault.entry() < 0; i++) RuinsRailLookSuite.advance(sim, 0.25f);
        }
        if (vault.entry() < 0) throw new IllegalStateException("the apron was not taken for the shot");
        List<RuinsWorks.Site> junctions = sim.getRuinsWorks().junctions();
        RuinsWorks.Site stand = junctions.get(junctions.size() - 1);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("Stand " + (i + 1), Faction.MARINE, UnitType.MARINE,
                    stand.x() + i % 2, stand.y() + i / 2));
        }
        RuinsRailLookSuite.advance(sim, 1f);
        return sim;
    }

    /** Lifts the stir a step, lets the push come up and walk a little, and shoots it with its line. */
    private static SnapshotArtifact push(SnapshotContext context, BattleSimulation sim, String name, Aim expected)
            throws Exception {
        RuinsPressure pressure = sim.ruinsPressure();
        int before = pressure.pushes().size();
        int squadsBefore = pressure.fielded().squads().size();
        pressure.loud(RuinsPressure.STEP);
        RuinsRailLookSuite.advance(sim, 0.5f);
        if (pressure.pushes().size() == before) throw new IllegalStateException("no push for " + name);
        Push push = pressure.pushes().get(pressure.pushes().size() - 1);
        if (push.aim() != expected) throw new IllegalStateException(name + " sent a " + push.aim()
                + " push from layer " + (push.layer() + 1) + ", dug in " + pressure.dugIn());
        RuinsRailLookSuite.advance(sim, 6f);
        List<Integer> squads = pressure.fielded().squads();
        float[] company = middle(sim, List.of(), Faction.MARINE);
        float[] raised = middle(sim, squads.subList(squadsBefore, squads.size()), null);
        if (company == null) throw new IllegalStateException("the company is gone for " + name);
        float cx = raised == null ? company[0] : (company[0] + raised[0]) / 2f;
        float cy = raised == null ? company[1] : (company[1] + raised[1]) / 2f;
        float span = raised == null ? 90f : Math.max(90f, 1.6f * Math.max(Math.abs(company[0] - raised[0]),
                Math.abs(company[1] - raised[1]) * 16f / 9f));
        SnapshotArtifact shot = RuinsRailLookSuite.shot(context, sim, name, cx, cy, span);
        plate(context, shot.frames().get(0), RuinsPressureCommsPresenter.notice(pressure.fielded().voice(), push));
        return shot;
    }

    /**
     * A wake point: a hunt coming up out of the crawlway of the layer it is sent to, then the same crawlway after a
     * company charge has gone off over it, marked collapsed.
     */
    private static List<SnapshotArtifact> wakePoint(SnapshotContext context, BattleSimulation sim) throws Exception {
        RuinsPressure pressure = sim.ruinsPressure();
        pressure.loud(RuinsPressure.STEP);
        RuinsRailLookSuite.advance(sim, 0.5f);
        Push push = pressure.pushes().get(pressure.pushes().size() - 1);
        RuinsWorks.WakePoint wake = sim.getRuinsWorks().wakePoint(push.layer());
        if (wake == null) throw new IllegalStateException("layer " + (push.layer() + 1) + " has no wake point");
        RuinsRailLookSuite.advance(sim, 2f);
        SnapshotArtifact rising = RuinsRailLookSuite.shot(context, sim, "04-crew-wake-point", wake.x(), wake.y(), 60f);
        plate(context, rising.frames().get(0), RuinsPressureCommsPresenter.notice(pressure.fielded().voice(), push));
        long carrier = 0L;
        for (int i = 0, m = sim.liveUnitCount(); i < m && carrier == 0L; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && sim.movement().has(unit)) carrier = unit;
        }
        SatchelChargeSpec spec = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID)
                .satchelChargeSpec();
        if (!sim.satchelCharges().plantAtCell(carrier, wake.x(), wake.y(), Faction.MARINE, spec)) {
            throw new IllegalStateException("no charge set on the wake point");
        }
        RuinsRailLookSuite.advance(sim, spec.fuseSeconds() + 1.5f);
        if (!sim.getRuinsWorks().wakeCollapsed(push.layer())) throw new IllegalStateException("the charge left it");
        SnapshotArtifact shut = RuinsRailLookSuite.shot(context, sim, "05-crew-wake-collapsed", wake.x(), wake.y(), 60f);
        return List.of(rising, shut);
    }

    /** Every marine squad holds the ground it stands on. */
    static void holdTheCompany(BattleSimulation sim) {
        Set<Integer> held = new TreeSet<>();
        for (int i = 0, m = sim.liveUnitCount(); i < m; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.movement().has(unit)) continue;
            if (!sim.squad().hasSquad(unit)) continue;
            int squad = sim.squad().squadId(unit);
            if (squad < 0 || !held.add(squad)) continue;
            int x = (int) Math.floor(sim.world().x(unit)), y = (int) Math.floor(sim.world().y(unit));
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, x, y, 3), CommandAuthority.SCRIPTED,
                    "ruins-push-cue-look", "the company digs in for the shot");
        }
    }

    /** The middle of some squads' members, or with {@code side} of that side's movers; null with none. */
    static float[] middle(BattleSimulation sim, List<Integer> squads, Faction side) {
        float sx = 0f, sy = 0f;
        int n = 0;
        if (side == null) {
            for (int squad : squads) {
                for (int i = 0, m = sim.squadMemberCount(squad); i < m; i++) {
                    long unit = sim.squadMemberAt(squad, i);
                    sx += sim.world().x(unit);
                    sy += sim.world().y(unit);
                    n++;
                }
            }
        } else {
            for (int i = 0, m = sim.liveUnitCount(); i < m; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != side || !sim.movement().has(unit)) continue;
                sx += sim.world().x(unit);
                sy += sim.world().y(unit);
                n++;
            }
        }
        return n == 0 ? null : new float[]{sx / n, sy / n};
    }

    /** The comms plate at the top middle, as {@code BattleCommsPanel} draws it. */
    private static void plate(SnapshotContext context, BufferedImage frame, BattleCommsFeed.Notice notice)
            throws Exception {
        BitmapFont font = Fonts.ORBITRON_20;
        List<String> lines = font.wrapLines(notice.body(), PANEL_W - PAD * 2f - 4f);
        int height = Math.max(PANEL_H, 40 + lines.size() * font.getLineHeight());
        int x = (frame.getWidth() - PANEL_W) / 2;
        Color accent = notice.tone() == BattleCommsFeed.Tone.DANGER ? DANGER : WARNING;
        Graphics2D g = frame.createGraphics();
        g.setColor(PANEL_BG);
        g.fillRect(x, TOP, PANEL_W, height);
        g.setColor(accent);
        g.fillRect(x, TOP, 4, height);
        g.drawRect(x, TOP, PANEL_W - 1, height - 1);
        BufferedImage[] pages = pages(context, font);
        text(g, font, pages, notice.heading(), x + PAD + 4, TOP + 8, accent);
        int y = TOP + 32;
        for (String line : lines) {
            text(g, font, pages, line, x + PAD + 4, y, TEXT);
            y += font.getLineHeight();
        }
        g.dispose();
    }

    private static BufferedImage[] pages(SnapshotContext context, BitmapFont font) throws Exception {
        BufferedImage[] pages = new BufferedImage[font.pageCount()];
        for (int i = 0; i < pages.length; i++) {
            String page = font.pagePath(i);
            if (page == null) continue;
            Path path = context.modRoot().resolve(page);
            if (!Files.exists(path)) path = context.starsectorCore().resolve(page);
            pages[i] = ImageIO.read(path.toFile());
        }
        return pages;
    }

    /** A line of the bitmap font, its glyphs' coverage tinted, the line's top at {@code top}. */
    private static void text(Graphics2D g, BitmapFont font, BufferedImage[] pages, String s, int left, int top,
                             Color color) {
        int x = left;
        for (int i = 0; i < s.length(); i++) {
            BitmapFont.Glyph glyph = font.glyph(s.charAt(i));
            if (glyph == null) continue;
            BufferedImage page = glyph.page < pages.length ? pages[glyph.page] : null;
            if (page != null && glyph.w > 0 && glyph.h > 0) {
                BufferedImage tinted = new BufferedImage(glyph.w, glyph.h, BufferedImage.TYPE_INT_ARGB);
                for (int py = 0; py < glyph.h; py++) {
                    for (int px = 0; px < glyph.w; px++) {
                        int argb = page.getRGB(glyph.x + px, glyph.y + py);
                        int coverage = (argb >>> 24) * ((argb >> 16) & 0xff) / 255;
                        tinted.setRGB(px, py, (coverage << 24) | (color.getRGB() & 0xffffff));
                    }
                }
                g.drawImage(tinted, x + glyph.xoffset, top + glyph.yoffset, null);
            }
            x += glyph.xadvance;
        }
    }
}
