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
package me.glindholm.plugin.bracketeer2.common;

import org.eclipse.jface.text.Position;

public class Hint {
    private String _str;
    private final String _type;

    private final Position _originPosition;
    private final Position _hintPosition;

    public Hint(final String type, final int originOffset, final int hintOffset, final String txt) {
        _originPosition = new Position(originOffset, 1);
        _hintPosition = new Position(hintOffset, 1);
        _str = txt.replaceAll("\\s*[\r|\n]+\\s*", " "); //$NON-NLS-1$ //$NON-NLS-2$
        _type = type;
    }

    public String getTxt() {
        return _str;
    }

    public String getType() {
        return _type;
    }

    public Position getHintPosition() {
        if (!_hintPosition.isDeleted && _hintPosition.length > 0) {
            return _hintPosition;
        } else {
            return null;
        }
    }

    public Position getHintPositionRaw() {
        return _hintPosition;
    }

    public Position getOriginPosition() {
        if (!_originPosition.isDeleted && _originPosition.length > 0) {
            return _originPosition;
        } else {
            return null;
        }
    }

    public Position getOriginPositionRaw() {
        return _originPosition;
    }

    public void setTxt(final String str) {
        _str = str;
    }

    public boolean hasDeletedPosition() {
        return getHintPosition() == null || getOriginPosition() == null;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof final Hint other)) {
            return false;
        }

        return _str.equals(other._str) && _type.equals(other._type) && _originPosition.equals(other._originPosition)
                && _hintPosition.equals(other._hintPosition);
    }

}