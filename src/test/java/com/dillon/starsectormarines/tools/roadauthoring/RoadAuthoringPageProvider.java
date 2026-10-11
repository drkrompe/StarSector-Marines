package com.dillon.starsectormarines.tools.roadauthoring;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
public final class RoadAuthoringPageProvider implements AuthoringPageProvider {
    public String id() { return "roads"; }
    public String label() { return "Roads"; }
    public AuthoringPage create(AuthoringPageContext context) throws Exception { return new RoadAuthoringPage(context); }
}
