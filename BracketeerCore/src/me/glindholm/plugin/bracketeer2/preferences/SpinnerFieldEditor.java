/*******************************************************************************
 * Copyright (c) Emil Crumhorn - Hexapixel.com - emil.crumhorn@gmail.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    emil.crumhorn@gmail.com - initial API and implementation
 *******************************************************************************/

package me.glindholm.plugin.bracketeer2.preferences;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

// very basic spinner in a field editor
public class SpinnerFieldEditor extends FieldEditor {

    private Composite _outer;

    private Spinner _spinner;

    public SpinnerFieldEditor(final String name, final String label, final Composite parent) {
        super(name, label, parent);
    }

    @Override
    protected void adjustForNumColumns(final int numColumns) {
        ((GridData) _outer.getLayoutData()).horizontalSpan = numColumns;
    }

    @Override
    protected void doFillIntoGrid(final Composite parent, final int numColumns) {
        _outer = parent;

        final GridData griddata = new GridData(GridData.FILL_HORIZONTAL);
        griddata.horizontalSpan = numColumns;
        _outer.setLayoutData(griddata);

        final Label label = getLabelControl(_outer);
        label.setLayoutData(new GridData());

        _spinner = new Spinner(_outer, SWT.BORDER);
        _spinner.setLayoutData(new GridData());
        _spinner.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
    }

    public Spinner getSpinner() {
        return _spinner;
    }

    @Override
    protected void doLoad() {
        final int loadedint = getPreferenceStore().getInt(getPreferenceName());
        _spinner.setSelection(loadedint);
    }

    @Override
    protected void doLoadDefault() {
        final int loadedint = getPreferenceStore().getDefaultInt(getPreferenceName());
        _spinner.setSelection(loadedint);
    }

    @Override
    protected void doStore() {
        getPreferenceStore().setValue(getPreferenceName(), _spinner.getSelection());
    }

    @Override
    public int getNumberOfControls() {
        return 2;
    }

}
