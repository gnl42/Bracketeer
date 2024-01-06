package me.glindholm.plugin.bracketeer2.cdt;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "me.glindholm.plugin.bracketeer2.cdt.messages"; //$NON-NLS-1$
    public static String BracketeerCdtProcessor_ErrLength;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
