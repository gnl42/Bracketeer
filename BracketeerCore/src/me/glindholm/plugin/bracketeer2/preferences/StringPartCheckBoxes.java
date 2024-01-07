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
package me.glindholm.plugin.bracketeer2.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class StringPartCheckBoxes extends FieldEditor {
    private Composite _parent;
    private final String _bracketsString;
    private final List<Button> _pairsList;

    public StringPartCheckBoxes(final String name, final Composite parent, String bracketsString) {
        setPreferenceName(name);
        if (bracketsString == null) {
            bracketsString = ""; //$NON-NLS-1$
        }
        _bracketsString = bracketsString;
        _parent = parent;
        _pairsList = new ArrayList<>();

        createControl(parent);
    }

    @Override
    protected void adjustForNumColumns(final int numColumns) {
        ((GridData) _parent.getLayoutData()).horizontalSpan = numColumns;
    }

    @Override
    protected void doFillIntoGrid(final Composite parent, final int numColumns) {
        _parent = parent;

        final GridData griddata = new GridData(GridData.FILL_HORIZONTAL);
        griddata.horizontalSpan = numColumns;
        parent.setLayoutData(griddata);

        Assert.isTrue(_bracketsString.length() % 2 == 0, Messages.StringPartCheckBoxes_ErrSupportedBrackets);
        for (int i = 0; i < _bracketsString.length(); i += 2) {
            final String pair = _bracketsString.substring(i, i + 2);
            final Button btnCheckButton = new Button(parent, SWT.CHECK);
            btnCheckButton.setText(pair);
            _pairsList.add(btnCheckButton);
        }
    }

    @Override
    protected void doLoad() {
        final String str = getPreferenceStore().getString(getPreferenceName());
        updateButtons(str);
    }

    @Override
    protected void doLoadDefault() {
        final String str = getPreferenceStore().getDefaultString(getPreferenceName());
        updateButtons(str);
    }

    @Override
    protected void doStore() {
        final StringBuilder sb = new StringBuilder();
        for (final Button btn : _pairsList) {
            if (btn.getSelection()) {
                sb.append(btn.getText());
            }
        }
        getPreferenceStore().setValue(getPreferenceName(), sb.toString());
    }

    @Override
    public int getNumberOfControls() {
        return 2;
    }

    private void updateButtons(final String str) {
        for (final Button btn : _pairsList) {
            btn.setSelection(str.contains(btn.getText()));
        }
    }

}
