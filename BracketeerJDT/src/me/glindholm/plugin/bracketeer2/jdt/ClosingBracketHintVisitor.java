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
package me.glindholm.plugin.bracketeer2.jdt;

import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

import me.glindholm.plugin.bracketeer2.common.BracketsPair;
import me.glindholm.plugin.bracketeer2.common.Hint;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.IHintConfiguration;
import me.glindholm.plugin.bracketeer2.common.MutableBool;

public class ClosingBracketHintVisitor extends ASTVisitor {

    class ScopeInfo {
        public String _str;
        public int _offset;
        public Statement _statement;

        public ScopeInfo(final String str, final int offset, final Statement statement) {
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
    IDocument _doc;
    Stack<ScopeInfo> _scopeStack;

    public ClosingBracketHintVisitor(final IBracketeerProcessingContainer container, final IDocument doc, final MutableBool cancelProcessing,
            final IHintConfiguration hintConf) {
        _cancelProcessing = cancelProcessing;
        _container = container;
        _doc = doc;
        _hintConf = hintConf;

        _scopeStack = new Stack<>();
    }

    private void removeFromStack(final Statement node) {
        try {
            ScopeInfo scope;
            if (node instanceof SwitchStatement) {
                scope = _scopeStack.peek();
                if (scope._statement instanceof SwitchCase) {
                    _scopeStack.pop();
                }
            }

            scope = _scopeStack.pop();
            if (!scope._statement.getClass().equals(node.getClass())) {
                if (Activator.DEBUG) {
                    Activator.log("Lost track of scope. Expected:" + scope._statement + //$NON-NLS-1$
                            " but was:" + node); //$NON-NLS-1$
                }
            }
        } catch (final EmptyStackException e) {
            if (Activator.DEBUG) {
                Activator.log(e);
            }
        }
    }

    private void addBrackets(final ParameterizedType node) throws BadLocationException {
        @SuppressWarnings("unchecked")
        final List<Type> args = node.typeArguments();
        if (args.isEmpty()) {
            return;
        }

        Type type = args.get(0);
        final int startLoc = type.getStartPosition() - 1;
        type = args.get(args.size() - 1);
        final int endLoc = type.getStartPosition() + type.getLength();
        _container.add(new BracketsPair(startLoc, '<', endLoc, '>'));

    }

    private void addBrackets(final List<TypeParameter> typeParameters) throws BadLocationException {
        if (typeParameters == null || typeParameters.isEmpty()) {
            return;
        }

        TypeParameter type = typeParameters.get(0);
        final int startLoc = type.getStartPosition() - 1;
        type = typeParameters.get(typeParameters.size() - 1);
        final int endLoc = type.getStartPosition() + type.getLength();
        _container.add(new BracketsPair(startLoc, '<', endLoc, '>'));
    }

    @Override
    public boolean visit(final ParameterizedType node) {
        try {
            addBrackets(node);
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public boolean visit(final SwitchCase node) {
        /* TODO: specific params: don't show the switch part (only the case argument) */

        try {
            ScopeInfo scope = _scopeStack.peek();
            if (!(scope._statement instanceof SwitchStatement)) {
                if (!(scope._statement instanceof SwitchCase)) {
                    throw new ScopeTraceException("Lost track of stack (in case), found:" + scope._statement); //$NON-NLS-1$
                }

                _scopeStack.pop();
                scope = _scopeStack.peek();
            }

            if (!(scope._statement instanceof SwitchStatement)) {
                throw new ScopeTraceException("Lost track of stack (in case2), found:" + scope._statement); //$NON-NLS-1$
            }

            String hint = ""; //$NON-NLS-1$
            if (node.isDefault()) {
                hint = "default"; //$NON-NLS-1$
            } else {
                hint = "case: " + node.getExpression(); //$NON-NLS-1$
            }

            final int startLoc = node.getStartPosition();
            _scopeStack.push(new ScopeInfo(scope._str + " - " + hint, startLoc, node)); //$NON-NLS-1$
        } catch (final ScopeTraceException | EmptyStackException e) {
            if (Activator.DEBUG) {
                Activator.log(e);
            }
        }

        return shouldContinue();
    }

    @Override
    public boolean visit(final DoStatement node) {
        String hint = GetNodeText(node.getExpression());
        final int startLoc = node.getStartPosition();
        hint = "do_while( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        _scopeStack.push(new ScopeInfo(hint, startLoc, node));
        return shouldContinue();
    }

    @Override
    public void endVisit(final DoStatement node) {
        removeFromStack(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(final BreakStatement node) {
        return visitBreak(node);
    }

    @Override
    public boolean visit(final ContinueStatement node) {
        return visitBreak(node);
    }

    private boolean visitBreak(final Statement node) {
        try {
            if (_scopeStack.isEmpty()) {
                throw new ScopeTraceException("break without scope: " + node); //$NON-NLS-1$
            }

            final ScopeInfo scope = _scopeStack.peek();

            if (node instanceof BreakStatement) {
                // ignoring break with labels on them
                if (((BreakStatement) node).getLabel() != null) {
                    return shouldContinue();
                }
            }

            String hintType;
            if (scope._statement instanceof ForStatement) {
                hintType = "break-for"; //$NON-NLS-1$
            } else if (scope._statement instanceof EnhancedForStatement) {
                hintType = "break-foreach"; //$NON-NLS-1$
            } else if (scope._statement instanceof WhileStatement) {
                hintType = "break-while"; //$NON-NLS-1$
            } else if (scope._statement instanceof DoStatement) {
                hintType = "break-do"; //$NON-NLS-1$
            } else if (scope._statement instanceof SwitchCase) {
                hintType = "break-case"; //$NON-NLS-1$
            } else {
                throw new ScopeTraceException("Unexpect scope (" + scope._statement + ") on break/continue:" + node); //$NON-NLS-1$ //$NON-NLS-2$
            }

            final int endLoc = node.getStartPosition() + node.getLength() - 1;
            _container.add(new Hint(hintType, scope._offset, endLoc, scope._str));
        } catch (final ScopeTraceException e) {
            if (Activator.DEBUG) {
                Activator.log(e);
            }
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(final TypeDeclaration node) {
        final String hint = node.getName().getIdentifier();
        final int startLoc = node.getName().getStartPosition();
        final int endLoc = node.getStartPosition() + node.getLength() - 1;
        try {
            _container.add(new Hint("type", startLoc, endLoc, hint)); //$NON-NLS-1$
            addBrackets(node.typeParameters());
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public boolean visit(final MethodDeclaration node) {
        // starting a function empties the stack... (which should already be empty on good flow)
        _scopeStack.clear();

        final StringBuilder hint = new StringBuilder();
        hint.append(node.getName().getIdentifier());
        /* TODO: specific params: exclude function parameters (show only the name) */
        hint.append("( "); //$NON-NLS-1$
        for (@SuppressWarnings("rawtypes")
        final Iterator iterator = node.parameters().iterator(); iterator.hasNext();) {
            final SingleVariableDeclaration param = (SingleVariableDeclaration) iterator.next();
            hint.append(param.getName());
            if (iterator.hasNext()) {
                hint.append(", "); //$NON-NLS-1$
            }
        }
        hint.append(" )"); //$NON-NLS-1$
        final int startLoc = node.getName().getStartPosition();
        final int endLoc = node.getStartPosition() + node.getLength() - 1;
        try {
            _container.add(new Hint("function", startLoc, endLoc, hint.toString())); //$NON-NLS-1$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public boolean visit(final ForStatement node) {
        /* TODO: specific params: show also initializer && increment expressions */
        String hint = GetNodeText(node.getExpression());
        hint = "for( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        final int startLoc = node.getStartPosition();
        final int endLoc = startLoc + node.getLength() - 1;
        _scopeStack.push(new ScopeInfo(hint, startLoc, node));
        try {
            _container.add(new Hint("for", startLoc, endLoc, hint)); //$NON-NLS-1$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public void endVisit(final ForStatement node) {
        removeFromStack(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(final EnhancedForStatement node) {
        /* TODO: specific params: put 2 checkboxes: the var name & the collection */
        String hint = GetNodeText(node.getExpression());
        final int startLoc = node.getStartPosition();
        final int endLoc = startLoc + node.getLength() - 1;
        hint = "foreach( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        _scopeStack.push(new ScopeInfo(hint, startLoc, node));
        try {
            _container.add(new Hint("foreach", startLoc, endLoc, hint)); //$NON-NLS-1$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public void endVisit(final EnhancedForStatement node) {
        removeFromStack(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(final SwitchStatement node) {
        String hint = GetNodeText(node.getExpression());
        final int startLoc = node.getStartPosition();
        final int endLoc = startLoc + node.getLength() - 1;
        hint = "switch( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        _scopeStack.push(new ScopeInfo(hint, startLoc, node));
        try {
            _container.add(new Hint("switch", startLoc, endLoc, hint)); //$NON-NLS-1$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public void endVisit(final SwitchStatement node) {
        removeFromStack(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(final WhileStatement node) {
        String hint = GetNodeText(node.getExpression());
        final int startLoc = node.getStartPosition();
        final int endLoc = startLoc + node.getLength() - 1;
        hint = "while( " + hint + " )"; //$NON-NLS-1$ //$NON-NLS-2$
        _scopeStack.push(new ScopeInfo(hint, startLoc, node));
        try {
            _container.add(new Hint("while", startLoc, endLoc, hint)); //$NON-NLS-1$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public void endVisit(final WhileStatement node) {
        removeFromStack(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(final SynchronizedStatement node) {
        final String hint = GetNodeText(node.getExpression());
        final int startLoc = node.getStartPosition();
        final int endLoc = startLoc + node.getLength() - 1;
        try {
            _container.add(new Hint("synchronized", startLoc, endLoc, "synchronized( " + hint + " )")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }
        return shouldContinue();
    }

    @Override
    public boolean visit(final IfStatement node) {
        final Statement thenStmt = node.getThenStatement();
        final Statement elseStmt = node.getElseStatement();
        final String hint = GetNodeText(node.getExpression());

        boolean showIfHint = elseStmt == null;
        int endLoc = -1;

        try {

            if (!showIfHint) {

                if (_doc.getLineOfOffset(elseStmt.getStartPosition()) != _doc.getLineOfOffset(thenStmt.getStartPosition() + thenStmt.getLength())) {
                    showIfHint = true;
                }

                // if the else looks like this "} else {", then show the hint on the "{"
                if (!showIfHint && !(elseStmt instanceof IfStatement)) {
                    endLoc = elseStmt.getStartPosition();
                    showIfHint = true;
                }
            }

            if (showIfHint && !(thenStmt instanceof Block)) {
                showIfHint = false;
            }

            if (showIfHint) {
                if (endLoc == -1) {
                    endLoc = thenStmt.getStartPosition() + thenStmt.getLength() - 1;
                }
                final int startLoc = node.getStartPosition();
                _container.add(new Hint("if", startLoc, endLoc, "if( " + hint + " )")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }

            if (elseStmt != null && !(elseStmt instanceof IfStatement) && elseStmt instanceof Block) {
                endLoc = elseStmt.getStartPosition() + elseStmt.getLength() - 1;
                final int startLoc = elseStmt.getStartPosition();
                _container.add(new Hint("if", startLoc, endLoc, "else_of_if( " + hint + " )")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }

        } catch (final BadLocationException e) {
            _cancelProcessing.set(true);
        }

        return shouldContinue();
    }

    private String GetNodeText(final ASTNode node) {
        if (node == null) {
            return ""; //$NON-NLS-1$
        }

        try {
            return _doc.get(node.getStartPosition(), node.getLength());
        } catch (final BadLocationException e) {
            return ""; //$NON-NLS-1$
        }
    }

    private boolean shouldContinue() {
        return !_cancelProcessing.get();
    }
}
