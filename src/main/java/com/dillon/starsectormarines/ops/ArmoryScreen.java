package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

/** Player-facing fabrication and persistent soldier-allocation surface. */
public final class ArmoryScreen implements Screen {

    private static final float PAD = 16f;
    private static final float BUTTON_H = 32f;
    private static final float ROW_H = 42f;
    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xFF, 0xE0, 0x70);
    private static final Color MUTED = new Color(0x92, 0x9A, 0xA5);
    private static final Color GOOD = new Color(0x80, 0xD8, 0x98);

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private MarineRoster roster;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        MarineRosterScript script = MarineRosterScript.getInstance();
        this.roster = script != null ? script.roster() : null;
        if (roster != null) roster.ensureActiveSoldiers(10);
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;
        float left = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;

        addButton(left, position.getY() + PAD, 120f, "Back",
                () -> ctx.goTo(ScreenId.MISSION_SELECT), HEADER);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Fleet Armory / Persistent Personnel", left, top, HEADER));
        if (roster == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Marine roster unavailable.", left, top - 38f, MUTED));
            return;
        }

        MarineArmory armory = roster.armory();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Masterwork parts & materials: " + armory.fabricationMaterials()
                        + "    Victories: " + armory.victories()
                        + "    High-risk: " + armory.highRiskVictories(),
                left, top - 34f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Masterwork DMR: " + (armory.isPrimaryUnlocked(
                        MarineWeapon.DMR, EquipmentGrade.MASTERWORK)
                        ? "RECIPE UNLOCKED" : "locked — 5 victories + 1 high-risk victory"),
                left, top - 60f,
                armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK)
                        ? GOOD : MUTED));

        float printY = top - 106f;
        float printW = (position.getWidth() - 2 * PAD - 3 * 8f) / 4f;
        addPrintButton(left, printY, printW, MarineWeapon.PULSE_RIFLE,
                EquipmentGrade.SURPLUS, "Print Surplus Rifle · 1");
        addPrintButton(left + printW + 8f, printY, printW, MarineWeapon.SMG,
                EquipmentGrade.SERVICE, "Print Service SMG · 2");
        addPrintButton(left + 2f * (printW + 8f), printY, printW, MarineWeapon.DMR,
                EquipmentGrade.SERVICE, "Print Service DMR · 2");
        addPrintButton(left + 3f * (printW + 8f), printY, printW, MarineWeapon.PULSE_RIFLE,
                EquipmentGrade.MILSPEC, "Print Milspec Rifle · 4");

        float printY2 = printY - BUTTON_H - 8f;
        addPrintButton(left, printY2, printW, MarineWeapon.SMG,
                EquipmentGrade.MILSPEC, "Print Milspec SMG · 4");
        addPrintButton(left + printW + 8f, printY2, printW, MarineWeapon.DMR,
                EquipmentGrade.MILSPEC, "Print Milspec DMR · 4");
        addPrintButton(left + 2f * (printW + 8f), printY2, printW, MarineWeapon.DMR,
                EquipmentGrade.MASTERWORK, "Print Masterwork DMR · 8");

        float rowY = printY2 - 54f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Soldier                 Development       Allocated field kit",
                left, rowY + 22f, HEADER));
        int shown = 0;
        for (MarineSoldier soldier : roster.activeSoldiers()) {
            if (shown++ >= 10 || rowY < position.getY() + PAD + BUTTON_H + 12f) break;
            addSoldierRow(soldier, left, rowY, position.getWidth() - 2 * PAD);
            rowY -= ROW_H;
        }
    }

    private void addPrintButton(float x, float y, float w, MarineWeapon weapon,
                                EquipmentGrade grade, String label) {
        MarineArmory armory = roster.armory();
        boolean unlocked = armory.isPrimaryUnlocked(weapon, grade);
        Runnable action = unlocked ? () -> {
            armory.printPrimary(weapon, grade);
            rebuild();
        } : null;
        String count = unlocked ? "  [" + armory.ownedPrimary(weapon, grade) + "]" : "  [LOCKED]";
        addButton(x, y, w, label + count, action, unlocked ? HEADER : MUTED);
    }

    private void addSoldierRow(MarineSoldier soldier, float x, float y, float w) {
        String kit = soldier.primary().displayName + "-" + soldier.primaryGrade().tierMark()
                + " / " + soldier.armor().displayName
                + (soldier.secondary() != null ? " / Rockets" : "");
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.name(), x, y + 25f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.profile().shortLabel() + "  " + soldier.experienceXp() + " XP",
                x + w * 0.24f, y + 25f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                kit, x + w * 0.41f, y + 25f, GOOD));
        addButton(x + w - 210f, y + 4f, 98f, "Cycle weapon", () -> {
            roster.cyclePrimary(soldier.id());
            rebuild();
        }, HEADER);
        addButton(x + w - 104f, y + 4f, 104f, "Cycle armor", () -> {
            roster.cycleArmor(soldier.id());
            rebuild();
        }, HEADER);
    }

    private void addButton(float x, float y, float w, String text,
                           Runnable action, Color color) {
        widgets.add(new ButtonWidget(x, y, w, BUTTON_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, text,
                x + 8f, y + BUTTON_H - 6f, color));
    }

    @Override public void advance(float dt) { widgets.advance(dt); }
    @Override public void render(float alphaMult) { widgets.render(alphaMult); }
    @Override public void processInput(List<InputEventAPI> events) { widgets.processInput(events); }
}
