package com.dillon.starsectormarines.tools.authoring;

/** Service-provider entry point for an authoring domain contributed to the workbench. */
public interface AuthoringPageProvider {

    String id();

    String label();

    AuthoringPage create(AuthoringPageContext context) throws Exception;
}
