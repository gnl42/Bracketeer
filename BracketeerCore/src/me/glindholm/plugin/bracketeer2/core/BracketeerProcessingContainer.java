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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DefaultPositionUpdater;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IPositionUpdater;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.Position;
import org.eclipse.ui.services.IDisposable;

import me.glindholm.plugin.bracketeer2.Activator;
import me.glindholm.plugin.bracketeer2.common.BracketsPair;
import me.glindholm.plugin.bracketeer2.common.Hint;
import me.glindholm.plugin.bracketeer2.common.IBracketeerProcessingContainer;
import me.glindholm.plugin.bracketeer2.common.SingleBracket;

public class BracketeerProcessingContainer implements IDisposable, IBracketeerProcessingContainer {
    private class ObjectContainer<T> {
        private final T _object;
        private boolean _toDelete;

        public ObjectContainer(final T obj) {
            _object = obj;
        }

        public T getObject() {
            return _object;
        }

        public boolean isToDelete() {
            return _toDelete;
        }

        public void setToDelete(final boolean toDelete) {
            _toDelete = toDelete;
        }

        @Override
        public boolean equals(final Object other) {
            if (other == null) {
                return false;
            }

            if (other instanceof ObjectContainer<?>) {
                return _object.equals(((ObjectContainer<?>) other)._object);
            }

            return _object.equals(other);
        }
    }

    private final IDocument _doc;
    private Object _docLock;

    private final List<ObjectContainer<SingleBracket>> _singleBrackets;
    private final List<ObjectContainer<BracketsPair>> _bracketsPairList;
    private final List<ObjectContainer<Hint>> _hints;

    private final String _positionCategory;
    private final IPositionUpdater _positionUpdater;
    private final List<IProcessingContainerListener> _listeners;

    private boolean _bracketsPairsTouched;
    private boolean _singleBracketsTouched;
    private boolean _hintsTouched;
    private boolean _updatingListeners;

    public BracketeerProcessingContainer(final IDocument doc) {
        _singleBrackets = new ArrayList<>();
        _bracketsPairList = new LinkedList<>();
        _hints = new LinkedList<>();

        _doc = doc;
        if (_doc instanceof ISynchronizable) {
            _docLock = ((ISynchronizable) _doc).getLockObject();
        } else {
            _docLock = new Object();
        }

        _positionCategory = "bracketeerPosition"; //$NON-NLS-1$

        _doc.addPositionCategory(_positionCategory);
        _positionUpdater = new DefaultPositionUpdater(_positionCategory);
        _doc.addPositionUpdater(_positionUpdater);

        _listeners = new LinkedList<>();

        _updatingListeners = false;
    }

    @Override
    public void dispose() {
        _doc.removePositionUpdater(_positionUpdater);
        try {
            _doc.removePositionCategory(_positionCategory);
        } catch (final BadPositionCategoryException e) {
            Activator.log(e);
        }
    }

    public void addListener(final IProcessingContainerListener listener) {
        _listeners.add(listener);
    }

    public void removeListener(final IProcessingContainerListener listener) {
        if (!_listeners.remove(listener)) {
            Activator.log(Messages.BracketeerProcessingContainer_listsnerNotFound);
        }
    }

    @Override
    public BracketsPair getMatchingPair(final int openOffset, final int closeOffset) {

        synchronized (_docLock) {
            for (final ObjectContainer<BracketsPair> objCont : _bracketsPairList) {
                if (objCont.isToDelete()) {
                    continue;
                }

                final BracketsPair pair = objCont.getObject();
                final Position openPos = pair.getOpeningBracket().getPosition();
                if (openPos != null && openPos.getOffset() == openOffset) {
                    final Position closePos = pair.getClosingBracket().getPosition();
                    if (closePos != null && closePos.getOffset() == closeOffset) {
                        return pair;
                    }

                    Activator.log(String.format("[%1$d,%2$d] paritally found - %3$s", //$NON-NLS-1$
                            openOffset, closeOffset, pair.toString()));
                    return null;
                }
            }
        }

        return null;
    }

    public List<BracketsPair> getPairsSurrounding(final int offset) {
        final List<BracketsPair> retVal = new LinkedList<>();

        synchronized (_docLock) {
            for (final ObjectContainer<BracketsPair> objCont : _bracketsPairList) {
                final BracketsPair pair = objCont.getObject();

                final Position opBrPos = pair.getOpeningBracket().getPosition();
                final Position clBrPos = pair.getClosingBracket().getPosition();
                if (opBrPos == null || clBrPos == null) {
                    continue;
                }

                if (opBrPos.offset <= offset && clBrPos.offset > offset) {
                    if (!retVal.contains(pair)) {
                        retVal.add(pair);
                    }
                }
            }
        }
        return retVal;
    }

    public List<BracketsPair> getMatchingPairs(final int startOffset, final int length) {
        final List<BracketsPair> retVal = new LinkedList<>();

        synchronized (_docLock) {
            for (final ObjectContainer<BracketsPair> objCont : _bracketsPairList) {
                final BracketsPair pair = objCont.getObject();

                for (final SingleBracket br : pair.getBrackets()) {
                    final Position pos = br.getPosition();
                    if (pos != null && pos.overlapsWith(startOffset, length) && !retVal.contains(pair)) {
                        retVal.add(pair);
                        break;
                    }
                }
            }
        }
        return retVal;
    }

    public List<SingleBracket> getSingleBrackets() {
        final List<SingleBracket> ret = new LinkedList<>();
        synchronized (_docLock) {
            for (final ObjectContainer<SingleBracket> objCont : _singleBrackets) {
                final SingleBracket br = objCont.getObject();

                if (br.getPosition() != null) {
                    ret.add(br);
                }
            }
        }
        return ret;
    }

    public Hint getHint(final int startOffset) {
        synchronized (_docLock) {
            for (final ObjectContainer<Hint> objCont : _hints) {
                final Hint hint = objCont.getObject();

                final Position pos = hint.getHintPosition();
                if (pos != null && pos.overlapsWith(startOffset, 1)) {
                    return hint;
                }
            }
        }
        return null;
    }

    @Override
    public List<Hint> getHints() {
        final List<Hint> ret = new LinkedList<>();
        synchronized (_docLock) {
            for (final ObjectContainer<Hint> objCont : _hints) {
                final Hint hint = objCont.getObject();

                if (!hint.hasDeletedPosition()) {
                    ret.add(hint);
                }
            }
        }
        return ret;
    }

    public List<BracketsPair> getBracketPairs() {
        final List<BracketsPair> ret = new LinkedList<>();
        synchronized (_docLock) {
            for (final ObjectContainer<BracketsPair> objCont : _bracketsPairList) {
                final BracketsPair pair = objCont.getObject();
                if (!pair.hasDeletedPosition()) {
                    ret.add(pair);
                }
            }
        }
        return ret;
    }

    public void markAllToBeDeleted() {
        synchronized (_docLock) {
            for (final ObjectContainer<BracketsPair> objCont : _bracketsPairList) {
                objCont.setToDelete(true);
            }

            for (final ObjectContainer<SingleBracket> objCont : _singleBrackets) {
                objCont.setToDelete(true);
            }

            for (final ObjectContainer<Hint> objCont : _hints) {
                objCont.setToDelete(true);
            }
        }
    }

    public void deleteAllMarked() {
        synchronized (_docLock) {
            {
                final Iterator<ObjectContainer<BracketsPair>> it = _bracketsPairList.iterator();
                while (it.hasNext()) {
                    final ObjectContainer<BracketsPair> objCont = it.next();

                    if (objCont.isToDelete()) {
                        _bracketsPairsTouched = true;
                        for (final SingleBracket bracket : objCont.getObject().getBrackets()) {
                            delete(bracket.getPositionRaw());
                        }
                        it.remove();
                    }
                }
            }

            {
                final Iterator<ObjectContainer<SingleBracket>> it = _singleBrackets.iterator();
                while (it.hasNext()) {
                    final ObjectContainer<SingleBracket> objCont = it.next();

                    if (objCont.isToDelete()) {
                        _singleBracketsTouched = true;
                        delete(objCont.getObject().getPositionRaw());
                        it.remove();
                    }
                }
            }

            {
                final Iterator<ObjectContainer<Hint>> it = _hints.iterator();
                while (it.hasNext()) {
                    final ObjectContainer<Hint> objCont = it.next();

                    if (objCont.isToDelete()) {
                        _hintsTouched = true;
                        delete(objCont.getObject().getOriginPositionRaw());
                        delete(objCont.getObject().getHintPositionRaw());
                        it.remove();
                    }
                }
            }
        }
        if (Activator.DEBUG) {
            try {
                Activator.trace("Positions tracked = " + _doc.getPositions(_positionCategory).length); //$NON-NLS-1$
            } catch (final BadPositionCategoryException e) {
                Activator.log(e);
            }
            Activator.trace("Pairs = " + _bracketsPairList.size()); //$NON-NLS-1$
            Activator.trace("Singles = " + _singleBrackets.size()); //$NON-NLS-1$
            Activator.trace("Hints = " + _hints.size()); //$NON-NLS-1$
        }
    }

    public void updateComplete() {
        _updatingListeners = true;

        for (final IProcessingContainerListener listener : _listeners) {
            listener.containerUpdated(_bracketsPairsTouched, _singleBracketsTouched, _hintsTouched);
        }

        _bracketsPairsTouched = false;
        _singleBracketsTouched = false;
        _hintsTouched = false;

        _updatingListeners = false;
    }

    public boolean isUpdatingListeners() {
        return _updatingListeners;
    }

    @Override
    public void add(final BracketsPair pair) throws BadLocationException {
        synchronized (_docLock) {
            final ObjectContainer<BracketsPair> existing = findExistingObj(_bracketsPairList, pair);

            if (existing != null) {
                if (existing.equals(pair) && !existing.getObject().hasDeletedPosition()) {
                    existing.setToDelete(false);
                    return;
                } else {
                    deletePair(existing);
                }
            }

            _bracketsPairsTouched = true;

            final ObjectContainer<BracketsPair> pairContainer = new ObjectContainer<>(pair);

            _bracketsPairList.add(pairContainer);
            for (final SingleBracket br : pair.getBrackets()) {
                addPosition(br.getPosition());
            }
        }
    }

    @Override
    public void add(final SingleBracket bracket) throws BadLocationException {
        synchronized (_docLock) {
            final ObjectContainer<SingleBracket> existing = findExistingObj(_singleBrackets, bracket);

            if (existing != null) {
                if (existing.equals(bracket) && existing.getObject().getPosition() != null) {
                    existing.setToDelete(false);
                    return;
                } else {
                    deleteSingle(existing);
                }
            }

            _singleBracketsTouched = true;

            _singleBrackets.add(new ObjectContainer<>(bracket));

            addPosition(bracket.getPosition());
        }
    }

    @Override
    public void add(final Hint hint) throws BadLocationException {
        synchronized (_docLock) {
            final ObjectContainer<Hint> existing = findExistingObj(_hints, hint);

            if (existing != null) {
                if (existing.equals(hint) && !existing.getObject().hasDeletedPosition()) {
                    existing.setToDelete(false);
                    return;
                } else {
                    deleteHint(existing);
                }
            }

            _hintsTouched = true;

            _hints.add(new ObjectContainer<>(hint));

            addPosition(hint.getHintPositionRaw());
            addPosition(hint.getOriginPositionRaw());
        }
    }

    private void addPosition(final Position position) throws BadLocationException {
        try {
            if (position != null) {
                _doc.addPosition(_positionCategory, position);
            }
        } catch (final BadPositionCategoryException e) {
            Activator.log(e);
        }

    }

    private static <T> ObjectContainer<T> findExistingObj(final List<ObjectContainer<T>> objList, final T obj) {
        for (final ObjectContainer<T> objCont : objList) {
            if (objCont.equals(obj)) {
                return objCont;
            }
        }
        return null;
    }

    private void delete(final Position position) {
        try {
            _doc.removePosition(_positionCategory, position);
        } catch (final BadPositionCategoryException e) {
            Activator.log(e);
        }
    }

    private void deletePair(final ObjectContainer<BracketsPair> objCont) {
        synchronized (_docLock) {
            final boolean found = _bracketsPairList.remove(objCont);
            Assert.isTrue(found);

            for (final SingleBracket bracket : objCont.getObject().getBrackets()) {
                delete(bracket.getPositionRaw());
            }
        }
    }

    private void deleteSingle(final ObjectContainer<SingleBracket> objCont) {
        synchronized (_docLock) {
            final boolean found = _singleBrackets.remove(objCont);
            Assert.isTrue(found);

            final SingleBracket bracket = objCont.getObject();
            delete(bracket.getPositionRaw());
        }
    }

    private void deleteHint(final ObjectContainer<Hint> objCont) {
        synchronized (_docLock) {
            final boolean found = _hints.remove(objCont);
            Assert.isTrue(found);

            final Hint hint = objCont.getObject();
            delete(hint.getOriginPositionRaw());
            delete(hint.getHintPositionRaw());
        }
    }

    /*
     * private static <T> List<T> mapObjListToObjList(Collection<ObjectContainer<T>> vals) { List<T>
     * retVal = new LinkedList<T>(); for (ObjectContainer<T> mapObj : vals) { if(
     * !retVal.contains(mapObj.getObject()) && !mapObj.isToDelete() ) retVal.add(mapObj.getObject()); }
     * return retVal; }
     */

}
