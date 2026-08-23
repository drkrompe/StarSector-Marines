package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;

import java.util.ArrayList;
import java.util.List;

/** Owns deterministic grenade flight and faction-neutral temporary smoke opacity. */
public final class SmokeFieldService {

    private final NavigationGrid grid;
    private final ArrayList<SmokeThrow> throwsInFlight = new ArrayList<>();
    private final ArrayList<SmokeField> fields = new ArrayList<>();
    private volatile List<SmokeThrowView> throwSnapshot = List.of();
    private volatile List<SmokeFieldView> fieldSnapshot = List.of();
    private long nextId = 1L;

    public SmokeFieldService(NavigationGrid grid) {
        this.grid = grid;
    }

    public synchronized void launch(long carrierId, Faction faction, float fromX, float fromY,
                                    float toX, float toY, SmokeGrenadeSpec spec) {
        throwsInFlight.add(new SmokeThrow(nextId++, carrierId, faction,
                fromX, fromY, toX, toY, spec.flightSeconds(), spec.flightSeconds(),
                spec.arcHeight(), spec.cloudRadius(), spec.cloudDuration()));
        publishSnapshots();
    }

    public synchronized void tick(float dt) {
        for (int i = fields.size() - 1; i >= 0; i--) {
            SmokeField field = fields.get(i);
            field.remaining -= dt;
            if (field.remaining > 0f) continue;
            for (int idx : field.occupiedCells) grid.removeTransientOpacityAt(idx);
            fields.remove(i);
        }
        for (int i = throwsInFlight.size() - 1; i >= 0; i--) {
            SmokeThrow grenade = throwsInFlight.get(i);
            grenade.remaining -= dt;
            if (grenade.remaining > 0f) continue;
            deploy(grenade);
            throwsInFlight.remove(i);
        }
        publishSnapshots();
    }

    public List<SmokeThrowView> throwsInFlight() { return throwSnapshot; }
    public List<SmokeFieldView> activeFields() { return fieldSnapshot; }

    public boolean hasSmokeNear(float x, float y, float radius) {
        for (SmokeFieldView field : fieldSnapshot) {
            float dx = field.x() - x;
            float dy = field.y() - y;
            float reach = radius + field.radius();
            if (dx * dx + dy * dy <= reach * reach) return true;
        }
        return false;
    }

    public boolean hasThrowFrom(long carrierId) {
        for (SmokeThrowView grenade : throwSnapshot) {
            if (grenade.carrierId() == carrierId) return true;
        }
        return false;
    }

    private void deploy(SmokeThrow grenade) {
        int minX = Math.max(0, (int) Math.floor(grenade.toX - grenade.cloudRadius));
        int maxX = Math.min(grid.getWidth() - 1,
                (int) Math.floor(grenade.toX + grenade.cloudRadius));
        int minY = Math.max(0, (int) Math.floor(grenade.toY - grenade.cloudRadius));
        int maxY = Math.min(grid.getHeight() - 1,
                (int) Math.floor(grenade.toY + grenade.cloudRadius));
        float radiusSq = grenade.cloudRadius * grenade.cloudRadius;
        int[] scratch = new int[(maxX - minX + 1) * (maxY - minY + 1)];
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float dx = x + 0.5f - grenade.toX;
                float dy = y + 0.5f - grenade.toY;
                if (dx * dx + dy * dy > radiusSq) continue;
                int idx = grid.index(x, y);
                scratch[count++] = idx;
                grid.addTransientOpacityAt(idx);
            }
        }
        int[] occupied = new int[count];
        System.arraycopy(scratch, 0, occupied, 0, count);
        fields.add(new SmokeField(grenade.id, grenade.faction, grenade.toX, grenade.toY,
                grenade.cloudRadius, grenade.cloudDuration, grenade.cloudDuration, occupied));
    }

    private void publishSnapshots() {
        ArrayList<SmokeThrowView> visibleThrows = new ArrayList<>(throwsInFlight.size());
        for (SmokeThrow grenade : throwsInFlight) {
            float progress = 1f - Math.max(0f, grenade.remaining) / grenade.totalFlight;
            float x = grenade.fromX + (grenade.toX - grenade.fromX) * progress;
            float y = grenade.fromY + (grenade.toY - grenade.fromY) * progress;
            float z = 4f * grenade.arcHeight * progress * (1f - progress);
            visibleThrows.add(new SmokeThrowView(grenade.id, grenade.carrierId,
                    grenade.faction, x, y, z, progress));
        }
        ArrayList<SmokeFieldView> visibleFields = new ArrayList<>(fields.size());
        for (SmokeField field : fields) {
            visibleFields.add(new SmokeFieldView(field.id, field.faction, field.x, field.y,
                    field.radius, field.remaining, field.totalDuration));
        }
        throwSnapshot = List.copyOf(visibleThrows);
        fieldSnapshot = List.copyOf(visibleFields);
    }

    public record SmokeThrowView(long id, long carrierId, Faction sourceFaction,
                                 float x, float y, float z, float progress) {}

    public record SmokeFieldView(long id, Faction sourceFaction,
                                 float x, float y, float radius,
                                 float remaining, float totalDuration) {}

    private static final class SmokeThrow {
        final long id;
        final long carrierId;
        final Faction faction;
        final float fromX;
        final float fromY;
        final float toX;
        final float toY;
        final float totalFlight;
        final float arcHeight;
        final float cloudRadius;
        final float cloudDuration;
        float remaining;

        SmokeThrow(long id, long carrierId, Faction faction,
                   float fromX, float fromY, float toX, float toY,
                   float totalFlight, float remaining, float arcHeight,
                   float cloudRadius, float cloudDuration) {
            this.id = id;
            this.carrierId = carrierId;
            this.faction = faction;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.totalFlight = totalFlight;
            this.remaining = remaining;
            this.arcHeight = arcHeight;
            this.cloudRadius = cloudRadius;
            this.cloudDuration = cloudDuration;
        }
    }

    private static final class SmokeField {
        final long id;
        final Faction faction;
        final float x;
        final float y;
        final float radius;
        final float totalDuration;
        final int[] occupiedCells;
        float remaining;

        SmokeField(long id, Faction faction, float x, float y, float radius,
                   float remaining, float totalDuration, int[] occupiedCells) {
            this.id = id;
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.remaining = remaining;
            this.totalDuration = totalDuration;
            this.occupiedCells = occupiedCells;
        }
    }
}
