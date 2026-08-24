package com.dillon.starsectormarines.tools.turretauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;

/** Discoverable root-project contribution for turret and emplacement authoring. */
public final class TurretAuthoringPageProvider implements AuthoringPageProvider {

    @Override public String id() { return "turrets"; }

    @Override public String label() { return "Turrets"; }

    @Override
    public AuthoringPage create(AuthoringPageContext context) throws Exception {
        return new TurretAuthoringPage(context);
    }
}
