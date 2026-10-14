package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live, render-free projection of the player side's air support pool for the MLX-authored air plate. The state
 * derivation and every string are produced by {@link #project}, a pure function of an {@link AirPoolView}; the signals
 * only carry its result to the markup.
 */
final class BattleAirOverlayModel {

    private static final String PLATE = "air-plate";
    private static final String STATUS_BASE = "air-status ";
    private static final float WINDOW_SECONDS = AirPoolView.RECENT_LOSS_SECONDS;

    private final MutableSignal<String> plateClasses;
    private final MutableSignal<String> status;
    private final MutableSignal<String> statusClasses;
    private final MutableSignal<List<MarkupPropertySource>> rows;

    BattleAirOverlayModel(Reactor reactor) {
        plateClasses = reactor.signal(PLATE);
        status = reactor.signal("");
        statusClasses = reactor.signal(STATUS_BASE + "air-status-ready");
        rows = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("airClasses", plateClasses);
        props.put("airStatus", status);
        props.put("airStatusClasses", statusClasses);
        props.put("airRows", rows);
        return props;
    }

    /** Pushes {@code view} to the markup and says how big the plate now is. */
    Presentation update(AirPoolView view) {
        Projection p = project(view);
        status.set(p.status());
        statusClasses.set(STATUS_BASE + p.statusClass());
        List<MarkupPropertySource> all = new ArrayList<>(p.kinds());
        all.addAll(p.lines());
        rows.set(List.copyOf(all));
        return new Presentation(p.visible(), p.kinds().size(), p.lines().size());
    }

    /** What the plate is made of for one reading of the pool. */
    record Projection(boolean visible, String status, String statusClass,
                      List<KindCard> kinds, List<LineCard> lines) {}

    /** Whether the plate shows at all, and how many rows it carries (its size follows from them). */
    record Presentation(boolean visible, int kindCount, int lineCount) {}

    /** The single pure derivation: state, wording and bar proportions from a pool reading. */
    static Projection project(AirPoolView view) {
        if (view == null || !view.hasPool()) {
            return new Projection(false, "", "air-status-ready", List.of(), List.of());
        }
        List<KindCard> cards = new ArrayList<>();
        int available = 0;
        int out = 0;
        int lost = 0;
        for (AirPoolView.KindRow row : view.kinds()) {
            available += row.available();
            out += row.outFlying();
            lost += row.lost();
            cards.add(kindCard(row));
        }
        int flying = view.sorties().size();
        String state;
        String statusClass;
        if (flying > 0) {
            state = flying + " IN FLIGHT";
            statusClass = "air-status-flight";
        } else if (view.callsGoingOut() > 0) {
            state = "CALLING";
            statusClass = "air-status-flight";
        } else if (available == 0 && out == 0) {
            state = "DEPLETED";
            statusClass = "air-status-depleted";
        } else {
            state = "READY";
            statusClass = "air-status-ready";
        }
        List<LineCard> lines = new ArrayList<>();
        String sorties = sortieLine(view);
        if (!sorties.isEmpty()) lines.add(new LineCard("battle-air-line-sorties", "air-caption-sorties", sorties));
        String losses = lossLine(lost, view.recentLosses());
        if (!losses.isEmpty()) lines.add(new LineCard("battle-air-line-losses", "air-caption-losses", losses));
        return new Projection(true, state, statusClass, List.copyOf(cards), List.copyOf(lines));
    }

    static KindCard kindCard(AirPoolView.KindRow row) {
        String id = "battle-air-" + row.kind().name().toLowerCase(Locale.ROOT);
        int cap = Math.max(1, row.cap());
        int ready = Math.max(0, Math.min(row.available(), cap));
        int flight = Math.max(0, Math.min(row.outFlying(), cap - ready));
        boolean below = row.available() + row.outFlying() < row.cap();
        String regen;
        String regenClass = "label air-kind-regen";
        if (!below) {
            regen = "";
        } else if (row.nextAirframeSeconds() >= 0f) {
            regen = "+1 IN " + clock(row.nextAirframeSeconds());
        } else {
            regen = "NO RESUPPLY";
            regenClass += " air-kind-regen-none";
        }
        return new KindCard(id, kindName(row.kind()), percentStyle(ready, cap), percentStyle(flight, cap),
                row.available() + "/" + row.cap(), regen, regenClass);
    }

    static String kindName(AirPoolKind kind) {
        return switch (kind) {
            case BOMBER -> "BOMBERS";
            case FIGHTER -> "FIGHTERS";
        };
    }

    private static String kindSingular(AirPoolKind kind) {
        return kind == AirPoolKind.BOMBER ? "BOMBER" : "FIGHTER";
    }

    /** "STRIKE BOMBER, COVER FIGHTER x2": what is airborne, grouped by kind and errand; empty when nothing is. */
    static String sortieLine(AirPoolView view) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AirPoolView.SortieRow s : view.sorties()) {
            counts.merge(s.mission().name() + " " + kindSingular(s.kind()), 1, Integer::sum);
        }
        if (counts.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (out.length() > 0) out.append(" / ");
            out.append(e.getKey());
            if (e.getValue() > 1) out.append(" x").append(e.getValue());
        }
        return out.toString();
    }

    /** "LOST 3 / 1 IN LAST 60S"; empty when nothing has been lost. */
    static String lossLine(int lostTotal, int recent) {
        if (lostTotal <= 0) return "";
        String line = "LOST " + lostTotal;
        if (recent > 0) line += " / " + recent + " IN LAST " + Math.round(WINDOW_SECONDS) + "S";
        return line;
    }

    /** "0:24" / "1:05"; whole seconds rounded up so the clock never reads 0:00 before the airframe arrives. */
    static String clock(float seconds) {
        int whole = (int) Math.ceil(Math.max(0f, seconds));
        return (whole / 60) + ":" + String.format(Locale.ROOT, "%02d", whole % 60);
    }

    private static String percentStyle(int part, int whole) {
        return "width: " + Math.round(100f * part / whole) + "%;";
    }

    /** One airframe kind's row. */
    record KindCard(String id, String name, String readyStyle, String flightStyle,
                    String count, String regen, String regenClasses) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "rowClasses" -> "air-kind";
                case "nameClasses" -> "label heading air-kind-name";
                case "trackClasses" -> "air-track";
                case "countClasses" -> "label heading air-kind-count";
                case "nameId" -> id + "-name";
                case "trackId" -> id + "-track";
                case "readyId" -> id + "-ready";
                case "flightId" -> id + "-flight";
                case "countId" -> id + "-count";
                case "regenId" -> id + "-regen";
                case "name" -> name;
                case "readyStyle" -> readyStyle;
                case "flightStyle" -> flightStyle;
                case "count" -> count;
                case "regen" -> regen;
                case "regenClasses" -> regenClasses;
                default -> throw new IllegalArgumentException("Unknown air-kind property: " + property);
            };
        }
    }

    /** A single caption line under the rows; shaped like a row so the markup repeats one element. */
    record LineCard(String id, String classes, String text) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "rowClasses" -> "air-caption-row";
                case "nameClasses" -> "label air-caption " + classes;
                case "name" -> text;
                case "trackClasses", "countClasses" -> "air-void";
                case "regenClasses" -> "air-void";
                case "nameId" -> id + "-text";
                case "trackId" -> id + "-track";
                case "readyId" -> id + "-ready";
                case "flightId" -> id + "-flight";
                case "countId" -> id + "-count";
                case "regenId" -> id + "-regen";
                case "readyStyle", "flightStyle" -> "width: 0%;";
                case "count", "regen" -> "";
                default -> throw new IllegalArgumentException("Unknown air-line property: " + property);
            };
        }
    }
}
