package com.dillon.starsectormarines.tools.damageauthoring;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
public final class DamageAuthoringPageProvider implements AuthoringPageProvider {
    public String id() { return "map-damage"; }
    public String label() { return "Map damage"; }
    public AuthoringPage create(AuthoringPageContext context) throws Exception { return new DamageAuthoringPage(context); }
}
