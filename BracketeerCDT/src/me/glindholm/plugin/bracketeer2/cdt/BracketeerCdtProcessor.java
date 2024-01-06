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
package me.glindholm.plugin.bracketeer2.cdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElseStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorEndifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfdefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfndefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ITranslationUnit;
//import org.eclipse.cdt.internal.core.model.ASTCache;
//import org.eclipse.cdt.internal.ui.editor.ASTProvider;
import org.eclipse.cdt.ui.CDTUITools;
import org.eclipse.cdt.ui.text.ICPartitions;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.ui.IEditorPart;

import me.glindholm.plugin.bracketeer2.cdt.core.internals.CPairMatcher;
import me.glindholm.plugin.bracketeer2.common.BracketsPair;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.SingleBracket;
import me.glindholm.plugin.bracketeer2.common.Utils;
import me.glindholm.plugin.bracketeer2.extensionpoint.BracketeerProcessor;

public class BracketeerCdtProcessor extends BracketeerProcessor {
    protected final static char[] BRACKETS = { '{', '}', '(', ')', '[', ']', '<', '>' };

    /*
     * Lonely brackets is different from BRACKETS because matching an angular bracket is heuristic. So I
     * don't want to have false positives
     */
    protected final static String LONELY_BRACKETS = "()[]{}"; //$NON-NLS-1$

    private final CPairMatcher _matcher;

    private final ICElement _celem;
    private IDocument _doc;

    private IASTTranslationUnit _ast;

//    @SuppressWarnings("restriction")
//    class AstRunner implements ASTCache.ASTRunnable
//    {
//        IBracketeerProcessingContainer _container;
//
//        public AstRunner(IBracketeerProcessingContainer container)
//        {
//            _container = container;
//        }
//
//        @Override
//        public IStatus runOnAST(ILanguage lang, IASTTranslationUnit ast) throws CoreException
//        {
//            ClosingBracketHintVisitor visitor = new ClosingBracketHintVisitor(_container,
//                                                                              _cancelProcessing,
//                                                                              _hintConf);
//            ast.accept(visitor);
//            return Status.OK_STATUS;
//        }
//
//    }

    public BracketeerCdtProcessor(final IEditorPart part, final IDocument doc) {
        super(doc);

        _celem = CDTUITools.getEditorInputCElement(part.getEditorInput());
        _matcher = new CPairMatcher(BRACKETS);
        _doc = doc;
    }

    private BracketsPair getMatchingPair(int offset) throws BadLocationException {
        final IRegion region = _matcher.match(_doc, offset);
        if (region == null) {
            return null;
        }

        if (region.getLength() < 1) {
            throw new RuntimeException(Messages.BracketeerCdtProcessor_ErrLength);
        }

        final boolean isAnchorOpening = ICharacterPairMatcher.LEFT == _matcher.getAnchor();
        int targetOffset = isAnchorOpening ? region.getOffset() + region.getLength() : region.getOffset() + 1;

        offset--;
        targetOffset--;

        if (isAnchorOpening) {
            return new BracketsPair(offset, _doc.getChar(offset), targetOffset, _doc.getChar(targetOffset));
        } else {
            return new BracketsPair(targetOffset, _doc.getChar(targetOffset), offset, _doc.getChar(offset));
        }

    }

    private SingleBracket getLonelyBracket(final int offset, final List<Position> inactiveCode) throws BadLocationException {
        final int charOffset = offset - 1;
        char prevChar;

        prevChar = _doc.getChar(Math.max(charOffset, 0));
        if (LONELY_BRACKETS.indexOf(prevChar) == -1) {
            return null;
        }
        final String partition = TextUtilities.getContentType(_doc, ICPartitions.C_PARTITIONING, charOffset, false);
        for (final String partName : ICPartitions.ALL_CPARTITIONS) {
            if (partName.equals(partition)) {
                return null;
            }
            for (final Position pos : inactiveCode) {
                if (pos.includes(offset)) {
                    return null;
                }
            }
        }

        return new SingleBracket(charOffset, Utils.isOpenningBracket(prevChar), prevChar);
    }

    @Override
    protected void processDocument(final IDocument doc, final IBracketeerProcessingContainer container) {
        if (Activator.DEBUG) {
            Activator.trace("starting process..."); //$NON-NLS-1$
        }

        try {
            _doc = doc;
            updateAst();
            processBrackets(container);
            processAst(container);
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }

        if (Activator.DEBUG) {
            Activator.trace("process ended (" + _cancelProcessing + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private void processBrackets(final IBracketeerProcessingContainer container) throws BadLocationException {
        final List<Position> inactiveCode = collectInactiveCodePositions(_ast);
        _matcher.updateInactiveCodePositions(inactiveCode);
        for (int i = 1; i < _doc.getLength() + 1; i++) {
            if (_cancelProcessing.get()) {
                break;
            }

            final BracketsPair pair = getMatchingPair(i);
            if (pair != null) {
                if (Activator.DEBUG) {
                    Activator.trace("matching pair added: " + pair.toString()); //$NON-NLS-1$
                }
                container.add(pair);
                continue;
            }

            final SingleBracket single = getLonelyBracket(i, inactiveCode);
            if (single != null) {
                container.add(single);
            }
        }
    }

//    @SuppressWarnings("restriction")
    private void processAst(final IBracketeerProcessingContainer container) throws BadLocationException {
        if (_ast == null) {
            return;
        }

//        AstRunner runner = new AstRunner(container);
//        ASTProvider provider = CUIPlugin.getDefault().getASTProvider();
//
//        if( provider.runOnAST(_celem, ASTProvider.WAIT_ACTIVE_ONLY, null, runner) == Status.OK_STATUS)
//            return;

        final ClosingBracketHintVisitor visitor = new ClosingBracketHintVisitor(container, _cancelProcessing, _hintConf);

        _ast.accept(visitor);
        // runner.runOnAST(null, ast);

        final IASTPreprocessorStatement[] stmts = _ast.getAllPreprocessorStatements();
        final PreprocessorVisitor preVisotor = new PreprocessorVisitor(container, _cancelProcessing, _hintConf);
        preVisotor.visit(stmts);
    }

    private void updateAst() {
        try {
            _ast = null;
            if (_celem == null) {
                return;
            }

            final ITranslationUnit tu = (ITranslationUnit) _celem;
            IASTTranslationUnit ast;
            ast = tu.getAST(null, ITranslationUnit.AST_SKIP_ALL_HEADERS | ITranslationUnit.AST_CONFIGURE_USING_SOURCE_CONTEXT
                    | ITranslationUnit.AST_SKIP_TRIVIAL_EXPRESSIONS_IN_AGGREGATE_INITIALIZERS | ITranslationUnit.AST_PARSE_INACTIVE_CODE);

            _ast = ast;
        } catch (final CoreException e) {
            Activator.log(e);
        }
    }

    /**
     * copied from org.eclipse.cdt.internal.ui.editor.InactiveCodeHighlighting.
     * 
     * Collect source positions of preprocessor-hidden branches in the given translation unit.
     * 
     * @param translationUnit the {@link IASTTranslationUnit}, may be <code>null</code>
     * @return a {@link List} of {@link IRegion}s
     */
    private List<Position> collectInactiveCodePositions(final IASTTranslationUnit translationUnit) {
        if (translationUnit == null) {
            return Collections.emptyList();
        }
        final String fileName = translationUnit.getFilePath();
        if (fileName == null) {
            return Collections.emptyList();
        }
        final List<Position> positions = new ArrayList<>();
        int inactiveCodeStart = -1;
        boolean inInactiveCode = false;
        final Stack<Boolean> inactiveCodeStack = new Stack<>();

        final IASTPreprocessorStatement[] preprocStmts = translationUnit.getAllPreprocessorStatements();

        for (final IASTPreprocessorStatement statement : preprocStmts) {
            final IASTFileLocation floc = statement.getFileLocation();
            if (floc == null || !fileName.equals(floc.getFileName())) {
                // preprocessor directive is from a different file
                continue;
            }
            if (statement instanceof final IASTPreprocessorIfStatement ifStmt) {
                inactiveCodeStack.push(inInactiveCode);
                if (!ifStmt.taken()) {
                    if (!inInactiveCode) {
                        inactiveCodeStart = floc.getNodeOffset();
                        inInactiveCode = true;
                    }
                }
            } else if (statement instanceof final IASTPreprocessorIfdefStatement ifdefStmt) {
                inactiveCodeStack.push(inInactiveCode);
                if (!ifdefStmt.taken()) {
                    if (!inInactiveCode) {
                        inactiveCodeStart = floc.getNodeOffset();
                        inInactiveCode = true;
                    }
                }
            } else if (statement instanceof final IASTPreprocessorIfndefStatement ifndefStmt) {
                inactiveCodeStack.push(inInactiveCode);
                if (!ifndefStmt.taken()) {
                    if (!inInactiveCode) {
                        inactiveCodeStart = floc.getNodeOffset();
                        inInactiveCode = true;
                    }
                }
            } else if (statement instanceof final IASTPreprocessorElseStatement elseStmt) {
                if (!elseStmt.taken() && !inInactiveCode) {
                    inactiveCodeStart = floc.getNodeOffset();
                    inInactiveCode = true;
                } else if (elseStmt.taken() && inInactiveCode) {
                    final int inactiveCodeEnd = floc.getNodeOffset();
                    positions.add(createInactiveCodePosition(inactiveCodeStart, inactiveCodeEnd, false));
                    inInactiveCode = false;
                }
            } else if (statement instanceof final IASTPreprocessorElifStatement elifStmt) {
                if (!elifStmt.taken() && !inInactiveCode) {
                    inactiveCodeStart = floc.getNodeOffset();
                    inInactiveCode = true;
                } else if (elifStmt.taken() && inInactiveCode) {
                    final int inactiveCodeEnd = floc.getNodeOffset();
                    positions.add(createInactiveCodePosition(inactiveCodeStart, inactiveCodeEnd, false));
                    inInactiveCode = false;
                }
            } else if (statement instanceof IASTPreprocessorEndifStatement) {
                try {
                    final boolean wasInInactiveCode = inactiveCodeStack.pop();
                    if (inInactiveCode && !wasInInactiveCode) {
                        final int inactiveCodeEnd = floc.getNodeOffset() + floc.getNodeLength();
                        positions.add(createInactiveCodePosition(inactiveCodeStart, inactiveCodeEnd, true));
                    }
                    inInactiveCode = wasInInactiveCode;
                } catch (final EmptyStackException e) {
                }
            }
        }
        if (inInactiveCode) {
            // handle unterminated #if - http://bugs.eclipse.org/255018
            final int inactiveCodeEnd = _doc.getLength();
            positions.add(createInactiveCodePosition(inactiveCodeStart, inactiveCodeEnd, true));
        }
        return positions;
    }

    /**
     * Create a highlight position aligned to start at a line offset. The region's start is decreased to
     * the line offset, and the end offset decreased to the line start if <code>inclusive</code> is
     * <code>false</code>.
     * 
     * @param startOffset the start offset of the region to align
     * @param endOffset   the (exclusive) end offset of the region to align
     * @param inclusive   whether the last line should be included or not
     * @param key         the highlight key
     * @return a position aligned for background highlighting
     */
    private Position createInactiveCodePosition(int startOffset, int endOffset, final boolean inclusive) {
        final IDocument document = _doc;
        try {
            if (document != null) {
                final int start = document.getLineOfOffset(startOffset);
                final int end = document.getLineOfOffset(endOffset);
                startOffset = document.getLineOffset(start);
                if (!inclusive) {
                    endOffset = document.getLineOffset(end);
                }
            }
        } catch (final BadLocationException x) {
            // concurrent modification?
        }
        return new Position(startOffset, endOffset - startOffset);
    }

}
