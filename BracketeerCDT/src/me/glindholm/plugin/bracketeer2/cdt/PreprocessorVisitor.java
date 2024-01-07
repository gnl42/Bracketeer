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

import java.util.Stack;

import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElseStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorEndifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfdefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfndefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorStatement;
import org.eclipse.jface.text.BadLocationException;

import me.glindholm.plugin.bracketeer2.common.Hint;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.IHintConfiguration;
import me.glindholm.plugin.bracketeer2.common.MutableBool;

public class PreprocessorVisitor {
    MutableBool _cancelProcessing;
    IBracketeerProcessingContainer _container;
    IHintConfiguration _hintConf;

    class CondInfo {
        public String _cond;
        public IASTFileLocation _fileLoc;

        public CondInfo(final String str, final IASTFileLocation fileLocation) {
            _cond = str;
            _fileLoc = fileLocation;
        }
    }

    Stack<CondInfo> _stack;

    public PreprocessorVisitor(final IBracketeerProcessingContainer container, final MutableBool cancelProcessing, final IHintConfiguration hintConf) {
        _cancelProcessing = cancelProcessing;
        _container = container;
        _hintConf = hintConf;

        _stack = new Stack<>();
    }

    private String stripEolBackslash(final char[] ch) {
        final String str = new String(ch);
        return stripEolBackslash(str);
    }

    private String stripEolBackslash(final String str) {
        return str.replaceAll("\\\\(\\s*[\r|\n])", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void visit(final IASTPreprocessorStatement[] stmts) throws BadLocationException {
        for (final IASTPreprocessorStatement stmt : stmts) {
            if (_cancelProcessing.get()) {
                break;
            }

            if (stmt instanceof IASTPreprocessorIfStatement) {
                final StringBuilder str = new StringBuilder();
                str.append("if( "); //$NON-NLS-1$
                str.append(stripEolBackslash(((IASTPreprocessorIfStatement) stmt).getCondition()));
                str.append(" )"); //$NON-NLS-1$
                _stack.push(new CondInfo(str.toString(), stmt.getFileLocation()));
            } else if (stmt instanceof IASTPreprocessorIfdefStatement) {
                final StringBuilder str = new StringBuilder();
                str.append("if_defined( "); //$NON-NLS-1$
                str.append(stripEolBackslash(((IASTPreprocessorIfdefStatement) stmt).getCondition()));
                str.append(" )"); //$NON-NLS-1$

                _stack.push(new CondInfo(str.toString(), stmt.getFileLocation()));
            } else if (stmt instanceof IASTPreprocessorIfndefStatement) {
                final StringBuilder str = new StringBuilder();
                str.append("if_not_defined( "); //$NON-NLS-1$
                str.append(stripEolBackslash(((IASTPreprocessorIfndefStatement) stmt).getCondition()));
                str.append(" )"); //$NON-NLS-1$

                _stack.push(new CondInfo(str.toString(), stmt.getFileLocation()));
            } else if (stmt instanceof IASTPreprocessorElifStatement) {
                CondInfo cond = null;
                if (!_stack.empty()) {
                    cond = _stack.pop();
                }

                if (cond != null) {
                    final IASTFileLocation location = stmt.getFileLocation();
                    final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
                    final int startLoc = cond._fileLoc.getNodeOffset();
                    _container.add(new Hint("preprocess", startLoc, endLoc, cond._cond)); //$NON-NLS-1$
                }

                final StringBuilder str = new StringBuilder();
                str.append("if( "); //$NON-NLS-1$
                str.append(stripEolBackslash(((IASTPreprocessorElifStatement) stmt).getCondition()));
                str.append(" )"); //$NON-NLS-1$

                _stack.push(new CondInfo(str.toString(), stmt.getFileLocation()));
            } else if (stmt instanceof IASTPreprocessorElseStatement) {
                CondInfo cond = null;
                if (!_stack.empty()) {
                    cond = _stack.pop();
                }

                final StringBuilder str = new StringBuilder();
                str.append("else_of_"); //$NON-NLS-1$

                if (cond != null) {
                    final IASTFileLocation location = stmt.getFileLocation();
                    final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
                    final int startLoc = cond._fileLoc.getNodeOffset();
                    _container.add(new Hint("preprocess", startLoc, endLoc, cond._cond)); //$NON-NLS-1$
                    str.append(cond._cond);
                }

                _stack.push(new CondInfo(str.toString(), stmt.getFileLocation()));
            } else if (stmt instanceof IASTPreprocessorEndifStatement) {
                if (_stack.empty()) {
                    continue;
                }

                final CondInfo cond = _stack.pop();

                final IASTFileLocation location = stmt.getFileLocation();
                final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
                final int startLoc = cond._fileLoc.getNodeOffset();
                _container.add(new Hint("preprocess", startLoc, endLoc, cond._cond)); //$NON-NLS-1$
            }
        }
    }

}
