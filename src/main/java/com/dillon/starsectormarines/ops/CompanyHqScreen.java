package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CompanyStanding;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * The company's between-contracts home — reached from the campaign map through
 * {@link CompanyViewAbility}, not from a planet.
 *
 * <p>Three columns, ordered by what the player came for: standing, the clocks running
 * against them, and the company itself. Standing is populated; the other two are
 * reserved space (slices 3 and 4). See {@code c10-company-between-contracts.md}.
 *
 * <p>Unlike every other {@link Screen} here, this one runs with a null
 * {@code ctx.planet} and no market. It must therefore read nothing off the context but
 * navigation and the officer's own voice, and must never route anywhere that assumes a
 * market, a mission, or a battle.
 */
public final class CompanyHqScreen implements Screen {

    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xE0, 0xE8, 0xFF);
    private static final Color MUTED = new Color(0x8A, 0x9A, 0xB4);
    private static final Color GOOD = new Color(0xC8, 0xFF, 0xE0);
    private static final Color WARN = new Color(0xFF, 0xD0, 0x70);
    private static final Color BAD = new Color(0xFF, 0x80, 0x80);

    private static final float PAD = 24f;
    private static final float ROW = 30f;
    private static final float SECTION_GAP = 16f;
    private static final float COLUMN_GAP = 24f;
    private static final float BTN_H = 32f;
    private static final float BTN_W = 180f;
    private static final float OFFICER_H = 26f;
    private static final int COLUMNS = 3;
    /** Employers listed before the column is cut; the rest are counted, never dropped. */
    private static final int EMPLOYER_LIMIT = 5;

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private Runnable dismissDialog;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.dismissDialog = dismissDialog;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null) return;

        float left = position.getX() + PAD;
        float width = position.getWidth() - 2f * PAD;
        float top = position.getY() + position.getHeight() - PAD;

        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                Strings.get("companyHqHeader"), left, top, HEADER));

        // The comms officer's own line, exactly as mission select renders it. With no
        // client selected it falls to the overview flavor, which is what this screen
        // wants — and it needs no planet.
        float officerY = top - 34f;
        widgets.add(new OfficerHeaderWidget(ctx, left, officerY - OFFICER_H,
                width, OFFICER_H));

        float columnTop = officerY - OFFICER_H - SECTION_GAP;
        float columnW = (width - (COLUMNS - 1) * COLUMN_GAP) / COLUMNS;

        buildStanding(left, columnTop, columnW);
        buildPlaceholder(left + columnW + COLUMN_GAP, columnTop,
                Strings.get("companyHqClocksHeader"),
                Strings.get("companyHqDeadlinesPending"));
        buildPlaceholder(left + 2f * (columnW + COLUMN_GAP), columnTop,
                Strings.get("companyHqRosterHeader"),
                Strings.get("companyHqRosterPending"));

        float buttonY = position.getY() + PAD;
        widgets.add(new ButtonWidget(left, buttonY, BTN_W, BTN_H, this::onClose));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                left + 14f, buttonY + BTN_H - 8f, VALUE));
    }

    private void buildStanding(float x, float top, float width) {
        CompanyStanding standing = CompanyStanding.current(EMPLOYER_LIMIT);
        NumberFormat credits = NumberFormat.getIntegerInstance();

        float y = top;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("companyHqStandingHeader"), x, y, HEADER));
        y -= ROW + 4f;

        // Runway leads: it is the number that governs every other decision here.
        float runway = standing.finances.runwayMonths();
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD, runwayText(runway),
                x, y, runwayColor(runway)));
        y -= ROW + 2f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("companyHqRunwayCaption"), x, y, MUTED));
        y -= ROW + SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqOnHand"),
                        credits.format((long) standing.finances.credits)), x, y, VALUE));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqUpkeep"),
                        credits.format((long) standing.finances.upkeepLastMonth)),
                x, y, VALUE));
        y -= ROW;
        float net = standing.finances.netLastMonth;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqNet"),
                        signed(credits, net)), x, y, net < 0f ? BAD : VALUE));
        y -= ROW;
        if (standing.finances.debt > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("companyHqDebt"),
                            credits.format(standing.finances.debt)), x, y, BAD));
            y -= ROW;
        }
        y -= SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                standing.stationingContracts == 0
                        ? Strings.get("companyHqNoRetainers")
                        : MessageFormat.format(Strings.get("companyHqRetainers"),
                                credits.format(standing.retainerPerMonth),
                                standing.stationingContracts),
                x, y, standing.stationingContracts == 0 ? MUTED : GOOD));
        y -= ROW + SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("companyHqPersonnelHeader"), x, y, HEADER));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqStrength"),
                        standing.strength, standing.available), x, y,
                standing.available == 0 ? BAD : VALUE));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqUnavailable"),
                        standing.stationed, standing.wounded,
                        Math.max(0, standing.unavailable - standing.stationed
                                - standing.wounded)),
                x, y, MUTED));
        y -= ROW + SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("companyHqReputationHeader"), x, y, HEADER));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("companyHqMrb"),
                        standing.finances.mrbRep), x, y,
                standing.finances.mrbRep < 0 ? BAD : VALUE));
        y -= ROW;
        buildEmployers(standing.employers, x, y, width);
    }

    private void buildEmployers(List<CompanyStanding.Employer> employers,
                                float x, float top, float width) {
        if (employers.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("companyHqNoEmployers"), x, top, MUTED));
            return;
        }
        float y = top;
        for (CompanyStanding.Employer employer : employers) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("companyHqEmployer"),
                            employer.name, signedInt(employer.reputation),
                            employer.contractsCompleted, employer.contractsFailed),
                    x, y, employer.reputation < 0 ? BAD : VALUE));
            y -= ROW;
        }
    }

    private void buildPlaceholder(float x, float top, String header, String body) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, header, x, top, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, body, x, top - ROW - 4f, MUTED));
    }

    /**
     * A missing denominator is not infinite runway. The first month of a campaign has
     * no monthly report yet, so say so rather than printing a number that would read as
     * good news.
     */
    private static String runwayText(float months) {
        if (months < 0f) return Strings.get("companyHqRunwayUnknown");
        return MessageFormat.format(Strings.get("companyHqRunway"),
                String.format("%.1f", months));
    }

    private static Color runwayColor(float months) {
        if (months < 0f) return MUTED;
        if (months < OfficerMoodReader.DESPERATE_RUNWAY_MONTHS) return BAD;
        if (months < OfficerMoodReader.SEASONED_RUNWAY_MONTHS) return WARN;
        return GOOD;
    }

    private static String signed(NumberFormat format, float value) {
        String magnitude = format.format((long) Math.abs(value));
        return (value < 0f ? "-" : "+") + magnitude;
    }

    private static String signedInt(int value) {
        return (value < 0 ? "" : "+") + value;
    }

    private void onClose() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void render(float alphaMult) {
        widgets.render(alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }
}
