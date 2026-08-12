package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

/** Compact pre-battle whole-fireteam assignment surface. */
public final class SquadDeploymentScreen implements Screen {

    private static final float PAD = 18f;
    private static final float GAP = 12f;
    private static final float ROW_H = 48f;
    private static final float BUTTON_H = 32f;
    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color MUTED = new Color(0x8F, 0xA8, 0xC0);
    private static final Color READY = new Color(0x80, 0xD8, 0x98);
    private static final Color SELECTED = new Color(0xFF, 0xD0, 0x60);
    private static final Color BAD = new Color(0xE0, 0x70, 0x70);

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private MarineRoster roster;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;
        float left = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;
        int capacity = ctx.getMarineDeploymentCapacity();
        int assigned = assignedReady();

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Pre-Battle Fireteam Assignment", left, top, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Select persistent fireteams; tactical squads form around their actual lifts.",
                left, top - 30f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "READY SEATS  " + Math.min(assigned, capacity) + " / " + capacity
                        + (assigned > capacity ? "   (" + (assigned - capacity) + " reserve)" : ""),
                left, top - 60f, assigned >= capacity ? READY : BAD));

        if (roster == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Persistent roster unavailable.", left, top - 100f, BAD));
        } else {
            float colW = (position.getWidth() - 2f * PAD - GAP) / 2f;
            int rowsPerCol = Math.max(1, (int) ((position.getHeight() - 150f) / ROW_H));
            int index = 0;
            for (MarineSquad squad : roster.squads()) {
                if (squad.reserve()) continue;
                if (index >= rowsPerCol * 2) break;
                int col = index / rowsPerCol;
                int row = index % rowsPerCol;
                addSquad(squad, left + col * (colW + GAP), top - 100f - row * ROW_H, colW);
                index++;
            }
        }

        addButton(left, position.getY() + PAD, 160f, "Back to Briefing",
                () -> ctx.goTo(ScreenId.BRIEFING), HEADER);
    }

    private void addSquad(MarineSquad squad, float x, float y, float w) {
        boolean selected = ctx.isMarineSquadSelected(squad.id());
        int ready = roster.readyCount(squad);
        widgets.add(new ButtonWidget(x, y - BUTTON_H + 6f, w, BUTTON_H, () -> {
            ctx.toggleMarineSquad(squad.id());
            rebuild();
        }));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                (selected ? "[X] " : "[ ] ") + squad.name() + "   " + ready + "/" + MarineSquad.CAPACITY + " RTD",
                x + 8f, y, selected ? SELECTED : HEADER));

        int wia = 0, mia = 0, kia = 0;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.status() == MarineSoldierStatus.WIA) wia++;
            else if (soldier.status() == MarineSoldierStatus.MIA) mia++;
            else if (soldier.status() == MarineSoldierStatus.KIA) kia++;
        }
        String unavailable = "";
        if (wia > 0) unavailable += wia + " WIA  ";
        if (mia > 0) unavailable += mia + " MIA  ";
        if (kia > 0) unavailable += kia + " KIA";
        if (!unavailable.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, unavailable, x + w * 0.58f, y, MUTED));
        }
    }

    private int assignedReady() {
        if (roster == null) return 0;
        int total = 0;
        for (String id : ctx.getSelectedMarineSquadIds()) {
            total += roster.readyCount(roster.squadById(id));
        }
        return total;
    }

    private void addButton(float x, float y, float w, String label, Runnable action, Color color) {
        widgets.add(new ButtonWidget(x, y, w, BUTTON_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                x + 8f, y + BUTTON_H - 6f, color));
    }

    @Override public void advance(float dt) { widgets.advance(dt); }
    @Override public void render(float alphaMult) { widgets.render(alphaMult); }
    @Override public void processInput(List<InputEventAPI> events) { widgets.processInput(events); }
}
