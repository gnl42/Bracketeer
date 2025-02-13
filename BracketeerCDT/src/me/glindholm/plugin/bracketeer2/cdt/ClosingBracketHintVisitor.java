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

import java.util.EmptyStackException;
import java.util.Stack;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTBreakStatement;
import org.eclipse.cdt.core.dom.ast.IASTCaseStatement;
import org.eclipse.cdt.core.dom.ast.IASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTContinueStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDefaultStatement;
import org.eclipse.cdt.core.dom.ast.IASTDoStatement;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTForStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTSwitchStatement;
import org.eclipse.cdt.core.dom.ast.IASTWhileStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTIfStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateId;
import org.eclipse.jface.text.BadLocationException;

import me.glindholm.plugin.bracketeer2.common.BracketsPair;
import me.glindholm.plugin.bracketeer2.common.Hint;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.IHintConfiguration;
import me.glindholm.plugin.bracketeer2.common.MutableBool;

public class ClosingBracketHintVisitor extends ASTVisitor {

    class ScopeInfo {
        public String _str;
        public int _offset;
        public IASTStatement _statement;

        public ScopeInfo(final String str, final int offset, final IASTStatement statement) {
            _str = str;
            _offset = offset;
            _statement = statement;
        }
    }

    public class ScopeTraceException extends Exception {
        private static final long serialVersionUID = 6297837237586982280L;

        public ScopeTraceException(final String message) {
            super(message);
        }
    }

    MutableBool _cancelProcessing;
    IBracketeerProcessingContainer _container;
    IHintConfiguration _hintConf;
    Stack<ScopeInfo> _scopeStack;

    public ClosingBracketHintVisitor(final IBracketeerProcessingContainer container, final MutableBool cancelProcessing, final IHintConfiguration hintConf) {
        _cancelProcessing = cancelProcessing;
        _container = container;
        _hintConf = hintConf;

        shouldVisitStatements = true;
        shouldVisitDeclarations = true;
        shouldVisitExpressions = true; // not really visiting expressions, see bug 370637.

        _scopeStack = new Stack<>();
    }

    @Override
    public int leave(final IASTStatement statement) {
        try {
            if (statement instanceof IASTSwitchStatement || statement instanceof IASTForStatement || statement instanceof IASTWhileStatement
                    || statement instanceof IASTDoStatement) {
                ScopeInfo scope;
                if (statement instanceof IASTSwitchStatement) {
                    scope = _scopeStack.peek();
                    if (scope._statement instanceof IASTCaseStatement || scope._statement instanceof IASTDefaultStatement) {
                        _scopeStack.pop();
                    }
                }

                scope = _scopeStack.pop();
                if (!scope._statement.getClass().equals(statement.getClass())) {
                    if (Activator.DEBUG) {
                        Activator.log("Lost track of scope. Expected:" + scope._statement + //$NON-NLS-1$
                                " but was:" + statement); //$NON-NLS-1$
                    }
                }
            }
        } catch (final EmptyStackException e) {
            if (Activator.DEBUG) {
                Activator.log(e);
            }
        }

        return super.leave(statement);
    }

    @Override
    public int visit(final IASTStatement statement) {
        try {
            if (statement instanceof IASTIfStatement) {
                visitIf((IASTIfStatement) statement);
            }

            if (statement instanceof IASTSwitchStatement) {
                visitSwitch((IASTSwitchStatement) statement);
            }

            if (statement instanceof IASTForStatement) {
                visitFor((IASTForStatement) statement);
            }

            if (statement instanceof IASTWhileStatement) {
                visitWhile((IASTWhileStatement) statement);
            }

            if (statement instanceof IASTDoStatement) {
                visitDo((IASTDoStatement) statement);
            }

            if (statement instanceof IASTCaseStatement || statement instanceof IASTDefaultStatement) {
                visitCase(statement);
            }

            if (statement instanceof IASTBreakStatement || statement instanceof IASTContinueStatement) {
                visitBreak(statement);
            }

        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        } catch (final Exception e) {
            if (!(e instanceof ScopeTraceException) && !(e instanceof EmptyStackException) || Activator.DEBUG) {
                Activator.log(e);
            }
        }
        return shouldContinue();
    }

    private void visitBreak(final IASTStatement statement) throws ScopeTraceException, BadLocationException {
        if (_scopeStack.isEmpty()) {
            throw new ScopeTraceException("break without scope: " + statement); //$NON-NLS-1$
        }

        final ScopeInfo scope = _scopeStack.peek();

        String hintType;
        if (scope._statement instanceof IASTForStatement) {
            hintType = "break-for"; //$NON-NLS-1$
        } else if (scope._statement instanceof IASTWhileStatement) {
            hintType = "break-while"; //$NON-NLS-1$
        } else if (scope._statement instanceof IASTDoStatement) {
            hintType = "break-do"; //$NON-NLS-1$
        } else if (scope._statement instanceof IASTCaseStatement || scope._statement instanceof IASTDefaultStatement) {
            hintType = "break-case"; //$NON-NLS-1$
        } else {
            throw new ScopeTraceException("Unexpect scope (" + scope._statement + ") on break/continue:" + statement); //$NON-NLS-1$ //$NON-NLS-2$
        }

        final int endLoc = statement.getFileLocation().getNodeOffset() + statement.getFileLocation().getNodeLength() - 1;
        _container.add(new Hint(hintType, scope._offset, endLoc, scope._str));

    }

    private void visitCase(final IASTStatement statement) throws ScopeTraceException {
        /* TODO: specific params: don't show the switch part (only the case argument) */

        ScopeInfo scope = _scopeStack.peek();
        if (!(scope._statement instanceof IASTSwitchStatement)) {
            if (!(scope._statement instanceof IASTCaseStatement) && !(scope._statement instanceof IASTDefaultStatement)) {
                throw new ScopeTraceException("Lost track of stack (in case), found:" + scope._statement); //$NON-NLS-1$
            }

            _scopeStack.pop();
            scope = _scopeStack.peek();
        }

        if (!(scope._statement instanceof IASTSwitchStatement)) {
            throw new ScopeTraceException("Lost track of stack (in case2), found:" + scope._statement); //$NON-NLS-1$
        }

        String hint = ""; //$NON-NLS-1$
        if (statement instanceof IASTCaseStatement) {
            final IASTExpression cond = ((IASTCaseStatement) statement).getExpression();
            if (cond != null) {
                hint = cond.getRawSignature();
            }
            hint = "case: " + hint; //$NON-NLS-1$
        } else // default
        {
            hint = "default"; //$NON-NLS-1$
        }

        final int startLoc = statement.getFileLocation().getNodeOffset();
        _scopeStack.push(new ScopeInfo(scope._str + " - " + hint, startLoc, statement)); //$NON-NLS-1$
    }

    private void visitDo(final IASTDoStatement statement) {
        final IASTExpression cond = statement.getCondition();
        String hint = ""; //$NON-NLS-1$
        if (cond != null) {
            hint = cond.getRawSignature();
        }
        hint = "do_while( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        final int startLoc = statement.getFileLocation().getNodeOffset();
        _scopeStack.push(new ScopeInfo(hint, startLoc, statement));
    }

    private void visitIf(final IASTIfStatement statement) throws BadLocationException {
        /*
         * TODO: specific params: don't show the if hint if there's an "else if" after it (by checking if
         * the elseClause is an instance of ifstatment)
         */

        String hint = ""; //$NON-NLS-1$
        if (statement.getConditionExpression() != null) {
            hint = statement.getConditionExpression().getRawSignature();
        } else if (statement instanceof ICPPASTIfStatement && ((ICPPASTIfStatement) statement).getConditionDeclaration() != null) {
            hint = ((ICPPASTIfStatement) statement).getConditionDeclaration().getRawSignature();
        }

        final IASTStatement thenClause = statement.getThenClause();
        final IASTStatement elseClause = statement.getElseClause();

        boolean showIfHint = elseClause == null;
        int endLoc = -1;
        if (!showIfHint) {
            if (elseClause.getFileLocation().getStartingLineNumber() != thenClause.getFileLocation().getEndingLineNumber()) {
                showIfHint = true;
            }

            // if the else looks like this "} else {", then show the hint on the "{"
            if (!showIfHint && !(elseClause instanceof IASTIfStatement)) {
                endLoc = elseClause.getFileLocation().getNodeOffset();
                showIfHint = true;
            }
        }

        if (showIfHint && !(thenClause instanceof IASTCompoundStatement)) {
            showIfHint = false;
        }

        if (showIfHint) {
            final IASTFileLocation location = thenClause.getFileLocation();
            if (endLoc == -1) {
                endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
            }
            final int startLoc = statement.getFileLocation().getNodeOffset();
            _container.add(new Hint("if", startLoc, endLoc, "if( " + hint + " )")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        if (elseClause != null && !(elseClause instanceof IASTIfStatement) && elseClause instanceof IASTCompoundStatement) {
            final IASTFileLocation location = elseClause.getFileLocation();
            endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
            final int startLoc = location.getNodeOffset();
            _container.add(new Hint("if", startLoc, endLoc, "else_of_if( " + hint + " )")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
    }

    private void visitSwitch(final IASTSwitchStatement statement) throws BadLocationException {
        String hint = statement.getControllerExpression().getRawSignature();
        final IASTFileLocation location = statement.getBody().getFileLocation();
        final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
        final int startLoc = statement.getFileLocation().getNodeOffset();
        hint = "switch( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        _container.add(new Hint("switch", startLoc, endLoc, hint)); //$NON-NLS-1$
        _scopeStack.push(new ScopeInfo(hint, startLoc, statement));
    }

    private void visitFor(final IASTForStatement statement) throws BadLocationException {
        /* TODO: specific params: show also initializer && increment expressions */

        final IASTExpression cond = statement.getConditionExpression();
        String hint = ""; //$NON-NLS-1$
        if (cond != null) {
            hint = cond.getRawSignature();
        }
        hint = "for( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        final int startLoc = statement.getFileLocation().getNodeOffset();
        _scopeStack.push(new ScopeInfo(hint, startLoc, statement));

        final IASTStatement body = statement.getBody();
        if (body instanceof IASTCompoundStatement) {
            final IASTFileLocation location = body.getFileLocation();
            final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
            _container.add(new Hint("for", startLoc, endLoc, hint)); //$NON-NLS-1$
        }
    }

    private void visitWhile(final IASTWhileStatement statement) throws BadLocationException {
        final IASTExpression cond = statement.getCondition();
        String hint = ""; //$NON-NLS-1$
        if (cond != null) {
            hint = cond.getRawSignature();
        }
        hint = "while( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        final int startLoc = statement.getFileLocation().getNodeOffset();
        _scopeStack.push(new ScopeInfo(hint, startLoc, statement));

        final IASTStatement body = statement.getBody();
        if (body instanceof IASTCompoundStatement) {
            final IASTFileLocation location = body.getFileLocation();

            final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
            _container.add(new Hint("while", startLoc, endLoc, hint)); //$NON-NLS-1$
        }
    }

    @Override
    public int visit(final IASTDeclaration declaration) {
        try {
            if (declaration instanceof IASTFunctionDefinition) {
                visitFunc((IASTFunctionDefinition) declaration);
            }

            if (declaration instanceof IASTSimpleDeclaration) {
                visitType((IASTSimpleDeclaration) declaration);
            }
        } catch (final Exception e) {
            Activator.log(e);
        }
        return shouldContinue();
    }

    private void visitFunc(final IASTFunctionDefinition declaration) throws BadLocationException {
        final IASTStatement body = declaration.getBody();
        if (!(body instanceof IASTCompoundStatement)) {
            return;
        }

        // starting a function empties the stack... (which should already be empty on good flow)
        _scopeStack.clear();

        final IASTFileLocation location = body.getFileLocation();
        final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;

        final IASTFunctionDeclarator declerator = declaration.getDeclarator();
        final int startLoc = declerator.getFileLocation().getNodeOffset();

        final StringBuilder hint = new StringBuilder();
        hint.append(declerator.getName().getRawSignature());
        /* TODO: specific params: exclude function parameters (show only the name) */
        hint.append("( "); //$NON-NLS-1$
        final IASTNode[] decChildren = declerator.getChildren();
        boolean firstParam = true;
        for (final IASTNode node : decChildren) {
            if (node instanceof final IASTParameterDeclaration param) {
                if (firstParam) {
                    firstParam = false;
                } else {
                    hint.append(", "); //$NON-NLS-1$
                }
                hint.append(param.getDeclarator().getName());
            }
        }
        hint.append(" )"); //$NON-NLS-1$

        _container.add(new Hint("function", startLoc, endLoc, hint.toString())); //$NON-NLS-1$
    }

    private void visitType(final IASTSimpleDeclaration declaration) throws BadLocationException {
        /* TODO: specific params: include type('class' / 'struct') */

        final IASTDeclSpecifier spec = declaration.getDeclSpecifier();
        if (spec instanceof IASTCompositeTypeSpecifier) {
            final String hint = ((IASTCompositeTypeSpecifier) spec).getName().getRawSignature();
            if (hint.isEmpty()) {
                return;
            }

            final IASTFileLocation location = declaration.getFileLocation();
            final int endLoc = location.getNodeOffset() + location.getNodeLength() - 1;
            final int startLoc = location.getNodeOffset();
            _container.add(new Hint("type", startLoc, endLoc, hint)); //$NON-NLS-1$
        }

        if (spec instanceof ICPPASTNamedTypeSpecifier) {
            final IASTName name = ((ICPPASTNamedTypeSpecifier) spec).getName();
            addBrackets(name);
        }

    }

    private void addBrackets(final IASTName name) throws BadLocationException {
        if (name instanceof ICPPASTTemplateId) {
            final IASTNode[] args = ((ICPPASTTemplateId) name).getTemplateArguments();
            addBrackets(args);
        } else if (name instanceof ICPPASTQualifiedName) {
            final IASTName[] names = ((ICPPASTQualifiedName) name).getNames();
            for (final IASTName n : names) {
                addBrackets(n);
            }
        }
    }

    private void addBrackets(final IASTNode[] args) throws BadLocationException {
        if (args == null || args.length == 0) {
            return;
        }

        final int startLoc = args[0].getFileLocation().getNodeOffset() - 1;
        final IASTFileLocation endFileLoc = args[args.length - 1].getFileLocation();
        final int endLoc = endFileLoc.getNodeOffset() + endFileLoc.getNodeLength();
        _container.add(new BracketsPair(startLoc, '<', endLoc, '>'));
    }

    private int shouldContinue() {
        if (_cancelProcessing.get()) {
            return PROCESS_ABORT;
        } else {
            return PROCESS_CONTINUE;
        }
    }

}
