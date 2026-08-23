package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.campaign.CompanyStanding;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.event.PlayerEventPresenter;
import com.dillon.starsectormarines.ops.event.PlayerEventTarget;
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
 * against them, and the company itself. Standing and the clocks are populated; the
 * roster column is reserved space (slice 4). See {@code c10-company-between-contracts.md}.
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
    /** Clock rows shown before the column is cut; the remainder is stated, never dropped. */
    private static final int CLOCK_LIMIT = 6;
    /** Days remaining at or below which a clock reads as urgent. */
    private static final int CLOCK_URGENT_DAYS = 2;
    /** Days remaining at or below which a clock reads as near. */
    private static final int CLOCK_SOON_DAYS = 7;
    private static final float RESPOND_W = 120f;
    private static final float RESPOND_H = 26f;

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
        buildClocks(left + columnW + COLUMN_GAP, columnTop);
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
        if (standing.finances.hasMonthlyReport) {
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
        } else {
            // Zeroes here would be a claim, not a gap. Say the report does not exist.
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("companyHqNoReport"), x, y, MUTED));
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

    /**
     * Everything with a deadline running against the company, soonest first.
     *
     * <p>Obligations only. A lapsing <em>offer</em> is not here: it costs nothing to
     * miss, and the only action it has is to fly somewhere else, which belongs to the
     * contract board. See {@code c11-the-contract-board.md}.
     */
    private void buildClocks(float x, float top) {
        float y = top;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("companyHqClocksHeader"), x, y, HEADER));
        y -= ROW + 4f;

        List<CompanyClocks.Entry> entries = CompanyClocks.current();
        if (entries.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("companyHqClocksEmpty"), x, y, MUTED));
            return;
        }

        int day = CampaignClock.day();
        int shown = Math.min(entries.size(), CLOCK_LIMIT);
        for (int i = 0; i < shown; i++) {
            y = buildClockRow(entries.get(i), day, x, y);
        }
        if (entries.size() > shown) {
            // Design commitment 9: the off-screen count is always stated.
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("companyHqClocksMore"),
                            entries.size() - shown), x, y, MUTED));
        }
    }

    /** @return the y the next row should start at */
    private float buildClockRow(CompanyClocks.Entry entry, int day,
                                float x, float top) {
        int days = entry.daysRemaining(day);
        float y = top;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                daysText(days) + "  ·  " + clockLabel(entry), x, y, clockColor(days)));
        y -= ROW - 4f;

        String where = clockWhere(entry);
        if (where != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, where, x, y, MUTED));
            y -= ROW - 4f;
        }
        if (entry.failsOnExpiry) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("companyHqClockTermFails"), x, y, BAD));
            y -= ROW - 4f;
        }
        if (entry.kind == CompanyClocks.Kind.RESPONSE && entry.notice != null) {
            float buttonY = y - RESPOND_H + 6f;
            widgets.add(new ButtonWidget(x, buttonY, RESPOND_W, RESPOND_H,
                    () -> onRespond(entry)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("companyHqClockRespond"),
                    x + 12f, buttonY + RESPOND_H - 7f, VALUE));
            y = buttonY - 4f;
        }
        return y - SECTION_GAP;
    }

    /**
     * The same hand-off the event popup's Deploy Now takes: queue the deployment, then
     * close this dialog so the presenter's quiet gate opens on a later frame.
     * {@code showInteractionDialog} refuses while this screen's own dialog is still up.
     */
    private void onRespond(CompanyClocks.Entry entry) {
        PlayerEventPresenter.requestDeployment(entry.notice);
        onClose();
    }

    private static String clockLabel(CompanyClocks.Entry entry) {
        if (entry.kind == CompanyClocks.Kind.TERM_ENDING) {
            return Strings.get("companyHqClockTerm");
        }
        return entry.notice != null
                && entry.notice.kind == PlayerEventNotice.Kind.CADRE_INCIDENT
                ? Strings.get("companyHqClockCadre")
                : Strings.get("companyHqClockGarrison");
    }

    /**
     * Market and employer, dropping whichever half the campaign cannot name rather than
     * rendering a registry slot or an empty separator.
     */
    private static String clockWhere(CompanyClocks.Entry entry) {
        String market = PlayerEventTarget.displayName(entry.marketId);
        String patron = entry.patronName;
        if (market != null && patron != null) {
            return MessageFormat.format(Strings.get("companyHqClockWhere"), market, patron);
        }
        return market != null ? market : patron;
    }

    private static String daysText(int days) {
        if (days <= 0) return Strings.get("companyHqClockDue");
        if (days == 1) return Strings.get("companyHqClockOneDay");
        return MessageFormat.format(Strings.get("companyHqClockDays"), days);
    }

    private static Color clockColor(int days) {
        if (days <= CLOCK_URGENT_DAYS) return BAD;
        if (days <= CLOCK_SOON_DAYS) return WARN;
        return VALUE;
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
