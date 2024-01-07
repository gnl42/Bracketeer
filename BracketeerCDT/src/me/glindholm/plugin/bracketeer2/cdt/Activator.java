/*******************************************************************************
 * Copyright (c) Gil Barash - chookapp@yahoo.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gil Barash - initial API and implementation
 *******************************************************************************/
package me.glindholm.plugin.bracketeer2.cdt;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    // The plug-in ID
    public static final String PLUGIN_ID = "me.glindholm.plugin.bracketeer2.Braketeer.CDT"; //$NON-NLS-1$

    public static final boolean DEBUG = false;

    private static BundleContext context;

    static BundleContext getContext() {
        return context;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        Activator.context = bundleContext;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(final BundleContext bundleContext) throws Exception {
        Activator.context = null;
    }

    /**
     * @param e
     */
    public static void log(final Throwable e) {
        Platform.getLog(context.getBundle()).log(getStatus(e));
    }

    public static void log(final Throwable e, final String str) {
        Platform.getLog(context.getBundle()).log(getStatus(e, str));
    }

    public static void log(final String message) {
        Platform.getLog(context.getBundle()).log(new Status(IStatus.ERROR, PLUGIN_ID, message));
    }

    public static void trace(final String message) {
        System.out.println(message);
    }

    /**
     * @param e
     * @return
     */
    public static IStatus getStatus(final Throwable e) {
        return new Status(IStatus.WARNING, PLUGIN_ID, e.getLocalizedMessage(), e);
    }

    public static IStatus getStatus(final Throwable e, final String str) {
        return new Status(IStatus.WARNING, PLUGIN_ID, e.getLocalizedMessage() + " " + str, e); //$NON-NLS-1$
    }

}
