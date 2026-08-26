package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Render-side lifecycle and fixed-budget selection for S3 ground lights.
 * Combat events author short-lived lights in cell coordinates; the composite
 * consumes at most {@link #MAX_SHADER_LIGHTS} nearest visible entries.
 */
public final class GroundLightService {

    public static final int MAX_SHADER_LIGHTS = 8;
    private static final int MAX_LIVE_LIGHTS = 128;
    private static final float MERGE_DISTANCE_SQ = 0.16f;

    private static final Color WARM_MUZZLE = new Color(0xFF, 0xD0, 0x88);
    private static final Color KINETIC_IMPACT = new Color(0xFF, 0xC0, 0x68);
    private static final Color HE_IMPACT = new Color(0xFF, 0x78, 0x32);
    private static final Color FIRE = new Color(0xFF, 0x68, 0x28);

    private final List<Light> live = new ArrayList<>();
    /** Live moving lights keyed by the exact render-side shot record they follow. */
    private final Map<ShotEvent, Light> boltLights = new IdentityHashMap<>();
    /** Reused identity set for pruning ended bolt shots without per-frame allocation. */
    private final Set<ShotEvent> activeBolts = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Light[] selected = new Light[MAX_SHADER_LIGHTS];
    private final float[] selectedScores = new float[MAX_SHADER_LIGHTS];

    /** Adds a weapon-colored flash at the shot origin. */
    public void spawnMuzzle(ShotEvent shot) {
        Color color = muzzleColor(shot);
        WeaponFxDef fx = WeaponFxRuntime.definition(shot);
        float radius = fx.hasHeavyImpact() ? 5.2f
                : fx.hasExplosiveImpact() ? 4.2f
                : fx.hasKineticImpact() ? 3.2f : 2.4f;
        float intensity = fx.hasHeavyImpact() ? 0.92f
                : fx.hasExplosiveImpact() ? 0.72f
                : fx.hasKineticImpact() ? 0.52f : 0.34f;
        spawn(shot.fromX, shot.fromY, 0.9f, radius, color, intensity, 0.13f);
    }

    /** Adds a definition-scaled flash at a resolved impact point. */
    public void spawnImpact(WeaponFxDef fx, float x, float y) {
        if (fx.hasHeavyImpact()) {
            spawn(x, y, 1.5f, 7.2f, HE_IMPACT, 1.20f, 0.62f);
        } else if (fx.hasExplosiveImpact()) {
            spawn(x, y, 1.2f, 5.5f, HE_IMPACT, 0.95f, 0.48f);
        } else if (fx.hasKineticImpact()) {
            spawn(x, y, 0.8f, 3.2f, KINETIC_IMPACT, 0.48f, 0.22f);
        } else {
            spawn(x, y, 0.6f, 1.9f, WARM_MUZZLE, 0.22f, 0.13f);
        }
    }

    /** Large orbital/support impact light. */
    public void spawnHeavyImpact(float x, float y, float radiusCells) {
        spawn(x, y, 1.8f, Math.max(7f, radiusCells * 1.6f),
                HE_IMPACT, 1.35f, 0.85f);
    }

    /** Brief warm halo paired with each authored ambient flame burst. */
    public void spawnFire(float x, float y, float radiusCells) {
        spawn(x, y, 0.8f, Math.max(2.5f, radiusCells * 4f),
                FIRE, 0.42f, 0.70f);
    }

    /**
     * Keeps one weapon-colored ground light under every active traveling bolt.
     * The light follows the same pose used by {@link ShotRenderService}, so it
     * freezes with the shot when paused and disappears on arrival instead of
     * leaving a render-frame trail behind it.
     */
    public void syncBoltLights(List<ShotEvent> activeShots) {
        activeBolts.clear();
        for (ShotEvent shot : activeShots) {
            if (!(ShotFx.of(shot).body() instanceof ShotFx.Bolt bolt)) continue;
            activeBolts.add(shot);
            ShotRenderService.BoltPose pose = ShotRenderService.boltPose(shot, bolt);
            if (pose.visibleLength() <= 1e-6f || pose.fadeIn() <= 0f) continue;

            Light light = boltLights.get(shot);
            if (light == null || !live.contains(light)) {
                if (live.size() >= MAX_LIVE_LIGHTS) evictOldest();
                Color color = bolt.color() != null ? bolt.color() : Color.WHITE;
                light = new Light(0f, 0f, 0f, 0f, color, 0f, 1f, true);
                boltLights.put(shot, light);
                live.add(light);
            }

            light.x = (pose.headX() + pose.tailX()) * 0.5f;
            light.y = (pose.headY() + pose.tailY()) * 0.5f;
            float centerZ = (pose.headZ() + pose.tailZ()) * 0.5f;
            light.height = Math.max(0.2f, 0.9f + centerZ);
            light.radius = 1.8f + Math.max(0f, bolt.lengthCells()) * 0.4f;
            light.intensity = 0.30f * pose.fadeIn();
            light.lifetime = 1f;
            light.remaining = 1f;
        }

        boltLights.entrySet().removeIf(entry -> {
            if (activeBolts.contains(entry.getKey())) return false;
            live.remove(entry.getValue());
            return true;
        });
    }

    /** Ages lights on the same scaled clock as their source FX. */
    public void advance(float dt) {
        if (dt <= 0f) return;
        for (int i = live.size() - 1; i >= 0; i--) {
            Light light = live.get(i);
            if (light.tracked) continue;
            light.remaining -= dt;
            if (light.remaining <= 0f) live.remove(i);
        }
    }

    /** Clears battle-local state on detach. */
    public void clear() {
        live.clear();
        boltLights.clear();
        activeBolts.clear();
        Arrays.fill(selected, null);
    }

    /**
     * Selects nearest lights whose radius intersects the current camera view.
     * Returns the count; {@link #selected(int)} exposes the stable references.
     */
    int selectNearest(BattleCamera camera) {
        Arrays.fill(selected, null);
        Arrays.fill(selectedScores, Float.POSITIVE_INFINITY);
        float cellPx = Math.max(0.001f, camera.cellPxSize());
        float halfW = camera.vpW() * 0.5f / cellPx;
        float halfH = camera.vpH() * 0.5f / cellPx;
        float minX = camera.panCellX() - halfW;
        float maxX = camera.panCellX() + halfW;
        float minY = camera.panCellY() - halfH;
        float maxY = camera.panCellY() + halfH;

        int count = 0;
        for (Light light : live) {
            if (light.x + light.radius < minX || light.x - light.radius > maxX
                    || light.y + light.radius < minY || light.y - light.radius > maxY) continue;
            float dx = light.x - camera.panCellX();
            float dy = light.y - camera.panCellY();
            float score = dx * dx + dy * dy;
            if (count == MAX_SHADER_LIGHTS
                    && score >= selectedScores[MAX_SHADER_LIGHTS - 1]) continue;
            int slot = Math.min(count, MAX_SHADER_LIGHTS - 1);
            while (slot > 0 && score < selectedScores[slot - 1]) {
                if (slot < MAX_SHADER_LIGHTS) {
                    selected[slot] = selected[slot - 1];
                    selectedScores[slot] = selectedScores[slot - 1];
                }
                slot--;
            }
            if (slot < MAX_SHADER_LIGHTS) {
                selected[slot] = light;
                selectedScores[slot] = score;
                if (count < MAX_SHADER_LIGHTS) count++;
            }
        }
        return count;
    }

    Light selected(int index) {
        return selected[index];
    }

    int liveCount() {
        return live.size();
    }

    Light boltLight(ShotEvent shot) {
        return boltLights.get(shot);
    }

    private void spawn(float x, float y, float height, float radius, Color color,
                       float intensity, float lifetime) {
        for (int i = live.size() - 1; i >= 0; i--) {
            Light existing = live.get(i);
            if (existing.tracked) continue;
            float dx = existing.x - x;
            float dy = existing.y - y;
            if (dx * dx + dy * dy > MERGE_DISTANCE_SQ || !existing.color.equals(color)) continue;
            existing.height = Math.max(existing.height, height);
            existing.radius = Math.max(existing.radius, radius);
            existing.intensity = Math.max(existing.intensity, intensity);
            existing.lifetime = Math.max(existing.lifetime, lifetime);
            existing.remaining = Math.max(existing.remaining, lifetime);
            return;
        }
        if (live.size() >= MAX_LIVE_LIGHTS) evictOldest();
        live.add(new Light(x, y, height, radius, color, intensity, lifetime, false));
    }

    private void evictOldest() {
        if (live.isEmpty()) return;
        Light removed = live.remove(0);
        boltLights.entrySet().removeIf(entry -> entry.getValue() == removed);
    }

    private static Color muzzleColor(ShotEvent shot) {
        ShotFx fx = ShotFx.of(shot);
        if (fx.body() instanceof ShotFx.Bolt bolt && bolt.color() != null) return bolt.color();
        if (fx.body() instanceof ShotFx.Tracer tracer) {
            return tracer.color() != null ? tracer.color() : ShotFx.defaultTracerColor(shot.shooterFaction);
        }
        if (shot.primaryWeaponDef != null && shot.primaryWeaponDef.tracerColor != null) {
            return shot.primaryWeaponDef.tracerColor;
        }
        if (shot.mechWeapon != null && shot.mechWeapon.tracerColor() != null) {
            return shot.mechWeapon.tracerColor();
        }
        return WARM_MUZZLE;
    }

    static final class Light {
        float x;
        float y;
        final Color color;
        float height;
        float radius;
        float intensity;
        float remaining;
        float lifetime;
        final boolean tracked;

        Light(float x, float y, float height, float radius, Color color,
              float intensity, float lifetime) {
            this(x, y, height, radius, color, intensity, lifetime, false);
        }

        Light(float x, float y, float height, float radius, Color color,
              float intensity, float lifetime, boolean tracked) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.radius = radius;
            this.color = color;
            this.intensity = intensity;
            this.remaining = lifetime;
            this.lifetime = lifetime;
            this.tracked = tracked;
        }

        float effectiveIntensity() {
            float fadeWindow = Math.max(0.001f, lifetime * 0.45f);
            float fade = Math.min(1f, remaining / fadeWindow);
            return intensity * fade;
        }
    }
}
