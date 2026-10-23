package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleDirectControlHelp.Layout;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/**
 * Strategic entry above selection. During takeover the HUD follows the game's own combat HUD:
 * the body's status at bottom left (beside its doll), its weapon groups at bottom
 * right, and the session controls at top right. The centre stays clear for the fight.
 */
final class BattleDirectControlOverlay {
    static final String COMPONENT = "battle-direct-control-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final String STATUS_COMPONENT = "battle-direct-control-status";
    static final String STATUS_COMPONENT_PATH = "data/ui/components/battle/" + STATUS_COMPONENT + ".mlx";
    static final String WEAPONS_COMPONENT = "battle-direct-control-weapons";
    static final String WEAPONS_COMPONENT_PATH = "data/ui/components/battle/" + WEAPONS_COMPONENT + ".mlx";
    static final float STATUS_WIDTH = 300f;
    static final float WEAPONS_WIDTH = 360f;
    /** Layout height a panel is measured at; its spacer absorbs whatever the content does not use. */
    static final float MEASURE_HEIGHT = 2000f;
    static final float MARGIN = 12f;
    /** How strongly a panel the controlled body has moved under is faded, so the body stays readable. */
    static final float OCCLUDING_ALPHA = .3f;
    static final String ACTIONS_COMPONENT = "battle-direct-control-actions";
    static final String ACTIONS_COMPONENT_PATH = "data/ui/components/battle/" + ACTIONS_COMPONENT + ".mlx";
    static final float ACTIONS_WIDTH = 312f;
    static final float ACTIONS_HEIGHT = 24f;
    static final float DOCUMENT_WIDTH = 170f;
    static final float DOCUMENT_HEIGHT = 26f;
    private static final float SELECTION_GAP = 4f;
    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> label = reactor.signal("");
    private final MutableSignal<Boolean> disabled = reactor.signal(true);
    private final Selection selection;
    private final Runnable toggle;
    private final BattleDirectControlHudModel actionModel;
    private PositionAPI position;
    private UiDocument document;
    private MarkupInstance markup;
    private UiDocument statusDocument;
    private MarkupInstance statusMarkup;
    private UiViewport statusViewport;
    private float statusHeight;
    private UiDocument weaponsDocument;
    private MarkupInstance weaponsMarkup;
    private UiViewport weaponsViewport;
    private StarsectorUiInputAdapter weaponsInput;
    private float weaponsHeight;
    private String measuredStructure;
    private boolean statusOccluding, weaponsOccluding;
    private UiDocument actionsDocument;
    private MarkupInstance actionsMarkup;
    private UiViewport actionsViewport;
    private StarsectorUiInputAdapter actionsInput;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private final BattleDirectControlHelpPlate helpPlate = new BattleDirectControlHelpPlate(reactor);
    private final BattleDirectControlHelp.Hover helpHover = new BattleDirectControlHelp.Hover();
    private MarkupInstance helpMarkup;
    private UiDocument helpDocument;
    private UiViewport helpViewport;
    private Layout helpLayout;
    private Carrier helpCarrier = Carrier.NONE;
    private boolean helpPointerKnown;
    private float helpPointerX, helpPointerY;
    private boolean visible;
    private BattleDamageDollPlate sectionPlate;
    private BattleMarineDollPlate marinePlate;
    private UiViewport sectionViewport;

    private boolean activeMode;

    BattleDirectControlOverlay(Selection selection, Runnable toggle, Runnable togglePause) {
        this(selection, toggle, togglePause, ignored -> {});
    }

    BattleDirectControlOverlay(Selection selection, Runnable toggle, Runnable togglePause,
                              IntConsumer selectWeapon) {
        this.selection = selection;
        this.toggle = toggle;
        actionModel = new BattleDirectControlHudModel(reactor, selectWeapon, toggle, togglePause);
    }

    void setExitLabelKey(String key) { actionModel.setExitLabelKey(key); }

    void attach(PositionAPI position, BattleSimulation sim, float speedMultiplier) {
        this.position = position;
        if (document == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, Map.of(
                    "label", label, "disabled", disabled, "toggle", toggle));
            document = document(markup);
            MarkupLoader panelLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(STATUS_COMPONENT_PATH, WEAPONS_COMPONENT_PATH));
            statusMarkup = panelLoader.reloadAndBuild(reactor, STATUS_COMPONENT, actionModel.statusProps());
            statusDocument = document(statusMarkup);
            weaponsMarkup = panelLoader.build(reactor, WEAPONS_COMPONENT, actionModel.weaponsProps());
            weaponsDocument = document(weaponsMarkup);
            MarkupLoader actionsLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(ACTIONS_COMPONENT_PATH));
            actionsMarkup = actionsLoader.reloadAndBuild(reactor, ACTIONS_COMPONENT, actionModel.actionsProps());
            actionsDocument = document(actionsMarkup);
            MarkupLoader helpLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(BattleDirectControlHelpPlate.COMPONENT_PATH));
            helpMarkup = helpLoader.reloadAndBuild(reactor, BattleDirectControlHelpPlate.COMPONENT,
                    helpPlate.props());
            helpDocument = document(helpMarkup);
            sectionPlate = new BattleDamageDollPlate();
            marinePlate = new BattleMarineDollPlate();
        }
        update(0f, sim, speedMultiplier);
    }

    /** The strategic entry shortcut's bounds for a selected mech. */
    static UiViewport viewport(PositionAPI position) {
        return entryViewport(position, true);
    }

    /**
     * The status panel at bottom left, {@code documentHeight} tall. The body's doll (a machine's damage
     * doll or a Marine's) takes the corner itself and the status stands to its right, bottom edges level.
     */
    static UiViewport statusViewport(PositionAPI position, float documentHeight, boolean doll) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        float left = position.getX() + MARGIN
                + (doll ? (BattleDamageDollPlate.DOCUMENT_WIDTH + BattleDamageDollPlate.GAP) * scale : 0f);
        float width = Math.max(0f, Math.min(STATUS_WIDTH * scale, position.getX() + position.getWidth() - MARGIN - left));
        return new UiViewport(left, position.getY() + MARGIN, width, documentHeight * scale, scale);
    }

    /** The weapon-groups panel at bottom right, {@code documentHeight} tall. */
    static UiViewport weaponsViewport(PositionAPI position, float documentHeight) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        float width = Math.max(0f, Math.min(WEAPONS_WIDTH * scale, position.getWidth() - 2f * MARGIN));
        return new UiViewport(position.getX() + position.getWidth() - MARGIN - width, position.getY() + MARGIN,
                width, documentHeight * scale, scale);
    }

    /** The damage doll in the bottom-left corner, beside the status panel. */
    static UiViewport sectionsViewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        return new UiViewport(position.getX() + MARGIN, position.getY() + MARGIN,
                BattleDamageDollPlate.DOCUMENT_WIDTH * scale, BattleDamageDollPlate.DOCUMENT_HEIGHT * scale, scale);
    }

    /**
     * The height a panel's content needs at {@code width}: laid out tall, less what its spacer
     * absorbed. Panels hug the bottom edge, so this is also where their top edge sits.
     */
    static float naturalHeight(UiDocument document, MarkupInstance markup, String spacerId, float width) {
        document.layout(width, MEASURE_HEIGHT);
        return MEASURE_HEIGHT - markup.requireElement(spacerId).box().borderBox().height();
    }

    /** The shortcut sits just above the selected unit's detail card, aligned to its right edge. */
    static UiViewport entryViewport(PositionAPI position, boolean mech) {
        UiViewport panel = mech ? BattleMechOverlay.viewport(position) : BattleSquadOverlay.viewport(position);
        float scale = panel.documentScale();
        float panelWidth = Math.min(BattleSquadOverlay.PANEL_WIDTH * scale, position.getWidth() - 24f);
        float width = Math.min(DOCUMENT_WIDTH * scale, panelWidth);
        return new UiViewport(panel.screenX() + panelWidth - width,
                panel.screenY() + panel.height() + SELECTION_GAP * scale,
                width, DOCUMENT_HEIGHT * scale, scale);
    }

    /** A selected ship's shortcut joins its selector in the compact left-hand stack. */
    static UiViewport shipEntryViewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        float width = Math.min(DOCUMENT_WIDTH * scale, position.getWidth() - 24f);
        float heroInset = BattleShipCommandOverlay.heroBottomInset(position, null, false);
        return new UiViewport(position.getX() + 12f,
                position.getY() + heroInset + 12f + (BattleShipCommandOverlay.HERO_HEIGHT + 6f) * scale,
                width, DOCUMENT_HEIGHT * scale, scale);
    }

    static UiViewport actionsViewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        return new UiViewport(position.getX() + position.getWidth() - 12f - ACTIONS_WIDTH * scale,
                position.getY() + position.getHeight() - 12f - ACTIONS_HEIGHT * scale,
                ACTIONS_WIDTH * scale, ACTIONS_HEIGHT * scale, scale);
    }

    void update(float dt, BattleSimulation sim, float speedMultiplier) {
        boolean active = sim != null && sim.directControl().active();
        if (active != activeMode) {
            deactivateDocuments();
            input = null;
            actionsInput = null;
            weaponsInput = null;
            statusViewport = null;
            weaponsViewport = null;
            measuredStructure = null;
            statusOccluding = weaponsOccluding = false;
            helpViewport = null;
            helpHover.collapse();
        }
        activeMode = active;
        long selected = selection.hasVehicleSelection() ? selection.getSelectedVehicleId()
                : selection.getSelectedUnitEntityId();
        boolean eligible = sim != null && (sim.directControl().canEnter(selected)
                || selection.hasWarshipSelection()
                && sim.directControl().canEnterWarship(selection.getSelectedWarship()));
        boolean nextVisible = sim != null && !sim.isComplete() && (active || eligible);
        if (visible && !nextVisible) deactivateDocuments();
        visible = nextVisible;
        disabled.set(!active && !eligible);
        label.set(Strings.get("battleDirectEnter"));
        if (active) {
            var status = BattleDirectControlStatus.snapshot(sim);
            helpCarrier = status.carrier();
            actionModel.update(status, sim.directControl().selectedWeapon(), speedMultiplier == 0f);
            if (sectionPlate != null) updateDoll(sim, status);
        } else if (sectionPlate != null) {
            sectionPlate.clear();
            marinePlate.clear();
        }
        updateHelp(dt, active);
        if (markup != null) markup.flush();
        if (statusMarkup != null) statusMarkup.flush();
        if (weaponsMarkup != null) weaponsMarkup.flush();
        if (actionsMarkup != null) actionsMarkup.flush();
        if (helpMarkup != null) helpMarkup.flush();
        if (active) {
            updatePanels(dt);
        } else if (position != null && document != null) {
            UiViewport next = selection.hasWarshipSelection() ? shipEntryViewport(position)
                    : entryViewport(position, eligible && selected != 0L && sim.world().hasMechLoadout(selected));
            if (!next.equals(viewport) || input == null) {
                document.deactivateInput();
                viewport = next;
                document.layout(viewport.documentWidth(), viewport.documentHeight());
                input = new StarsectorUiInputAdapter(document, viewport);
            }
            document.advance(dt);
        }
        if (active && position != null && actionsDocument != null) {
            UiViewport next = actionsViewport(position);
            if (!next.equals(actionsViewport) || actionsInput == null) {
                actionsDocument.deactivateInput();
                actionsViewport = next;
                actionsDocument.layout(next.documentWidth(), next.documentHeight());
                actionsInput = new StarsectorUiInputAdapter(actionsDocument, next);
            }
            actionsDocument.advance(dt);
        }
    }

    /** Shows the controlled body on its doll: a mech's sections, a vehicle's hull, or a Marine. */
    private void updateDoll(BattleSimulation sim, BattleDirectControlStatus.Snapshot status) {
        long id = status.entityId();
        if (status.carrier() == Carrier.MARINE) {
            sectionPlate.clear();
            marinePlate.update(BattleMarineDollPlate.MarineDoll.read(sim, id, Strings::get));
            return;
        }
        marinePlate.clear();
        if (status.carrier() == Carrier.MECH) {
            sectionPlate.update(sim.world().mechLoadout(id));
        } else if (status.carrier() == Carrier.VEHICLE) {
            var durability = status.durability();
            var turret = sim.convoy().turret(id);
            sectionPlate.updateVehicle(sim.convoy().vehicleType(id), durability.hp(), durability.maxHp(),
                    durability.armor(), durability.maxArmor(), turret != null ? turret.section : null,
                    sim.convoy().locations(id));
        } else {
            sectionPlate.clear();
        }
    }

    /** Re-measures the panels when a row comes or goes, then keeps them in their corners. */
    private void updatePanels(float dt) {
        if (position == null || statusDocument == null || weaponsDocument == null) return;
        boolean doll = hasDoll(actionModel.carrier());
        if (!actionModel.structureKey().equals(measuredStructure)) {
            measuredStructure = actionModel.structureKey();
            float status = naturalHeight(statusDocument, statusMarkup, "direct-status-spacer", STATUS_WIDTH);
            statusHeight = doll ? Math.max(status, BattleDamageDollPlate.DOCUMENT_HEIGHT) : status;
            weaponsHeight = naturalHeight(weaponsDocument, weaponsMarkup, "direct-weapons-spacer", WEAPONS_WIDTH);
            statusViewport = null;
            weaponsViewport = null;
        }
        UiViewport nextStatus = statusViewport(position, statusHeight, doll);
        if (!nextStatus.equals(statusViewport)) {
            statusViewport = nextStatus;
            statusDocument.layout(nextStatus.documentWidth(), nextStatus.documentHeight());
        }
        UiViewport nextWeapons = weaponsViewport(position, weaponsHeight);
        if (!nextWeapons.equals(weaponsViewport) || weaponsInput == null) {
            weaponsDocument.deactivateInput();
            weaponsViewport = nextWeapons;
            weaponsDocument.layout(nextWeapons.documentWidth(), nextWeapons.documentHeight());
            weaponsInput = new StarsectorUiInputAdapter(weaponsDocument, nextWeapons);
        }
        sectionViewport = doll ? sectionsViewport(position) : null;
        statusDocument.advance(dt);
        weaponsDocument.advance(dt);
        if (sectionPlate != null && sectionPlate.visible()) sectionPlate.document().advance(dt);
        if (marinePlate != null && marinePlate.visible()) marinePlate.document().advance(dt);
    }

    /** Whether a carrier shows a doll in the corner: a machine's damage doll, or a Marine's. */
    static boolean hasDoll(Carrier carrier) {
        return carrier == Carrier.MECH || carrier == Carrier.VEHICLE || carrier == Carrier.MARINE;
    }

    private boolean dollVisible() {
        return sectionPlate != null && sectionPlate.visible() || marinePlate != null && marinePlate.visible();
    }

    /** The help tab is hover-only: pointer events are observed here, never consumed or acted on. */
    private void trackPointer(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            // An earlier handler may already have consumed the event; its coordinates are then unreadable.
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent() || event.isLMBDownEvent() || event.isLMBUpEvent()
                    || event.isRMBDownEvent() || event.isRMBUpEvent()) {
                helpPointerKnown = true;
                helpPointerX = event.getX();
                helpPointerY = event.getY();
            }
        }
    }

    private void updateHelp(float dt, boolean active) {
        if (helpDocument == null || position == null || !active) {
            helpLayout = null;
            helpHover.collapse();
            return;
        }
        UiViewport strip = actionsViewport(position);
        float top = strip.screenY() + strip.height();
        Layout layout = BattleDirectControlHelp.layout(strip.screenX(), top, strip.documentScale(),
                BattleDirectControlHelpPlate.rowCount(helpCarrier));
        helpLayout = layout;
        boolean expanded = helpHover.update(true, helpPointerKnown, helpPointerX, helpPointerY, layout);
        helpPlate.update(expanded, helpCarrier);
        helpMarkup.flush();
        var bounds = expanded ? layout.union() : layout.tab();
        float scale = strip.documentScale();
        UiViewport next = new UiViewport(bounds.x(), bounds.y(), bounds.width(), bounds.height(), scale);
        if (!next.equals(helpViewport)) {
            helpViewport = next;
            helpDocument.layout(next.documentWidth(), next.documentHeight());
        }
        helpDocument.advance(dt);
    }

    void processInput(List<InputEventAPI> events) {
        if (events != null) trackPointer(events);
        if (!visible || events == null) return;
        if (activeMode) {
            if (actionsInput != null) actionsInput.process(events);
            if (weaponsInput != null && !weaponsOccluding) weaponsInput.process(events);
        } else if (input != null) {
            input.process(events);
        }
    }

    void render(float alpha) {
        if (!visible) return;
        if (!activeMode) {
            if (document != null && viewport != null) document.render(viewport, alpha);
            return;
        }
        float statusAlpha = statusOccluding ? alpha * OCCLUDING_ALPHA : alpha;
        if (sectionPlate != null) sectionPlate.render(sectionViewport, statusAlpha);
        if (marinePlate != null) marinePlate.render(sectionViewport, statusAlpha);
        if (statusDocument != null && statusViewport != null) statusDocument.render(statusViewport, statusAlpha);
        if (weaponsDocument != null && weaponsViewport != null) {
            weaponsDocument.render(weaponsViewport, weaponsOccluding ? alpha * OCCLUDING_ALPHA : alpha);
        }
        if (actionsDocument != null) actionsDocument.render(actionsViewport, alpha);
        if (helpDocument != null && helpViewport != null) helpDocument.render(helpViewport, alpha);
    }

    List<BattleHudSurface> surfaces() {
        List<BattleHudSurface> result = new ArrayList<>();
        if (!visible) return result;
        if (!activeMode) {
            if (document != null && viewport != null) result.add(new BattleHudSurface(document, viewport));
            return List.copyOf(result);
        }
        if (statusDocument != null && statusViewport != null) result.add(new BattleHudSurface(statusDocument, statusViewport));
        if (weaponsDocument != null && weaponsViewport != null) result.add(new BattleHudSurface(weaponsDocument, weaponsViewport));
        if (actionsDocument != null && actionsViewport != null) result.add(new BattleHudSurface(actionsDocument, actionsViewport));
        if (helpDocument != null && helpViewport != null) result.add(new BattleHudSurface(helpDocument, helpViewport));
        if (sectionPlate != null) {
            BattleHudSurface sections = sectionPlate.surface(sectionViewport);
            if (sections != null) result.add(sections);
        }
        if (marinePlate != null) {
            BattleHudSurface marine = marinePlate.surface(sectionViewport);
            if (marine != null) result.add(marine);
        }
        return List.copyOf(result);
    }

    /** A faded panel lets the pointer through: the body beneath it is what the player is aiming at. */
    boolean blocksWorldPointer(float x, float y) {
        if (!visible) return false;
        if (!activeMode) return contains(viewport, x, y);
        return contains(actionsViewport, x, y)
                || !weaponsOccluding && contains(weaponsViewport, x, y)
                || !statusOccluding && (contains(statusViewport, x, y)
                || dollVisible() && contains(sectionViewport, x, y))
                || helpLayout != null && helpHover.blocks(true, x, y, helpLayout);
    }

    private static boolean contains(UiViewport bounds, float x, float y) {
        return bounds != null && x >= bounds.screenX() && x < bounds.screenX() + bounds.width()
                && y >= bounds.screenY() && y < bounds.screenY() + bounds.height();
    }

    void detach() {
        deactivateDocuments();
        input = null;
        actionsInput = null;
        weaponsInput = null;
        helpViewport = null;
        helpHover.collapse();
    }

    /**
     * Map clamping can put the body in a bottom corner. The panel it would sit under fades and
     * lets the pointer through rather than moving: chrome that jumps is harder to read than chrome
     * that steps back.
     */
    void avoidControlledBody(float screenX, float screenY, float screenRadius) {
        if (!activeMode) return;
        float radius = Math.max(0f, screenRadius) + 8f;
        statusOccluding = overlaps(statusViewport, screenX, screenY, radius)
                || dollVisible() && overlaps(sectionViewport, screenX, screenY, radius);
        weaponsOccluding = overlaps(weaponsViewport, screenX, screenY, radius);
        if (weaponsOccluding && weaponsDocument != null) weaponsDocument.deactivateInput();
    }

    static boolean overlaps(UiViewport bounds, float x, float y, float radius) {
        return bounds != null && x + radius > bounds.screenX() && x - radius < bounds.screenX() + bounds.width()
                && y + radius > bounds.screenY() && y - radius < bounds.screenY() + bounds.height();
    }

    boolean statusOccluding() { return statusOccluding; }
    boolean weaponsOccluding() { return weaponsOccluding; }

    private void deactivateDocuments() {
        if (document != null) document.deactivateInput();
        if (weaponsDocument != null) weaponsDocument.deactivateInput();
        if (actionsDocument != null) actionsDocument.deactivateInput();
    }

    static UiDocument document(MarkupInstance markup) {
        UiDocument result = new UiDocument(markup.root());
        for (var style : markup.styles()) result.addStyleSheet(style);
        result.theme(MarineOpsThemes.standard());
        return result;
    }
}
