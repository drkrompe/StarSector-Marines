package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;
import java.util.Locale;

/** Nonblocking field coaching; only the portrait consumes input, never the battlefield. */
final class BattleLessonOverlay {
    private final MarineRoster roster;
    private final UiElement tray = new UiElement("lesson-power-tray").style("position: absolute; pointer-events: none;");
    private final UiDocument document;
    private final PreparationGuide guide;
    private PositionAPI position;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private boolean available;
    private boolean called;

    BattleLessonOverlay(MarineRoster roster) {
        this.roster = roster;
        document = document(tray);
        guide = PreparationGuide.install(document, this::lesson, () -> {});
        guide.combatInput();
    }

    static UiDocument document(UiElement tray) {
        // This full-screen host sits above the battle: only the guide may paint a backdrop.
        return new UiDocument(new UiElement("field-lesson")
                .style("background-color: transparent;").child(tray)).theme(MarineOpsThemes.standard());
    }

    void attach(PositionAPI position) {
        this.position = position;
        viewport = MarineOpsUiViewport.from(position);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    void update(BattleSimulation sim) {
        available = sim != null && !roster.campaignLessons().fieldGuidanceReviewed();
        if (available) {
            var powers = sim.getCommandPowerService();
            var support = powers.getAvailablePowers().stream().filter(p -> MechSupport.ID.equals(p.id)).findFirst().orElse(null);
            available = support != null && !sim.isComplete();
            called = support != null && support.maxCharges > 0 && powers.getChargesRemaining(support.id) < support.maxCharges;
            boolean landed = false;
            if (called && !roster.campaignLessons().supportDelivered()) {
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id).isMech()) { landed = true; break; }
                }
            }
            roster.campaignLessons().observeDelivery(true, called, landed);
            UiViewport powerView = BattlePowerOverlay.viewport(position, powers.getAvailablePowers().size(), true);
            tray.style(String.format(Locale.ROOT, "left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx;",
                    viewport.documentX(powerView.screenX()), viewport.documentY(powerView.screenY() + powerView.height()),
                    powerView.width() / viewport.documentScale(), powerView.height() / viewport.documentScale()));
        }
        document.advance(0f);
        guide.update();
    }

    PreparationGuide.Lesson lesson() {
        if (!available) return null;
        boolean landed = roster.campaignLessons().supportDelivered();
        return CampaignLessons.lesson(landed ? 3 : called ? 2 : 1, "lessonFieldTitle",
                landed ? "lessonFieldLanded" : called ? "lessonFieldInbound" : "lessonFieldCall",
                List.of("lesson-power-tray"), landed ? "lessonUnderstood" : null,
                landed ? roster.campaignLessons()::reviewFieldGuidance : null);
    }

    void render(float alpha) { if (guide.visible()) document.render(viewport, alpha); }
    boolean blocksWorldPointer(float x, float y) {
        return viewport != null && guide.calloutBounds().contains(viewport.documentX(x), viewport.documentY(y));
    }
    void processInput(List<InputEventAPI> events) {
        if (!guide.visible() || input == null) return;
        input.process(events.stream().filter(e -> !e.isConsumed()
                && (e.isMouseMoveEvent() || e.isMouseScrollEvent() || e.isLMBDownEvent() || e.isLMBUpEvent())
                && blocksWorldPointer(e.getX(), e.getY())).toList());
    }
    void detach() { document.deactivateInput(); input = null; }
}
