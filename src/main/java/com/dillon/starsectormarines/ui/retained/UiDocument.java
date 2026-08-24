package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.retained.style.StyleResolver;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * One retained element tree with layout and pointer state.
 *
 * <p>Pointer methods speak document coordinates. The hosting screen converts
 * through {@link UiViewport} once before calling them.
 */
public final class UiDocument {

    private final UiElement root;
    private final StyleResolver styles = new StyleResolver();
    private final UiTextMeasurer text = new UiTextMeasurer(styles);
    private final UiLayoutEngine layout = new UiLayoutEngine(text);
    private final UiPainter painter = new UiPainter();
    private final CanvasRegistry canvases = new CanvasRegistry(this);
    private Rect viewport = Rect.EMPTY;
    private List<UiElement> hovered = List.of();
    private UiElement pressed;
    private UiElement pointerCapture;
    private UiElement focused;
    private UiElement spaceArmed;
    private Runnable onCancel;
    private float pointerX;
    private float pointerY;
    private boolean pointerKnown;
    private int layoutPasses;
    private FrameStats lastFrame = FrameStats.IDLE;

    public UiDocument(UiElement root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        this.root = root;
    }

    public UiElement root() {
        return root;
    }

    public CanvasRegistry canvases() {
        return canvases;
    }

    public StyleResolver styles() {
        return styles;
    }

    public UiDocument addStyleSheet(StyleSheet sheet) {
        styles.addSheet(sheet);
        return this;
    }

    public UiDocument theme(UiTheme theme) {
        styles.theme(theme);
        return this;
    }

    public void replaceStyleSheet(StyleSheet sheet) {
        styles.replaceSheet(sheet);
    }

    public CanvasMetrics canvasMetrics(UiElement canvas, float devicePixelRatio) {
        if (!containsElement(canvas)) {
            throw new IllegalArgumentException("Canvas must belong to this document");
        }
        return CanvasMetrics.of(canvas, canvas.box(), devicePixelRatio);
    }

    public void layout(float width, float height) {
        validateInteractionReferences();
        viewport = new Rect(0f, 0f, Math.max(0f, width), Math.max(0f, height));
        styles.resolve(root);
        layout.layout(root, viewport.width(), viewport.height());
        layoutPasses++;
        canvases.prune();
        refreshHoverAfterLayout();
    }

    public void render(UiViewport viewport, float alphaMult) {
        synchronizeStyles();
        painter.paint(root, this.viewport, alphaMult, canvases, text,
                new StarsectorUiPaintTarget(viewport));
    }

    /** Paints through a non-host backend such as the headless UX renderer. */
    public void render(UiPaintTarget target, float alphaMult) {
        if (target == null) throw new IllegalArgumentException("paint target is required");
        synchronizeStyles();
        painter.paint(root, viewport, alphaMult, canvases, text, target);
    }

    /** Resolves targets, advances retained motion on real time, and relayouts only when needed. */
    public FrameStats advance(float realSeconds) {
        StyleResolver.ResolveResult resolved = styles.resolve(root);
        StyleResolver.AdvanceResult advanced = styles.advance(realSeconds);
        boolean relayout = resolved.layoutChanged() || advanced.layoutChanged()
                || root.layoutDirty() || root.descendantLayoutDirty();
        if (relayout) {
            layout.layout(root, viewport.width(), viewport.height());
            layoutPasses++;
            canvases.prune();
            refreshHoverAfterLayout();
        }
        lastFrame = new FrameStats(resolved.resolvedElements(), advanced.movedValues(),
                relayout ? 1 : 0, advanced.runningTransitions());
        return lastFrame;
    }

    public FrameStats lastFrameStats() {
        return lastFrame;
    }

    public int layoutPasses() {
        return layoutPasses;
    }

    private void synchronizeStyles() {
        StyleResolver.ResolveResult resolved = styles.resolve(root);
        if (!resolved.layoutChanged() && !root.layoutDirty()
                && !root.descendantLayoutDirty()) return;
        layout.layout(root, viewport.width(), viewport.height());
        layoutPasses++;
        canvases.prune();
        refreshHoverAfterLayout();
    }

    public void pointerMoved(float x, float y) {
        validateInteractionReferences();
        rememberPointer(x, y);
        UiElement target = pointerCapture != null ? pointerCapture : elementAt(x, y);
        updateHover(target);
        dispatchPointer(target, x, y, null, PointerPhase.MOVE);
    }

    public boolean pointerDown(float x, float y) {
        return pointerDown(x, y, PointerButton.PRIMARY);
    }

    public boolean pointerDown(float x, float y, PointerButton button) {
        validateInteractionReferences();
        rememberPointer(x, y);
        UiElement target = pointerCapture != null ? pointerCapture : elementAt(x, y);
        updateHover(target);
        UiPointerEvent event = dispatchPointer(target, x, y, button, PointerPhase.DOWN);
        if (button != PointerButton.PRIMARY || (event != null && event.defaultPrevented())) {
            return target != null;
        }
        requestFocus(nearestFocusable(target), false);
        if (pressed != null) pressed.armed(false);
        pressed = nearestClickable(target);
        if (pressed == null) return target != null;
        pressed.armed(true);
        return true;
    }

    public boolean pointerUp(float x, float y) {
        return pointerUp(x, y, PointerButton.PRIMARY);
    }

    public boolean pointerUp(float x, float y, PointerButton button) {
        validateInteractionReferences();
        rememberPointer(x, y);
        UiElement captureAtRelease = pointerCapture;
        UiElement target = captureAtRelease != null ? captureAtRelease : elementAt(x, y);
        UiPointerEvent event = dispatchPointer(target, x, y, button, PointerPhase.UP);
        UiElement wasPressed = pressed;
        if (button == PointerButton.PRIMARY) {
            pressed = null;
            if (wasPressed != null) {
                wasPressed.armed(false);
                UiElement releasedOver = nearestClickable(target);
                boolean capturedClick = captureAtRelease != null
                        && (wasPressed == captureAtRelease || isAncestor(captureAtRelease, wasPressed));
                if ((event == null || !event.defaultPrevented())
                        && (releasedOver == wasPressed || capturedClick)) {
                    wasPressed.click();
                }
            }
            pointerCapture = null;
            updateHover(elementAt(x, y));
        }
        return target != null || wasPressed != null || captureAtRelease != null;
    }

    public UiDocument onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    public UiElement focusedElement() {
        return focused;
    }

    public void requestFocus(UiElement element, boolean focusVisible) {
        if (element != null && (!attached(element) || !element.focusable())) {
            throw new IllegalArgumentException("Focus target must be attached and focusable");
        }
        if (focused == element && (element == null || element.focusVisible() == focusVisible)) return;
        cancelSpaceAction();
        if (focused != null) focused.focused(false, false);
        focused = element;
        if (focused != null) focused.focused(true, focusVisible);
    }

    public UiElement pointerCapture() {
        return pointerCapture;
    }

    public void setPointerCapture(UiElement element) {
        if (element == null || !attached(element)) {
            throw new IllegalArgumentException("Capture target must be attached");
        }
        pointerCapture = element;
        updateHover(element);
    }

    public void releasePointerCapture() {
        pointerCapture = null;
        if (pointerKnown) updateHover(elementAt(pointerX, pointerY));
    }

    /** Clears transient interaction when this document leaves the active screen. */
    public void deactivateInput() {
        cancelSpaceAction();
        if (pressed != null) pressed.armed(false);
        pressed = null;
        pointerCapture = null;
        updateHover(null);
        requestFocus(null, false);
        pointerKnown = false;
    }

    public boolean keyPressed(UiKey key, Set<KeyModifier> modifiers, boolean repeat) {
        validateInteractionReferences();
        Set<KeyModifier> held = modifiers == null ? Set.of() : modifiers;
        if (key == UiKey.TAB && onlyShift(held)) {
            return moveFocus(held.contains(KeyModifier.SHIFT));
        }
        if (!held.isEmpty()) return false;
        if (key == UiKey.ENTER && focused != null && focused.clickable()) {
            if (!repeat) focused.click();
            return true;
        }
        if (key == UiKey.SPACE && focused != null && focused.clickable()) {
            if (!repeat && spaceArmed == null) {
                spaceArmed = focused;
                spaceArmed.armed(true);
            }
            return true;
        }
        if (key == UiKey.ESCAPE && onCancel != null) {
            if (!repeat) onCancel.run();
            return true;
        }
        return false;
    }

    public boolean keyReleased(UiKey key, Set<KeyModifier> modifiers) {
        validateInteractionReferences();
        if (key != UiKey.SPACE || spaceArmed == null) return false;
        UiElement armed = spaceArmed;
        spaceArmed = null;
        armed.armed(false);
        if (armed == focused && armed.clickable()) armed.click();
        return true;
    }

    /**
     * Applies a vertical wheel delta to the nearest scroll surface that can
     * move, then walks outward when an inner surface reaches its boundary.
     * A wheel over a scroll surface remains handled at its terminal boundary
     * so the host cannot also act on the same notch.
     */
    public boolean pointerScrolled(float x, float y, float deltaY) {
        validateInteractionReferences();
        if (deltaY == 0f) return false;
        rememberPointer(x, y);
        boolean overScrollSurface = false;
        for (UiElement target = elementAt(x, y); target != null; target = target.parent()) {
            if (target.overflow() != Overflow.SCROLL) continue;
            overScrollSurface = true;
            float range = target.box().maxScrollTop();
            if (range <= 0f) continue;
            float from = Math.min(target.scrollTop(), range);
            float to = Math.max(0f, Math.min(from + deltaY, range));
            if (to == from) continue;
            target.scrollTop(to);
            layout.layout(root, viewport.width(), viewport.height());
            pointerMoved(x, y);
            return true;
        }
        return overScrollSurface;
    }

    UiElement hit(float x, float y) {
        for (UiElement target = elementAt(x, y); target != null; target = target.parent()) {
            if (target.clickable()) return target;
        }
        return null;
    }

    UiElement elementAt(float x, float y) {
        return elementAt(root, x, y, viewport);
    }

    private static UiElement elementAt(UiElement element, float x, float y,
                                       Rect inheritedClip) {
        Rect childClip = UiLayoutEngine.clipForChildren(
                element.overflow(), element.box(), inheritedClip);
        List<UiElement> children = element.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiElement hit = elementAt(children.get(i), x, y, childClip);
            if (hit != null) return hit;
        }
        return inheritedClip.contains(x, y)
                && element.box().borderBox().contains(x, y)
                ? element : null;
    }

    private boolean moveFocus(boolean reverse) {
        List<UiElement> order = new ArrayList<>();
        collectTabOrder(root, order);
        if (order.isEmpty()) {
            requestFocus(null, false);
            return false;
        }
        int current = order.indexOf(focused);
        int next;
        if (reverse) {
            next = current <= 0 ? order.size() - 1 : current - 1;
        } else {
            next = current < 0 || current == order.size() - 1 ? 0 : current + 1;
        }
        requestFocus(order.get(next), true);
        return true;
    }

    private static void collectTabOrder(UiElement element, List<UiElement> into) {
        if (element.tabbable()) into.add(element);
        for (UiElement child : element.children()) collectTabOrder(child, into);
    }

    private void cancelSpaceAction() {
        if (spaceArmed == null) return;
        spaceArmed.armed(false);
        spaceArmed = null;
    }

    private void rememberPointer(float x, float y) {
        pointerX = x;
        pointerY = y;
        pointerKnown = true;
    }

    private void updateHover(UiElement target) {
        List<UiElement> path = pathToRoot(target);
        if (hovered.equals(path)) return;
        for (UiElement element : hovered) {
            if (!path.contains(element)) element.hovered(false);
        }
        for (UiElement element : path) {
            if (!hovered.contains(element)) element.hovered(true);
        }
        hovered = path;
    }

    private void refreshHoverAfterLayout() {
        if (!pointerKnown) return;
        updateHover(pointerCapture != null ? pointerCapture : elementAt(pointerX, pointerY));
    }

    private static List<UiElement> pathToRoot(UiElement target) {
        if (target == null) return List.of();
        List<UiElement> path = new ArrayList<>();
        for (UiElement element = target; element != null; element = element.parent()) {
            path.add(element);
        }
        return Collections.unmodifiableList(path);
    }

    private UiPointerEvent dispatchPointer(UiElement target, float x, float y,
                                           PointerButton button, PointerPhase phase) {
        if (target == null) return null;
        UiPointerEvent event = new UiPointerEvent(this, target, x, y, button);
        for (UiElement element = target; element != null; element = element.parent()) {
            event.currentTarget(element);
            switch (phase) {
                case MOVE -> element.pointerMoved(event);
                case DOWN -> element.pointerDown(event);
                case UP -> element.pointerUp(event);
            }
            if (event.propagationStopped()) break;
        }
        return event;
    }

    private static UiElement nearestClickable(UiElement target) {
        for (UiElement element = target; element != null; element = element.parent()) {
            if (element.clickable()) return element;
        }
        return null;
    }

    private static UiElement nearestFocusable(UiElement target) {
        for (UiElement element = target; element != null; element = element.parent()) {
            if (element.focusable()) return element;
        }
        return null;
    }

    private boolean attached(UiElement element) {
        for (UiElement candidate = element; candidate != null; candidate = candidate.parent()) {
            if (candidate == root) return true;
        }
        return false;
    }

    boolean containsElement(UiElement element) {
        return element != null && attached(element);
    }

    private static boolean isAncestor(UiElement ancestor, UiElement element) {
        for (UiElement candidate = element.parent(); candidate != null; candidate = candidate.parent()) {
            if (candidate == ancestor) return true;
        }
        return false;
    }

    private static boolean onlyShift(Set<KeyModifier> modifiers) {
        return modifiers.isEmpty()
                || modifiers.size() == 1 && modifiers.contains(KeyModifier.SHIFT);
    }

    private void validateInteractionReferences() {
        if (focused != null && (!attached(focused) || !focused.focusable())) {
            requestFocus(null, false);
        }
        if (spaceArmed != null && (!attached(spaceArmed) || !spaceArmed.clickable())) {
            cancelSpaceAction();
        }
        if (pressed != null && (!attached(pressed) || !pressed.clickable())) {
            pressed.armed(false);
            pressed = null;
        }
        if (pointerCapture != null && !attached(pointerCapture)) {
            pointerCapture = null;
            updateHover(pointerKnown ? elementAt(pointerX, pointerY) : null);
        }
        boolean staleHover = false;
        for (UiElement element : hovered) {
            if (!attached(element)) {
                staleHover = true;
                break;
            }
        }
        if (staleHover) updateHover(pointerKnown ? elementAt(pointerX, pointerY) : null);
    }

    private enum PointerPhase {
        MOVE,
        DOWN,
        UP
    }

    public record FrameStats(int resolvedStyles, int transitionedValues,
                             int layoutPasses, int runningTransitions) {
        private static final FrameStats IDLE = new FrameStats(0, 0, 0, 0);
    }
}
