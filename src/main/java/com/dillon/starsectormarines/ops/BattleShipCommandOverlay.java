package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.panel.TaskForceStatusPanel;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Retained ship selection strip and contextual commands, independent of piloting. */
final class BattleShipCommandOverlay {
    static final String COMPONENT_PATH = "data/ui/components/battle/battle-ship-command-overlay.mlx";
    static final String ACTION_COMPONENT_PATH = "data/ui/components/battle/battle-ship-command-actions.mlx";
    static final float HERO_HEIGHT = 96f;
    static final float ACTION_HEIGHT = 118f;
    private final Reactor reactor = new Reactor();
    private final BattleShipCommandOverlayModel model = new BattleShipCommandOverlayModel(reactor);
    private final Supplier<BattleShipCommandOverlayModel.State> source;
    private MarkupInstance heroes, actions;
    private UiDocument heroDocument, actionDocument;
    private UiViewport heroViewport, actionViewport;
    private StarsectorUiInputAdapter heroInput, actionInput;
    private PositionAPI position;
    private float bottomOffset;
    private float heroBottomInset;
    private BattleShipCommandOverlayModel.State state = BattleShipCommandOverlayModel.State.EMPTY;

    BattleShipCommandOverlay(Supplier<BattleShipCommandOverlayModel.State> source) { this.source = source; }

    void attach(PositionAPI position) {
        this.position = position;
        if (heroes == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH, ACTION_COMPONENT_PATH));
            loader.reload();
            heroes = loader.build(reactor, "battle-ship-command-overlay", model.props());
            actions = loader.build(reactor, "battle-ship-command-actions", model.props());
            heroDocument = BattleDirectControlOverlay.document(heroes);
            actionDocument = BattleDirectControlOverlay.document(actions);
        }
        update(0f);
    }

    void heroBottomInset(float height) { heroBottomInset = Math.max(0f, height); }

    static float heroBottomInset(PositionAPI position, Selection selection, boolean selectedMech) {
        if (position == null) return 132f;
        if (selection != null && (selection.hasSquadSelection() || selection.hasVehicleSelection())) {
            UiViewport plate = BattleDirectControlOverlay.entryViewport(position, selectedMech);
            return plate.screenY() + plate.height() - position.getY();
        }
        return BattleLayout.PAD + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP + TaskForceStatusPanel.PANEL_H;
    }

    void bottomOffset(float height) { bottomOffset = Math.max(0f, height); }

    void update(float dt) {
        state = model.update(source.get());
        if (heroes == null || position == null) return;
        heroes.flush();
        actions.flush();
        String hovered = null;
        for (var card : heroes.requireElement("ship-heroes").children()) if (card.hovered()) hovered = card.id();
        for (var card : actions.requireElement("ship-actions").children()) if (card.hovered()) hovered = card.id();
        model.hover(hovered);
        actions.flush();
        float scale = MarineOpsUiViewport.from(position).documentScale();
        UiViewport nextHero = new UiViewport(position.getX() + 12f,
                position.getY() + 12f + heroBottomInset,
                Math.min(heroWidth(state.ships().size()) * scale, position.getWidth() - 24f), HERO_HEIGHT * scale, scale);
        float width = Math.min(actionWidth(state.actions().size()) * scale, position.getWidth() - 24f);
        UiViewport nextAction = new UiViewport(position.getX() + (position.getWidth() - width) / 2f,
                position.getY() + 12f + bottomOffset, width, ACTION_HEIGHT * scale, scale);
        if (!nextHero.equals(heroViewport)) {
            heroDocument.deactivateInput(); heroViewport = nextHero;
            heroDocument.layout(nextHero.documentWidth(), nextHero.documentHeight());
            heroInput = new StarsectorUiInputAdapter(heroDocument, nextHero);
        }
        if (!nextAction.equals(actionViewport)) {
            actionDocument.deactivateInput(); actionViewport = nextAction;
            actionDocument.layout(nextAction.documentWidth(), nextAction.documentHeight());
            actionInput = new StarsectorUiInputAdapter(actionDocument, nextAction);
        }
        if (!state.visible()) heroDocument.deactivateInput();
        if (!state.selected()) actionDocument.deactivateInput();
        heroDocument.advance(dt); actionDocument.advance(dt);
    }

    static float heroWidth(int count) { return 12f + Math.max(1, count) * 112f; }
    static float actionWidth(int count) { return Math.max(500f, 12f + Math.max(1, count) * 86f); }
    void render(float alpha) {
        if (state.visible() && heroDocument != null) heroDocument.render(heroViewport, alpha);
        if (state.selected() && actionDocument != null) actionDocument.render(actionViewport, alpha);
    }

    List<BattleHudSurface> surfaces() {
        var result = new ArrayList<BattleHudSurface>();
        if (state.visible() && heroDocument != null && heroViewport != null) result.add(new BattleHudSurface(heroDocument, heroViewport));
        if (state.selected() && actionDocument != null && actionViewport != null) result.add(new BattleHudSurface(actionDocument, actionViewport));
        return List.copyOf(result);
    }
    void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        List<InputEventAPI> retained = events.stream().filter(e -> !e.isRMBDownEvent() && !e.isRMBUpEvent()).toList();
        if (state.visible() && heroInput != null) heroInput.process(retained);
        if (state.selected() && actionInput != null) actionInput.process(retained);
    }
    boolean blocksWorldPointer(float x, float y) {
        return state.visible() && contains(heroViewport, x, y) || state.selected() && contains(actionViewport, x, y);
    }
    private static boolean contains(UiViewport v, float x, float y) {
        return v != null && x >= v.screenX() && x < v.screenX() + v.width() && y >= v.screenY() && y < v.screenY() + v.height();
    }
    void detach() {
        if (heroDocument != null) heroDocument.deactivateInput();
        if (actionDocument != null) actionDocument.deactivateInput();
        heroInput = null; actionInput = null; position = null;
    }
}
