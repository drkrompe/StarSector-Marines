package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Turns the dome service's absorbed-round records into the impact list a dome
 * is drawn with: the newest {@link ShieldDomeDraw#MAX_IMPACTS} that belong to
 * this dome, each placed in the dome's own screen-space frame, aged, and given
 * an intensity from how much flux the round cost.
 *
 * <p>Pure arithmetic, no GL.
 */
final class ShieldImpactField {

    /** A round costing this share of the dome's capacity draws at full intensity. */
    static final float FULL_INTENSITY_SHARE = 0.20f;
    /** The faintest an impact draws, so the smallest round is still seen. */
    static final float MIN_INTENSITY = 0.30f;

    private ShieldImpactField() {}

    /**
     * Fills {@code draw}'s impact array from {@code hits} (oldest first, as the
     * service publishes them). Hits for other domes and hits past their life are
     * skipped; when more than the bound remain the oldest are dropped.
     */
    static void gather(ShieldDomeDraw draw, ShieldDomeService.DomeView dome,
                       List<ShieldDomeService.HitView> hits, BattleCamera camera) {
        int count = 0;
        for (int i = hits.size() - 1; i >= 0 && count < ShieldDomeDraw.MAX_IMPACTS; i--) {
            ShieldDomeService.HitView hit = hits.get(i);
            if (hit.domeId() != dome.entityId()) continue;
            if (hit.ageSeconds() >= ShieldDomeService.IMPACT_SECONDS) continue;
            int at = count * 4;
            draw.impacts[at] = (camera.cellToScreenX(hit.x()) - draw.sx) / draw.radiusPx;
            draw.impacts[at + 1] = (camera.cellToScreenY(hit.y()) - draw.sy) / draw.radiusPx;
            draw.impacts[at + 2] = hit.ageSeconds();
            draw.impacts[at + 3] = intensity(hit.cost(), dome.capacity());
            count++;
        }
        draw.impactCount = count;
    }

    /**
     * How strongly a round that cost {@code cost} flux of a pool holding
     * {@code capacity} is drawn. A square root of its share, so a round twice the
     * size reads clearly bigger without a heavy one flattening every light one.
     */
    static float intensity(float cost, float capacity) {
        if (capacity <= 0f || cost <= 0f) return MIN_INTENSITY;
        float share = cost / (capacity * FULL_INTENSITY_SHARE);
        float scaled = MIN_INTENSITY + (1f - MIN_INTENSITY) * (float) Math.sqrt(Math.min(1f, share));
        return Math.max(MIN_INTENSITY, Math.min(1f, scaled));
    }
}
