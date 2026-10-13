package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.control.ShipCommandService.Notice;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.BiPredicate;

/** One next-click owner for remote ship travel and drone construction. Never pilots a hull. */
public final class ShipOrderTargetingPanel implements HudPanel {
    private final BattleUiContext ctx;
    private final BiPredicate<Float, Float> chrome;
    private Warship ship;
    private BuildOption option;
    private int mouseX, mouseY;
    private boolean pointerKnown;
    private Notice lastNotice = Notice.NONE;

    public ShipOrderTargetingPanel(BattleUiContext ctx, BiPredicate<Float, Float> chrome) {
        this.ctx = ctx;
        this.chrome = chrome;
    }

    public Warship ship() { return ship; }
    public BuildOption option() { return option; }
    public Notice notice() { return lastNotice; }
    public void pointer(float x, float y, boolean known) {
        mouseX = Math.round(x); mouseY = Math.round(y); pointerKnown = known;
    }
    public boolean active() { return ship != null; }
    public void cancel() { ship = null; option = null; lastNotice = Notice.NONE; }

    public void toggle(Warship target, BuildOption build) {
        if (ship == target && option == build) { cancel(); return; }
        cancel();
        BattleSimulation sim = ctx.getSim();
        if (sim == null || sim.isComplete() || sim.directControl().active()
                || !sim.warships().ships().contains(target)
                || ShipCommandService.eligibility(target, tender(sim, target), Faction.MARINE) != Notice.NONE) return;
        ship = target;
        option = build;
    }

    public static Tender tender(BattleSimulation sim, Warship ship) {
        if (sim != null) for (Tender tender : sim.tenders().tenders()) if (tender.ship() == ship) return tender;
        return null;
    }

    @Override public boolean isVisible() { return active(); }
    @Override public void update(float dt) {
        if (!active()) return;
        BattleSimulation sim = ctx.getSim();
        if (sim == null || sim.isComplete() || sim.directControl().active()
                || ctx.getSelection().getSelectedWarship() != ship
                || !sim.warships().ships().contains(ship)
                || ShipCommandService.eligibility(ship, tender(sim, ship), Faction.MARINE) != Notice.NONE) cancel();
    }

    /** Shared with controlled input evidence; the tick repeats all authority checks. */
    public Notice preview(float x, float y) {
        BattleSimulation sim = ctx.getSim();
        if (sim == null || !active()) return Notice.UNAVAILABLE;
        Tender tender = tender(sim, ship);
        Notice place = ShipCommandService.placement(ship, tender, Faction.MARINE, x, y,
                sim.getGrid(), sim.getFogOfWar()::isCellRevealed);
        if (place != Notice.NONE || option == null) return place;
        if (tender == null) return Notice.UNAVAILABLE;
        if (TenderPilot.placement(tender, x, y, sim.getGrid(), sim.getFogOfWar()::isCellRevealed)
                != TenderPilot.Placement.OPEN) return Notice.BLOCKED;
        if (TenderPilot.liveSites(tender) >= TenderPilot.MAX_SITES) return Notice.FULL;
        return tender.metered() && tender.stock() < option.cost() ? Notice.NO_CHARGES : Notice.NONE;
    }

    public boolean commitAt(float x, float y) {
        update(0f);
        if (!active()) return false;
        float cx = (float) Math.floor(x) + .5f, cy = (float) Math.floor(y) + .5f;
        lastNotice = preview(cx, cy);
        if (lastNotice != Notice.NONE) return false;
        BattleSimulation sim = ctx.getSim();
        if (option == null) sim.shipCommands().requestMove(ship, cx, cy);
        else sim.shipCommands().requestBuild(tender(sim, ship), option, cx, cy);
        cancel();
        return true;
    }

    @Override public void render(float alphaMult) {
        BattleCamera camera = ctx.getCamera();
        if (!active() || camera == null || !pointerKnown || !camera.containsScreen(mouseX, mouseY)
                || chrome.test((float) mouseX, (float) mouseY)) return;
        float x = (float) Math.floor(camera.screenToCellX(mouseX)) + .5f;
        float y = (float) Math.floor(camera.screenToCellY(mouseY)) + .5f;
        if (!ctx.getSim().getGrid().inBounds((int) x, (int) y)) return;
        String label = option == null ? Strings.get("battleShipMove") : option.label();
        var marker = BattlefieldMarkerPresentation.target(label, preview(x, y) == Notice.NONE, 1f);
        CommandPowerTargetingPanel.paintTargetingMarker(camera, marker,
                camera.cellToScreenX(x), camera.cellToScreenY(y), alphaMult);
    }

    @Override public void handleInput(List<InputEventAPI> events) {
        update(0f);
        if (!active() || events == null || ctx.getCamera() == null) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent()) { mouseX = event.getX(); mouseY = event.getY(); pointerKnown = true; }
            if (event.isRMBDownEvent() || event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                cancel(); event.consume(); return;
            }
            if (event.isLMBDownEvent() && ctx.getCamera().containsScreen(event.getX(), event.getY())
                    && !chrome.test((float) event.getX(), (float) event.getY())) {
                commitAt(ctx.getCamera().screenToCellX(event.getX()), ctx.getCamera().screenToCellY(event.getY()));
                event.consume();
                if (!active()) return;
            }
        }
    }
}
