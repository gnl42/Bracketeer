/*******************************************************************************
 * Copyright (c) Gil Barash - chookapp@yahoo.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gil Barash - initial API and implementation
 * 
 *******************************************************************************/
package me.glindholm.plugin.bracketeer2.core;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;

import me.glindholm.plugin.bracketeer2.Activator;
import me.glindholm.plugin.bracketeer2.extensionpoint.BracketeerProcessor;
import me.glindholm.plugin.bracketeer2.extensionpoint.IBracketeerProcessorsFactory;

public class ProcessorsRegistry {
    public static final String PROC_FACTORY_ID = "me.glindholm.plugin.bracketeer2.processorsFactory"; //$NON-NLS-1$
    public static final String SUPPORTED_BRACKETS_ATTR = "supportedBrackets"; //$NON-NLS-1$

    private final List<IBracketeerProcessorsFactory> _processorFactories;
    private final List<ProcessorConfiguration> _processorConfigurations;

    public ProcessorsRegistry() {
        _processorFactories = new LinkedList<>();
        _processorConfigurations = new LinkedList<>();

        final IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(PROC_FACTORY_ID);

        for (final IConfigurationElement element : config) {
            try {
                final Object o = element.createExecutableExtension("class"); //$NON-NLS-1$
                _processorFactories.add((IBracketeerProcessorsFactory) o);

                _processorConfigurations.add(new ProcessorConfiguration(element));

            } catch (final Exception e) {
                Activator.log(e);
            }
        }
    }

//	public static List<String> getPluginNames()
//	{
//	    List<String> ret = new LinkedList<String>();
//
//	    IConfigurationElement[] config = Platform.getExtensionRegistry()
//                .getConfigurationElementsFor(PROC_FACTORY_ID);
//
//        for (IConfigurationElement element : config) {
//            try {
//                String name = element.getAttribute("name");
//                ret.add(name);
//            } catch (Exception e) {
//                Activator.log(e);
//            }
//        }
//
//        return ret;
//	}

    public BracketeerProcessorInfo findProcessorFor(final IEditorPart part, final IDocument doc) {
        BracketeerProcessorInfo processorInfoFound = null;

        for (int i = 0; i < _processorFactories.size(); i++) {
            final BracketeerProcessor processor = _processorFactories.get(i).createProcessorFor(part, doc);
            if (processor != null) {
                if (processorInfoFound != null) {
                    throw new RuntimeException(Messages.ProcessorsRegistry_ErrProcExists);
                }

                processorInfoFound = new BracketeerProcessorInfo(processor, _processorConfigurations.get(i));
            }
        }
        return processorInfoFound;
    }
}
