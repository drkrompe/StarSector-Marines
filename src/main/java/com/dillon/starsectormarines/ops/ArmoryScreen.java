package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.TextFieldWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

/** Fabrication plus squad-centric persistent personnel management. */
public final class ArmoryScreen implements Screen {

    private static final float PAD = 16f;
    private static final float GAP = 10f;
    private static final float BUTTON_H = 32f;
    private static final float SQUAD_ROW_H = 36f;
    private static final float MEMBER_ROW_H = 46f;
    private static final float SQUAD_COL_W = 224f;
    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xFF, 0xE0, 0x70);
    private static final Color MUTED = new Color(0x92, 0x9A, 0xA5);
    private static final Color GOOD = new Color(0x80, 0xD8, 0x98);
    private static final Color BAD = new Color(0xE0, 0x70, 0x70);

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private MarineRoster roster;
    private String selectedSquadId;
    private int squadPage;
    private int memberPage;
    private TextFieldWidget renameField;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
        if (roster != null) {
            if (roster.soldiers().isEmpty()) roster.ensureActiveSoldiers(10);
            roster.reserveSquad();
            if (roster.squadById(selectedSquadId) == null && !roster.squads().isEmpty()) {
                selectedSquadId = roster.squads().get(0).id();
            }
        }
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        renameField = null;
        if (position == null || ctx == null) return;
        float left = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;

        addButton(left, position.getY() + PAD, 120f, "Back",
                () -> ctx.goTo(ScreenId.MISSION_SELECT), HEADER);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Fleet Armory / Fireteam Personnel", left, top, HEADER));
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

        float managementTop = printY2 - 48f;
        float managementBottom = position.getY() + PAD + BUTTON_H + 12f;
        buildSquadList(left, managementTop, managementBottom);
        buildSelectedSquad(left + SQUAD_COL_W + GAP, managementTop, managementBottom,
                position.getWidth() - 2f * PAD - SQUAD_COL_W - GAP);
    }

    private void buildSquadList(float x, float top, float bottom) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FIRETEAMS", x, top + 24f, HEADER));
        int pageSize = Math.max(3, (int) ((top - bottom - 44f) / SQUAD_ROW_H));
        int pages = Math.max(1, (roster.squads().size() + pageSize - 1) / pageSize);
        squadPage = Math.max(0, Math.min(squadPage, pages - 1));
        int start = squadPage * pageSize;
        int end = Math.min(roster.squads().size(), start + pageSize);
        float y = top - 12f;
        for (int i = start; i < end; i++) {
            MarineSquad squad = roster.squads().get(i);
            boolean selected = squad.id().equals(selectedSquadId);
            int unavailable = roster.squadMembers(squad).size() - roster.readyCount(squad);
            String label = (selected ? "> " : "  ") + squad.name()
                    + "  " + roster.readyCount(squad)
                    + (squad.reserve() ? " ready" : "/" + MarineSquad.CAPACITY)
                    + (unavailable > 0 ? "  +" + unavailable + " unavailable" : "");
            addButton(x, y - BUTTON_H + 6f, SQUAD_COL_W, label, () -> {
                selectedSquadId = squad.id();
                memberPage = 0;
                rebuild();
            }, selected ? VALUE : HEADER);
            y -= SQUAD_ROW_H;
        }
        if (pages > 1) {
            addButton(x, bottom, 104f, "Prev", squadPage > 0 ? () -> {
                squadPage--;
                rebuild();
            } : null, squadPage > 0 ? HEADER : MUTED);
            addButton(x + 112f, bottom, 112f, "Next " + (squadPage + 1) + "/" + pages,
                    squadPage + 1 < pages ? () -> {
                        squadPage++;
                        rebuild();
                    } : null, squadPage + 1 < pages ? HEADER : MUTED);
        }
    }

    private void buildSelectedSquad(float x, float top, float bottom, float width) {
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (squad == null) return;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                squad.reserve() ? "RESERVE POOL" : "SELECTED FIRETEAM",
                x, top + 24f, HEADER));
        if (!squad.reserve()) {
            renameField = new TextFieldWidget(x, top - 14f, 210f, BUTTON_H,
                    Fonts.ORBITRON_20, 22, "Fireteam name");
            renameField.setText(squad.name());
            renameField.setOnChange(value -> roster.renameSquad(squad.id(), value));
            widgets.add(renameField);
        } else {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    squad.name(), x, top - 2f, VALUE));
        }

        float actionX = x + 222f;
        int vacancies = roster.vacancies(squad);
        boolean canRecruit = squad.reserve() || vacancies > 0;
        addButton(actionX, top - 14f, 138f,
                squad.reserve() ? "Hire Reserve"
                        : vacancies > 0 ? "Reinforce (" + vacancies + ")" : "Fully Manned",
                canRecruit ? () -> {
                    roster.recruitToSquad(squad.id());
                    rebuild();
                } : null, canRecruit ? GOOD : MUTED);
        addButton(actionX + 148f, top - 14f, 130f, "New Fireteam", () -> {
            MarineSquad created = roster.createFireteam();
            selectedSquadId = created.id();
            squadPage = Integer.MAX_VALUE;
            memberPage = 0;
            rebuild();
        }, HEADER);

        float rowTop = top - 58f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Marine / status          Development          Field kit",
                x, rowTop + 18f, HEADER));
        List<MarineSoldier> members = roster.squadMembers(squad);
        int pageSize = Math.max(1, (int) ((rowTop - bottom - 38f) / MEMBER_ROW_H));
        int pages = Math.max(1, (members.size() + pageSize - 1) / pageSize);
        memberPage = Math.max(0, Math.min(memberPage, pages - 1));
        int start = memberPage * pageSize;
        int end = Math.min(members.size(), start + pageSize);
        float rowY = rowTop - 16f;
        for (int i = start; i < end; i++) {
            addSoldierRow(members.get(i), x, rowY, width);
            rowY -= MEMBER_ROW_H;
        }
        if (members.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    squad.reserve() ? "No marines held in reserve."
                            : "Empty fireteam — reinforce or transfer personnel here.",
                    x, rowY, MUTED));
        }
        if (pages > 1) {
            addButton(x, bottom, 130f, "Prev Members", memberPage > 0 ? () -> {
                memberPage--;
                rebuild();
            } : null, memberPage > 0 ? HEADER : MUTED);
            addButton(x + 140f, bottom, 160f,
                    "Next " + (memberPage + 1) + "/" + pages,
                    memberPage + 1 < pages ? () -> {
                        memberPage++;
                        rebuild();
                    } : null, memberPage + 1 < pages ? HEADER : MUTED);
        }
    }

    private void addSoldierRow(MarineSoldier soldier, float x, float y, float w) {
        boolean ready = soldier.status() == MarineSoldierStatus.ACTIVE;
        String kit = soldier.primary().displayName + "-" + soldier.primaryGrade().tierMark()
                + " / " + soldier.armor().displayName
                + (soldier.secondary() != null ? " / Rockets" : "");
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.name() + "  " + statusLabel(soldier), x, y + 26f,
                ready ? HEADER : statusColor(soldier.status())));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.profile().shortLabel() + "  " + soldier.experienceXp() + " XP",
                x + w * 0.25f, y + 26f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                kit, x + w * 0.43f, y + 26f, ready ? GOOD : MUTED));
        if (!ready) return;

        MarineSquad target = roster.nextTransferTarget(soldier.id());
        float moveW = 138f;
        float armorW = 74f;
        float weaponW = 82f;
        addButton(x + w - moveW, y + 4f, moveW,
                target != null ? "Move → " + shortSquadName(target) : "No Vacancy",
                target != null ? () -> {
                    roster.transferSoldier(soldier.id(), target.id());
                    rebuild();
                } : null, target != null ? HEADER : MUTED);
        addButton(x + w - moveW - armorW - 8f, y + 4f, armorW, "Armor", () -> {
            roster.cycleArmor(soldier.id());
            rebuild();
        }, HEADER);
        addButton(x + w - moveW - armorW - weaponW - 16f, y + 4f, weaponW, "Weapon", () -> {
            roster.cyclePrimary(soldier.id());
            rebuild();
        }, HEADER);
    }

    private static String statusLabel(MarineSoldier soldier) {
        if (soldier.status() == MarineSoldierStatus.ACTIVE) return "RTD";
        if (soldier.status() == MarineSoldierStatus.WIA) {
            return "WIA · D" + (int) Math.ceil(soldier.unavailableUntilDay());
        }
        return soldier.status().name();
    }

    private static Color statusColor(MarineSoldierStatus status) {
        if (status == MarineSoldierStatus.WIA) return VALUE;
        if (status == MarineSoldierStatus.KIA) return BAD;
        return MUTED;
    }

    private static String shortSquadName(MarineSquad squad) {
        if (squad.reserve()) return "Reserve";
        String name = squad.name();
        return name.length() <= 10 ? name : name.substring(0, 10);
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

    private void addButton(float x, float y, float w, String text,
                           Runnable action, Color color) {
        widgets.add(new ButtonWidget(x, y, w, BUTTON_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, text,
                x + 8f, y + BUTTON_H - 6f, color));
    }

    @Override public void advance(float dt) { widgets.advance(dt); }
    @Override public void render(float alphaMult) { widgets.render(alphaMult); }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (renameField != null) renameField.routeKeys(events);
        widgets.processInput(events);
    }
}
