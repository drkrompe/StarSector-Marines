package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Ordered authored dialogue, with one projection shared by the dossier and deployment briefing. */
public final class MissionDialogue {
    public static final String HIDDEN_STYLE =
            "height: 0px; padding: 0px; gap: 0px; border-width: 0px; overflow: hidden;";
    private static final Pattern PREFIX = Pattern.compile("(?m)^([^\\r\\n:]{1,64}):[ \\t]*");

    private MissionDialogue() { }

    /** Stable identity is separate from the displayed name; an empty name is unvoiced prose. */
    public record Turn(String speakerKey, String speaker, String text) {
        public Turn {
            speakerKey = Objects.requireNonNull(speakerKey);
            speaker = Objects.requireNonNull(speaker);
            text = Objects.requireNonNull(text);
        }
    }

    public static Turn say(String speaker, String text) {
        return new Turn(identity(speaker), speaker, text);
    }

    public static Turn prose(String text) {
        return new Turn("", "", text);
    }

    /** Plain-text clients receive the same words and ordering as the structured transcript. */
    public static String transcript(List<Turn> turns) {
        return turns.stream().map(turn -> turn.speaker().isBlank() ? Strings.resolve(turn.text())
                : Strings.resolve(turn.speaker()) + ": " + Strings.resolve(turn.text())).collect(Collectors.joining("\n\n"));
    }

    public static List<Turn> turns(Mission mission) {
        if (mission == null) return List.of();
        // Missions restored from an older save can lack the new field entirely.
        if (mission.dialogue != null && !mission.dialogue.isEmpty()) return mission.dialogue;
        return legacyTurns(Strings.resolve(mission.flavor));
    }

    /** Recognize only established character prefixes at line starts, never arbitrary prose colons. */
    static List<Turn> legacyTurns(String flavor) {
        if (flavor == null || flavor.isBlank()) return List.of();
        var matcher = PREFIX.matcher(flavor);
        var turns = new ArrayList<Turn>();
        String speaker = "";
        int bodyStart = 0;
        boolean recognized = false;
        while (matcher.find()) {
            String candidate = matcher.group(1).strip();
            if (!knownSpeaker(candidate)) continue;
            String previous = flavor.substring(bodyStart, matcher.start()).strip();
            if (!previous.isEmpty()) turns.add(say(speaker, previous));
            speaker = candidate;
            bodyStart = matcher.end();
            recognized = true;
        }
        if (!recognized) return List.of();
        turns.add(say(speaker, flavor.substring(bodyStart).strip()));
        return List.copyOf(turns);
    }

    public static void put(Map<String, Object> props, String prefix, Mission mission) {
        // The caller can supply empty-state or referral prose instead of the mission's flavor.
        // Resolve that binding too when no recognized speaker produces structured turns.
        if (props.get(prefix + "Flavor") instanceof String flavor)
            props.put(prefix + "Flavor", Strings.resolve(flavor));
        List<Turn> turns = turns(mission);
        var rows = new ArrayList<Row>();
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            String id = prefix + "-dialogue-" + i;
            rows.add(new Row(id, Strings.resolve(turn.speaker()), colorStyle(turn.speakerKey()), Strings.resolve(turn.text()),
                    turn.speaker().isBlank() ? HIDDEN_STYLE : "", id + "-speaker",
                    id + "-speaker-label", id + "-text"));
        }
        props.put(prefix + "Dialogue", List.copyOf(rows));
        props.put(prefix + "DialogueStyle", rows.isEmpty() ? HIDDEN_STYLE : "");
        props.put(prefix + "FlavorStyle", rows.isEmpty() ? "" : HIDDEN_STYLE);
    }

    public record Row(String id, String speaker, String speakerStyle, String text,
                      String speakerVisibility, String speakerId, String speakerLabelId, String textId)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "speaker" -> speaker;
                case "speakerStyle" -> speakerStyle;
                case "text" -> text;
                case "speakerVisibility" -> speakerVisibility;
                case "speakerId" -> speakerId;
                case "speakerLabelId" -> speakerLabelId;
                case "textId" -> textId;
                default -> throw new IllegalArgumentException("Unknown dialogue property: " + name);
            };
        }
    }

    /** Affiliation is presented separately from the character's readable name. */
    public static String speakerName(String speaker) {
        return switch (identity(speaker)) {
            case "castle" -> Strings.resolve("@dialogueSpeakerCastle");
            case "vale" -> Strings.resolve("@dialogueSpeakerTamsinVale");
            case "mara" -> Strings.resolve("@dialogueSpeakerMaraVenn");
            case "rill" -> Strings.resolve("@dialogueSpeakerDrRill");
            case "iona" -> Strings.resolve("@dialogueSpeakerIonaSerr");
            default -> speaker == null ? "" : speaker.split("/", 2)[0].strip();
        };
    }
    /** Includes full radio labels such as CASTLE / COMPANY OPERATIONS. */
    public static String speakerStyle(String speaker) {
        return colorStyle(identity(speaker));
    }

    private static String identity(String speaker) {
        String name = speaker == null ? "" : speaker.split("/", 2)[0].strip().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "vale", "tamsin vale", "@dialoguespeakervale", "@dialoguespeakertamsinvale" -> "vale";
            case "mara", "mara venn", "@dialoguespeakermaravenn" -> "mara";
            case "rill", "dr. rill", "doctor rill", "@dialoguespeakerdrrill" -> "rill";
            case "iona", "iona serr", "@dialoguespeakerionaserr" -> "iona";
            case "@dialoguespeakercastle" -> "castle";
            default -> name;
        };
    }

    private static boolean knownSpeaker(String speaker) {
        return switch (identity(speaker)) {
            case "castle", "vale", "mara", "rill", "iona" -> true;
            default -> false;
        };
    }

    private static String colorStyle(String identity) {
        String color = switch (identity) {
            case "castle" -> "#e4bd78";
            case "vale" -> "#9fd2a5";
            case "mara" -> "#c8b2e8";
            case "rill" -> "#e7a79d";
            case "iona" -> "#b9cb8b";
            default -> "#cbd2d0";
        };
        return "color: " + color + ";";
    }
}
