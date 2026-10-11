package com.dillon.starsectormarines.tools.forestauthoring;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
public final class ForestAuthoringPageProvider implements AuthoringPageProvider {
    public String id() { return "forests"; }
    public String label() { return "Forests"; }
    public AuthoringPage create(AuthoringPageContext context) throws Exception { return new ForestAuthoringPage(context); }
}
