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
package me.glindholm.plugin.bracketeer2.core;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISourceProvider;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import me.glindholm.plugin.bracketeer2.Activator;
import me.glindholm.plugin.bracketeer2.commands.SourceProvider;

public class PartListener implements IWindowListener, IPartListener2 {
    private static PartListener sInstance = new PartListener();
    private final Collection<IWorkbenchWindow> fWindows = new HashSet<>();
    private final HashMap<IWorkbenchPart, BracketsHighlighter> _activeMap;
    private final ProcessorsRegistry _processorsRegistry;
    private final List<IActiveProcessorListener> m_listeners;

    PartListener() {
        _activeMap = new HashMap<>();
        m_listeners = new LinkedList<>();
        _processorsRegistry = new ProcessorsRegistry();
    }

    public static PartListener getInstance() {
        return sInstance;
    }

    public void install() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        if (workbench == null) {
            Activator.log(Messages.PartListener_ErrWorkbanch);
            return;
        }

        final ISourceProviderService srcService = workbench.getService(ISourceProviderService.class);
        final ISourceProvider src = srcService.getSourceProvider(SourceProvider.PLUGIN_NAME);
        m_listeners.add((IActiveProcessorListener) src);

        // listen for new windows
        workbench.addWindowListener(this);
        final IWorkbenchWindow[] wnds = workbench.getWorkbenchWindows();
        for (final IWorkbenchWindow window : wnds) {
            register(window);
        }
        // register open windows
        // IWorkbenchWindow ww= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        // if (ww != null) {
        // IWorkbenchPage activePage = ww.getActivePage();
        // if (activePage != null) {
        // IWorkbenchPartReference part= activePage.getActivePartReference();
        // if (part != null) {
        // partActivated(part);
        // }
        // }
        // }

    }

    public void uninstall() {
        for (final IWorkbenchWindow window : fWindows) {
            unregister(window);
        }
    }

    private void register(final IWorkbenchWindow wnd) {
        wnd.getPartService().addPartListener(this);
        fWindows.add(wnd);
        final IWorkbenchPage[] pages = wnd.getPages();
        for (final IWorkbenchPage page : pages) {
            final IEditorReference[] editorRefs = page.getEditorReferences();
            for (final IEditorReference editorRef : editorRefs) {
                partActivated(editorRef);
            }
        }

        final IWorkbenchPage page = wnd.getActivePage();
        if (page != null) {
            activated(page.getActivePartReference());
        }
    }

    /*
     * This function is expected to be closed when a window is closed (including when eclipse closes),
     * so the parts have already been closed. This is because I don't dispose the higlighers in this
     * function...
     */
    private void unregister(final IWorkbenchWindow wnd) {
        wnd.getPartService().removePartListener(this);
        fWindows.remove(wnd);
    }

    /* window events */

    @Override
    public void windowActivated(final IWorkbenchWindow window) {
    }

    @Override
    public void windowDeactivated(final IWorkbenchWindow window) {
    }

    @Override
    public void windowOpened(final IWorkbenchWindow window) {
        register(window);
    }

    @Override
    public void windowClosed(final IWorkbenchWindow window) {
        unregister(window);
    }

    /* part events */

    @Override
    public void partActivated(final IWorkbenchPartReference partRef) {
        created(partRef);
        activated(partRef);
    }

    @Override
    public void partBroughtToTop(final IWorkbenchPartReference partRef) {
    }

    @Override
    public void partClosed(final IWorkbenchPartReference partRef) {
        destroyed(partRef);
    }

    @Override
    public void partDeactivated(final IWorkbenchPartReference partRef) {
        deactivated(partRef);
    }

    @Override
    public void partOpened(final IWorkbenchPartReference partRef) {
        created(partRef);
    }

    @Override
    public void partHidden(final IWorkbenchPartReference partRef) {
    }

    @Override
    public void partVisible(final IWorkbenchPartReference partRef) {
    }

    @Override
    public void partInputChanged(final IWorkbenchPartReference partRef) {
        destroyed(partRef);
        created(partRef);
    }

    private void created(final IWorkbenchPartReference partRef) {
        final IWorkbenchPart part = partRef.getPart(false);
        try {
            if (!(part instanceof final IEditorPart editorPart)) {
                return;
            }

            final ITextViewer viewer = callGetSourceViewer(editorPart);
            if (viewer == null) {
                return;
            }

            hook(editorPart, viewer);
        } catch (final Exception err) {
            err.printStackTrace();
        }
    }

    private void destroyed(final IWorkbenchPartReference partRef) {
        final IWorkbenchPart part = partRef.getPart(false);
        try {
            if (!(part instanceof IEditorPart)) {
                return;
            }

            unhook(part);
        } catch (final Exception err) {
            err.printStackTrace();
        }
    }

    private void activated(final IWorkbenchPartReference partRef) {
        if (partRef == null) {
            deactivated(partRef);
            return;
        }
        final IWorkbenchPart part = partRef.getPart(false);
        if (part == null) {
            deactivated(partRef);
            return;
        }

        BracketsHighlighter bracketsHighlighter;
        synchronized (_activeMap) {
            bracketsHighlighter = _activeMap.get(part);
        }
        if (bracketsHighlighter == null) {
            deactivated(partRef);
            return;
        }

        final String name = bracketsHighlighter.getConfiguration().getName();

        if (Activator.DEBUG) {
            Activator.trace("PluginName: " + name); //$NON-NLS-1$
        }
        for (final IActiveProcessorListener listener : m_listeners) {
            listener.activeProcessorChanged(name);
        }
    }

    private void deactivated(final IWorkbenchPartReference partRef) {
        for (final IActiveProcessorListener listener : m_listeners) {
            listener.activeProcessorChanged(null);
        }
    }

    private void hook(final IEditorPart part, final ITextViewer textViewer) {
        if (textViewer == null) {
            return;
        }

        BracketsHighlighter oldBracketsHighlighter;
        synchronized (_activeMap) {
            oldBracketsHighlighter = _activeMap.get(part);
        }

        if (oldBracketsHighlighter != null) {
            if (oldBracketsHighlighter.getTextViewer() != textViewer) {
                Activator.log("Part viewer changed"); //$NON-NLS-1$
                unhook(part);
            } else {
                // this part is already registered fine...
                return;
            }
        }

        final IDocument doc = getPartDocument(part);
        if (doc == null) {
            return;
        }

        BracketeerProcessorInfo processor = null;
        try {
            processor = _processorsRegistry.findProcessorFor(part, doc);
        } catch (final RuntimeException e) {
            Activator.log(e);
            return;
        }

        if (processor == null) {
            return;
        }

        final BracketsHighlighter bracketsHighlighter = new BracketsHighlighter();
        bracketsHighlighter.Init(processor.getProcessor(), part, doc, textViewer, processor.getConfiguration());
        synchronized (_activeMap) {
            _activeMap.put(part, bracketsHighlighter);

            if (Activator.DEBUG) {
                Activator.trace(String.format("Parts active = %1$d", _activeMap.size())); //$NON-NLS-1$
            }
        }
    }

    private void unhook(final IWorkbenchPart part) {
        synchronized (_activeMap) {
            final BracketsHighlighter oldBracketsHighlighter = _activeMap.get(part);
            if (oldBracketsHighlighter == null) {
                return;
            }

            oldBracketsHighlighter.dispose();

            _activeMap.remove(part);

            if (Activator.DEBUG) {
                Activator.trace(String.format("Parts active = %1$d", _activeMap.size())); //$NON-NLS-1$
            }
        }
    }

    private static IDocument getPartDocument(final IEditorPart part) {
        final ITextEditor editor = part.getAdapter(ITextEditor.class);
        IDocument document = null;
        if (editor != null) {
            final IDocumentProvider provider = editor.getDocumentProvider();
            if (provider != null) {
                document = provider.getDocument(editor.getEditorInput());
            }
        }
        return document;
    }

    /**
     * Calls AbstractTextEditor.getSourceViewer() through reflection, as that method is normally
     * protected (for some ungodly reason).
     * 
     * @param AbstractTextEditor to run reflection on
     */
    private static ITextViewer callGetSourceViewer(final IEditorPart editor) {
        if (editor == null || !(editor instanceof AbstractTextEditor)) {
            return null;
        }

        try {
            final Method method = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
            method.setAccessible(true);

            return (ITextViewer) method.invoke(editor);
        } catch (final Exception e) {
            Activator.log(e);
        }

        /*
         * StyledText text = (StyledText) editor.getAdapter(Control.class);
         */

        return null;
    }

}
