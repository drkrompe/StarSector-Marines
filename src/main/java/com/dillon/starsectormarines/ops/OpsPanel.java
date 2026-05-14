package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.WidgetRoot;

/**
 * Base class for one column of the marine ops screen. Each concrete panel owns
 * its column rect, its widgets, and its rendering. The plugin orchestrates
 * three of these; nothing else knows what a column does.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #attach} is called once per layout pass — panel stores rect/ctx
 *       and builds its widgets into the shared tree.</li>
 *   <li>{@link #onAdvance} ticks per frame (optional).</li>
 *   <li>{@link #onRender} draws non-widget content per frame (FBO blits,
 *       sprite draws, GL primitives).</li>
 * </ol>
 *
 * <p>The column header label is built by the plugin from {@link #getHeaderKey()}
 * — panels don't draw their own headers, only their body content.
 */
public abstract class OpsPanel {

    protected ColumnRect rect;
    protected MarineOpsContext ctx;

    public final void attach(ColumnRect rect, MarineOpsContext ctx, WidgetRoot widgets) {
        this.rect = rect;
        this.ctx = ctx;
        onLayout(widgets);
    }

    /** i18n key for the column header. */
    public abstract String getHeaderKey();

    /** Subclass hook: build column-specific widgets, given {@code rect} and {@code ctx}. */
    protected abstract void onLayout(WidgetRoot widgets);

    /** Optional per-frame state tick. Default no-op. */
    public void onAdvance(float dt) {}

    /** Subclass renders non-widget content. Widgets are drawn separately by the plugin. */
    public abstract void onRender(float alphaMult);
}
