package com.dillon.starsectormarines.battle.command.objective;

import java.util.List;

/** Public, read-only mission picture shared by the briefing, HUD and map. No command authority. */
public record MissionObjectiveSnapshot(List<Item> objectives, List<SquadActivity> squads) {
    public static final MissionObjectiveSnapshot EMPTY = new MissionObjectiveSnapshot(List.of(), List.of());

    public enum State { ACTIVE, COMPLETE, FAILED }

    /** Progress describes the named current step, never a synthetic overall mission percentage. */
    public record Item(String id, String name, State state, String phase, String steps,
                       String instruction, String progressText, float progress, String blocker,
                       List<Marker> markers, String focusMarkerId) {
        public Item { markers = List.copyOf(markers); }
        public Marker focus() {
            return markers.stream().filter(m -> m.id().equals(focusMarkerId)).findFirst()
                    .orElse(markers.isEmpty() ? null : markers.get(0));
        }
    }

    /** A named place or tracked friendly payload; a beacon does not imply a capture radius. */
    public record Cell(int x, int y) { }
    public record Marker(String id, String name, int x, int y, String status, State state, List<Cell> footprint, float radius) {
        public Marker { footprint = List.copyOf(footprint); }
        public Marker(String id, String name, int x, int y, String status, State state, List<Cell> footprint) {
            this(id, name, x, y, status, state, footprint, 0);
        }
        public Marker(String id, String name, int x, int y, String status, State state) {
            this(id, name, x, y, status, state, List.of(), 0);
        }
    }
    public record SquadActivity(int squadId, String name, String task, String status) { }

    public MissionObjectiveSnapshot {
        objectives = List.copyOf(objectives);
        squads = List.copyOf(squads);
    }
}
