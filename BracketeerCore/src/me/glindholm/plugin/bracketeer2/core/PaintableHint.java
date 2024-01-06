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

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

public class PaintableHint extends PaintableObject {

    private final String _txt;
    private final boolean _italic;
    private boolean _underline;

    public PaintableHint(final Position drawPosition, final RGB foreground, final RGB background, final boolean italic, final String txt) {
        super(drawPosition, foreground, background);
        _txt = txt;
        _italic = italic;
        _underline = false;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof final PaintableHint other)) {
            return false;
        }

        if (!_txt.equals(other._txt) || _italic != other._italic) {
            return false;
        }

        return super.equals(obj);
    }

    @Override
    protected void innerPaint(final GC gc, final StyledText st, final IDocument doc, final IRegion widgetRange, final Rectangle rect) {
        Font oldFont = null;
        Font newFont = null;
        if (_italic) {
            oldFont = gc.getFont();
            final FontData[] oldDatas = oldFont.getFontData();
            final FontData[] newDatas = new FontData[oldDatas.length];
            for (int i = 0; i < oldDatas.length; i++) {
                final FontData oldData = oldDatas[i];
                final FontData fontData = new FontData(oldData.getName(), oldData.getHeight(), SWT.ITALIC);
                fontData.setLocale(oldData.getLocale());
                newDatas[i] = fontData;
            }
            newFont = new Font(Display.getDefault(), newDatas);
            gc.setFont(newFont);
        }

        gc.drawText(_txt, rect.x, rect.y, _background == null);
        if (_underline) {
            gc.drawLine(rect.x - 1, rect.y + rect.height - 1, rect.x + rect.width + 1, rect.y + rect.height - 1);
        }

        if (newFont != null) {
            gc.setFont(oldFont);
            newFont.dispose();
        }
    }

    public boolean isOkToShow(final IDocument doc) throws BadLocationException {
        final IRegion region = doc.getLineInformationOfOffset(_position.getOffset());
        final int startOffset = _position.getOffset() + 1;
        int endOffset = region.getOffset() + region.getLength();

//        // is this the last char in the document?
//        if( startOffset >= doc.getLength() )
//            return true;

        endOffset = Math.min(endOffset, doc.getLength() - 1);

        // is the last char in the line?
        if (startOffset >= endOffset) {
            return true;
        }

        final String str = doc.get(startOffset, endOffset - startOffset);
        for (final char c : str.toCharArray()) {
            if (c != '\t' && c != ' ') {
                return false;
            }
        }
        return true;
    }

    public Rectangle getWidgetRect(final GC gc, final StyledText st, final IDocument doc, final IRegion widgetRange) {
        try {
            if ((widgetRange == null) || !isOkToShow(doc)) {
                return null;
            }

            final int offset = widgetRange.getOffset();

            final Point p = st.getLocationAtOffset(offset);
            p.x += gc.getAdvanceWidth(doc.getChar(_position.getOffset()));

            final Point metrics = gc.textExtent(_txt);
            final Rectangle rect = new Rectangle(p.x, p.y, metrics.x, metrics.y);
            return rect;
        } catch (final BadLocationException e) {
        }
        return null;
    }

    public void setUnderline(final boolean underline) {
        _underline = underline;
    }

}
