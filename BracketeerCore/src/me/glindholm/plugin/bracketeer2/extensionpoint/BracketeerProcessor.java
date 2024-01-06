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
package me.glindholm.plugin.bracketeer2.extensionpoint;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;

import me.glindholm.plugin.bracketeer2.Activator;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.IHintConfiguration;
import me.glindholm.plugin.bracketeer2.common.MutableBool;

public abstract class BracketeerProcessor implements IDocumentListener {

    protected MutableBool _cancelProcessing;
    protected IDocument _doc;
    protected IHintConfiguration _hintConf;

    protected BracketeerProcessor(final IDocument doc) {
        _doc = doc;
        _cancelProcessing = new MutableBool(false);
    }

    public void setHintConf(final IHintConfiguration conf) {
        _hintConf = conf;
    }

    public boolean process(final IBracketeerProcessingContainer container) {
        _cancelProcessing.set(false);

        _doc.addDocumentListener(this);

        processDocument(_doc, container);
        postProcess(_doc, container);

        _doc.removeDocumentListener(this);

        return !_cancelProcessing.get();
    }

    private void postProcess(final IDocument doc, final IBracketeerProcessingContainer container) {
    }

    @Override
    public void documentAboutToBeChanged(final DocumentEvent event) {
        if (Activator.DEBUG) {
            Activator.trace("doc about to be changed"); //$NON-NLS-1$
        }
        _cancelProcessing.set(true);
    }

    @Override
    public void documentChanged(final DocumentEvent event) {
        // nothing...
    }

    /**
     * 
     * @param doc       The document to be processed
     * @param container The contains to add the brackets to
     */
    protected abstract void processDocument(IDocument doc, IBracketeerProcessingContainer container);
}
