package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.UiElement;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves the ordered cascade and owns per-element presented transition state. */
public final class StyleResolver {

    private final List<StyleSheet> componentSheets = new ArrayList<>();
    private final Map<UiElement, ComputedStyle> styles = new IdentityHashMap<>();
    private final Map<UiElement, List<RunningTransition>> running = new IdentityHashMap<>();
    private UiTheme theme;
    private boolean forceAll = true;

    public StyleResolver addSheet(StyleSheet sheet) {
        componentSheets.add(Objects.requireNonNull(sheet, "sheet"));
        invalidate();
        return this;
    }

    /** Installs or replaces the final theme layer without changing its cascade position. */
    public StyleResolver theme(UiTheme replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (theme != null && !theme.sheet().name().equals(replacement.sheet().name())) {
            throw new UiStyleException("Theme replacement must keep the sheet name \""
                    + theme.sheet().name() + "\", not \"" + replacement.sheet().name() + "\".");
        }
        theme = replacement;
        invalidate();
        return this;
    }

    public void replaceSheet(StyleSheet replacement) {
        Objects.requireNonNull(replacement, "replacement");
        for (int index = 0; index < componentSheets.size(); index++) {
            if (componentSheets.get(index).name().equals(replacement.name())) {
                componentSheets.set(index, replacement);
                invalidate();
                return;
            }
        }
        if (theme != null && theme.sheet().name().equals(replacement.name())) {
            theme = new UiTheme(replacement, theme.fonts());
            invalidate();
            return;
        }
        throw new UiStyleException("No sheet named \"" + replacement.name()
                + "\" exists to replace.");
    }

    public ComputedStyle styleOf(UiElement element) {
        return styles.get(element);
    }

    public BitmapFont fontFor(UiElement element) {
        ComputedStyle style = styles.get(element);
        if (style == null || style.fontFamily() == null) return null;
        Object family = style.fontFamily();
        if (family instanceof BitmapFont font) return font;
        if (theme == null) {
            throw new UiStyleException("font-family \"" + family + "\" needs an installed theme font.");
        }
        return theme.font((String) family);
    }

    public ResolveResult resolve(UiElement root) {
        if (!forceAll && !root.styleDirty() && !root.descendantStyleDirty()) {
            return new ResolveResult(0, false, false);
        }
        ResolveAccumulator result = new ResolveAccumulator();
        resolve(root, null, forceAll, result);
        forceAll = false;
        if (result.resolved > 0) prune(root);
        return new ResolveResult(result.resolved, result.layoutChanged, result.paintChanged);
    }

    private void resolve(UiElement element, ComputedStyle parent, boolean forced,
                         ResolveAccumulator result) {
        boolean dirty = forced || element.styleDirty() || styles.get(element) == null;
        ComputedStyle presented = styles.get(element);
        if (dirty) {
            ComputedStyle target = compute(element, parent);
            if (presented == null) {
                result.layoutChanged = true;
                result.paintChanged = true;
            } else {
                retime(element, presented, target);
                result.layoutChanged |= target.differsForLayout(presented);
                result.paintChanged |= target.differsForPaint(presented);
            }
            styles.put(element, target);
            element.computedStyle(target);
            element.clearStyleDirty();
            presented = target;
            result.resolved++;
        }
        if (dirty || element.descendantStyleDirty()) {
            for (UiElement child : element.children()) {
                if (dirty || child.styleDirty() || child.descendantStyleDirty()
                        || styles.get(child) == null) {
                    resolve(child, presented, dirty, result);
                }
            }
            element.clearDescendantStyleDirty();
        }
    }

    private ComputedStyle compute(UiElement element, ComputedStyle parent) {
        ComputedStyle style = new ComputedStyle();
        style.inheritFrom(parent);
        for (StyleSheet sheet : componentSheets) apply(sheet, element, style);
        if (theme != null) apply(theme.sheet(), element, style);
        style.apply(element.authoredStyle());
        if (element.hovered() && element.hoverBackgroundOverride() != null) {
            style.set(StyleProperty.BACKGROUND_COLOR, element.hoverBackgroundOverride());
        }
        if (element.armed() && element.armedBackgroundOverride() != null) {
            style.set(StyleProperty.BACKGROUND_COLOR, element.armedBackgroundOverride());
        }
        return style;
    }

    private static void apply(StyleSheet sheet, UiElement element, ComputedStyle style) {
        for (StyleRule rule : sheet.rules()) {
            if (rule.matches(element)) style.apply(rule.declaration());
        }
    }

    private void retime(UiElement element, ComputedStyle presented, ComputedStyle target) {
        List<TransitionSpec> specs = target.transitions();
        List<RunningTransition> active = running.get(element);
        if (active != null) {
            active.removeIf(transition -> specFor(specs, transition.property()) == null);
            if (active.isEmpty()) {
                running.remove(element);
                active = null;
            }
        }
        for (TransitionSpec spec : specs) {
            if (specFor(specs, spec.property()) != spec) continue;
            Object from = presented.value(spec.property());
            Object to = target.value(spec.property());
            RunningTransition transition = find(active, spec.property());
            if (!RunningTransition.canInterpolate(from, to)) {
                if (transition != null) active.remove(transition);
                continue;
            }
            if (transition != null) {
                transition.retarget(spec, from, to);
            } else if (!Objects.equals(from, to)) {
                active = active != null ? active
                        : running.computeIfAbsent(element, ignored -> new ArrayList<>(2));
                active.add(RunningTransition.start(spec, from, to));
            }
        }
        if (active == null) return;
        if (active.isEmpty()) {
            running.remove(element);
            return;
        }
        for (RunningTransition transition : active) {
            target.set(transition.property(), transition.currentValue());
        }
    }

    public AdvanceResult advance(float realSeconds) {
        if (!Float.isFinite(realSeconds) || realSeconds < 0f) {
            throw new IllegalArgumentException("Transition time must be finite and non-negative: "
                    + realSeconds);
        }
        if (running.isEmpty()) return new AdvanceResult(0, 0, false, false);
        int moved = 0;
        boolean layoutChanged = false;
        boolean paintChanged = false;
        Iterator<Map.Entry<UiElement, List<RunningTransition>>> entries =
                running.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UiElement, List<RunningTransition>> entry = entries.next();
            ComputedStyle style = styles.get(entry.getKey());
            if (style == null) {
                entries.remove();
                continue;
            }
            Iterator<RunningTransition> transitions = entry.getValue().iterator();
            while (transitions.hasNext()) {
                RunningTransition transition = transitions.next();
                transition.advance(realSeconds);
                if (transition.delayed()) continue;
                Object previous = style.value(transition.property());
                Object current = transition.currentValue();
                style.set(transition.property(), current);
                if (Objects.equals(previous, current)) {
                    if (transition.finished()) transitions.remove();
                    continue;
                }
                moved++;
                layoutChanged |= transition.property().affectsLayout();
                paintChanged |= transition.property().affectsPaint();
                if (transition.property().inherited()) {
                    for (UiElement child : entry.getKey().children()) child.invalidateStyle();
                }
                if (transition.finished()) transitions.remove();
            }
            if (entry.getValue().isEmpty()) entries.remove();
        }
        return new AdvanceResult(runningTransitions(), moved, layoutChanged, paintChanged);
    }

    public int runningTransitions() {
        int count = 0;
        for (List<RunningTransition> transitions : running.values()) count += transitions.size();
        return count;
    }

    public int cachedStyles() {
        return styles.size();
    }

    private void invalidate() {
        forceAll = true;
    }

    private void prune(UiElement root) {
        styles.keySet().removeIf(element -> !belongsTo(element, root));
        running.keySet().removeIf(element -> !belongsTo(element, root));
    }

    private static boolean belongsTo(UiElement element, UiElement root) {
        for (UiElement candidate = element; candidate != null; candidate = candidate.parent()) {
            if (candidate == root) return true;
        }
        return false;
    }

    private static TransitionSpec specFor(List<TransitionSpec> specs, StyleProperty property) {
        for (int index = specs.size() - 1; index >= 0; index--) {
            if (specs.get(index).property() == property) return specs.get(index);
        }
        return null;
    }

    private static RunningTransition find(List<RunningTransition> active,
                                          StyleProperty property) {
        if (active == null) return null;
        for (RunningTransition transition : active) {
            if (transition.property() == property) return transition;
        }
        return null;
    }

    public record ResolveResult(int resolvedElements, boolean layoutChanged,
                                boolean paintChanged) {
    }

    public record AdvanceResult(int runningTransitions, int movedValues,
                                boolean layoutChanged, boolean paintChanged) {
    }

    private static final class ResolveAccumulator {
        private int resolved;
        private boolean layoutChanged;
        private boolean paintChanged;
    }
}
