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
package me.glindholm.plugin.bracketeer2;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import me.glindholm.plugin.bracketeer2.core.PartListener;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "me.glindholm.plugin.bracketeer2.Braketeer"; //$NON-NLS-1$

    public static final boolean DEBUG = false;

    // The shared instance
    private static Activator plugin;

    /**
     * The constructor
     */
    public Activator() {
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        Display.getDefault().asyncExec(() -> PartListener.getInstance().install());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        try {
            PartListener.getInstance().uninstall();
            plugin = null;
        } finally {
            super.stop(context);
        }
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static Activator getDefault() {
        return plugin;
    }

    /**
     * @param e
     */
    public static void log(final Throwable e) {
        getDefault().getLog().log(getStatus(e));
    }

    public static void log(final String message) {
        getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message));
    }

    public static void trace(final String message) {
        getDefault().getLog().log(new Status(IStatus.INFO, PLUGIN_ID, message));
    }

    /**
     * @param e
     * @return
     */
    public static IStatus getStatus(final Throwable e) {
        return new Status(IStatus.WARNING, PLUGIN_ID, e.getLocalizedMessage(), e);
    }

}
