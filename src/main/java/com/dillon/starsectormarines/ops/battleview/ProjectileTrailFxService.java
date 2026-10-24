package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Authored particle trails sampled on the shot clock, independently of how often a host paints. */
public final class ProjectileTrailFxService {
    private static final float INTERVAL = .05f;
    private final Map<ShotEvent, Cursor> cursors = new IdentityHashMap<>();
    private final Set<ShotEvent> present = Collections.newSetFromMap(new IdentityHashMap<>());

    public record Emission(ShotEvent shot, float x, float y, float bearing) { }
    private static final class Cursor {
        float age, x, y, next;
        Cursor(ShotEvent shot) { x = shot.fromX; y = shot.visualFromY(); }
    }

    public void tick(List<ShotEvent> shots, ImpactFx backend) {
        sample(shots, e -> WeaponFxRuntime.spawnTrail(backend, e.shot, e.x, e.y, e.bearing),
                e -> WeaponFxRuntime.spawnIgnition(backend, e.shot, e.x, e.y, e.bearing));
    }

    /** Pure positions emitted to an injected sink; old samples remain owned by the particle backend. */
    void sample(List<ShotEvent> shots, Consumer<Emission> sink) {
        sample(shots, sink, ignored -> { });
    }

    void sample(List<ShotEvent> shots, Consumer<Emission> sink, Consumer<Emission> ignitionSink) {
        present.clear();
        for (ShotEvent shot : shots) {
            if (shot.flightResolved || shot.lifetime <= 0f
                    || shot.guidedFlight != null && shot.guidedFlight.remaining <= 0f) continue;
            ShotFx fx = ShotFx.of(shot);
            if (!(fx.body() instanceof ShotFx.Sprite sprite)) continue;
            present.add(shot);
            Cursor cursor = cursors.computeIfAbsent(shot, Cursor::new);
            float age = Math.max(0f, shot.lifetimeMax - shot.lifetime);
            if (age <= cursor.age + 1e-6f) continue; // A paused or repeatedly painted frame emits nothing.
            var pose = ShotRenderService.spriteFlight(shot, fx);
            boolean softLaunch = shot.guidedFlight == null && fx.softLaunch() != null;
            if (softLaunch) {
                // Keep the cursor current during the coast: ignition must not backfill smoke to the tube.
                cursor.next = Math.max(cursor.next, fx.softLaunch().seconds());
                if (!ShotRenderService.motorLit(shot, fx)) {
                    cursor.age = age; cursor.x = pose.x(); cursor.y = pose.y();
                    continue;
                }
                if (!shot.ignitionShown) {
                    shot.ignitionShown = true;
                    ignitionSink.accept(new Emission(shot, pose.x(), pose.y(), pose.bearing()));
                }
            }
            if (WeaponFxRuntime.definition(shot).layers(FxSlot.TRAIL).isEmpty()) {
                cursor.age = age; cursor.x = pose.x(); cursor.y = pose.y();
                continue;
            }
            // Powered exhaust starts at the body tail. An unpowered charge's authored glow belongs at its center.
            float tail = shot.guidedFlight != null && !shot.guidedFlight.powered() ? 0f : sprite.visualCells() * .5f;
            float radians = (float) Math.toRadians(pose.bearing());
            while (cursor.next <= age + 1e-6f) {
                float t = Math.max(0f, Math.min(1f, (cursor.next - cursor.age) / (age - cursor.age)));
                float bodyX = cursor.x + (pose.x() - cursor.x) * t;
                float bodyY = cursor.y + (pose.y() - cursor.y) * t;
                // Never behind the launch point: a round just out of the muzzle has no tail behind the shooter yet.
                float back = Math.min(tail, (float) Math.hypot(bodyX - shot.fromX, bodyY - shot.visualFromY()));
                float x = bodyX + (float) Math.sin(radians) * back;
                float y = bodyY - (float) Math.cos(radians) * back;
                sink.accept(new Emission(shot, x, y, pose.bearing()));
                cursor.next += INTERVAL;
            }
            cursor.age = age; cursor.x = pose.x(); cursor.y = pose.y();
        }
        cursors.keySet().removeIf(shot -> !present.contains(shot));
        present.clear();
    }
}
