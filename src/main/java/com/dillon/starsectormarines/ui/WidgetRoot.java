package com.dillon.starsectormarines.ui;

import com.fs.starfarer.api.input.InputEventAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-of-tree container that walks render/advance and routes raw LWJGL input
 * (delivered via {@link com.fs.starfarer.api.campaign.CustomUIPanelPlugin#processInput})
 * into the appropriate child widget. Hit-testing is reverse-order so widgets
 * added later naturally draw on top and consume input first.
 *
 * <p>Mouse-move is broadcast (not consumed) so hover state works across all
 * widgets. Mouse-down/up are routed to the topmost containing widget and
 * consumed if the widget acts on them.
 */
public class WidgetRoot {

    private final List<Widget> children = new ArrayList<>();

    public void add(Widget w) {
        children.add(w);
    }

    public void clear() {
        children.clear();
    }

    public void advance(float dt) {
        for (Widget w : children) w.advance(dt);
    }

    public void render(float alphaMult) {
        for (Widget w : children) w.render(alphaMult);
    }

    public void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            int x = e.getX();
            int y = e.getY();

            if (e.isMouseMoveEvent()) {
                for (Widget w : children) {
                    w.onMouseMove(x, y);
                }
                continue;
            }
            if (e.isLMBDownEvent()) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    Widget w = children.get(i);
                    if (w.contains(x, y) && w.onMouseDown(x, y)) {
                        e.consume();
                        break;
                    }
                }
                continue;
            }
            if (e.isLMBUpEvent()) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    Widget w = children.get(i);
                    if (w.onMouseUp(x, y)) {
                        e.consume();
                        break;
                    }
                }
            }
        }
    }
}
