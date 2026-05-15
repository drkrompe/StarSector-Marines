package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

/**
 * Placeholder briefing surface. Renders a title and a Back button that returns
 * to {@link ScreenId#MISSION_SELECT}. The real two-zone layout (cropped terrain
 * + mission info + squad selector + Accept) lands in the next slice.
 */
public class BriefingScreen implements Screen {

    private static final Color TITLE_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private static final float PAD     = 12f;
    private static final float BACK_W  = 200f;
    private static final float BACK_H  = 32f;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private boolean attached;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        float backX = position.getX() + PAD;
        float backY = position.getY() + PAD;
        ButtonWidget back = new ButtonWidget(backX, backY, BACK_W, BACK_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT));
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                backX + 12f, backY + BACK_H - 6f, TITLE_COLOR));

        Mission m = ctx.getSelectedMission();
        String title = m != null ? "Briefing: " + m.name : "Briefing";
        float titleX = position.getX() + PAD;
        float titleY = position.getY() + position.getHeight() - PAD;
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD, title, titleX, titleY, TITLE_COLOR));

        attached = true;
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (!attached) return;
        widgets.render(alphaMult);
    }
}
