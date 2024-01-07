package me.glindholm.plugin.bracketeer2.preferences;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.SWTKeySupport;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public class ModifiersKeySequenceText extends FieldEditor implements KeyListener {
    Text _textField;
    int _mask;

    public ModifiersKeySequenceText(final String name, final String labelText, final Composite parent) {
        init(name, labelText);
        createControl(parent);
    }

    @Override
    public void keyPressed(final KeyEvent e) {
        switch (e.keyCode) {
        case SWT.CTRL:
        case SWT.ALT:
        case SWT.COMMAND:
        case SWT.SHIFT:
            _mask |= e.keyCode;
            generateTextFromMask();
            break;
        case SWT.DEL:
        case SWT.BS:
            _mask = 0;
            generateTextFromMask();
            break;
        }
    }

    private void generateTextFromMask() {
        if (_textField == null) {
            return;
        }

        final KeyStroke stroke = SWTKeySupport.convertAcceleratorToKeyStroke(_mask);
        String str = stroke.format();
        if (str.endsWith("+")) { //$NON-NLS-1$
            str = str.substring(0, str.length() - 1);
        }
        _textField.setText(str);
    }

    @Override
    public void keyReleased(final KeyEvent e) {
        // nothing
    }

    @Override
    protected void adjustForNumColumns(final int numColumns) {
        final GridData gd = (GridData) _textField.getLayoutData();
        gd.horizontalSpan = numColumns - 1;
        // We only grab excess space if we have to
        // If another field editor has more columns then
        // we assume it is setting the width.
        gd.grabExcessHorizontalSpace = gd.horizontalSpan == 1;
    }

    @Override
    protected void doFillIntoGrid(final Composite parent, final int numColumns) {
        getLabelControl(parent);

        _textField = getTextControl(parent);
        final GridData gd = new GridData();
        gd.horizontalSpan = numColumns - 1;

        gd.horizontalAlignment = GridData.FILL;
        gd.grabExcessHorizontalSpace = true;

        _textField.setLayoutData(gd);
        _textField.setToolTipText(Messages.ModifiersKeySequenceText_ModifiersToolTip);
    }

    @Override
    protected void doLoad() {
        _mask = getPreferenceStore().getInt(getPreferenceName());
        generateTextFromMask();
    }

    @Override
    protected void doLoadDefault() {
        _mask = getPreferenceStore().getDefaultInt(getPreferenceName());
        generateTextFromMask();
    }

    @Override
    protected void doStore() {
        getPreferenceStore().setValue(getPreferenceName(), _mask);
    }

    @Override
    public int getNumberOfControls() {
        return 2;
    }

    @Override
    public void dispose() {
        if (_textField != null) {
            _textField.removeKeyListener(this);
        }

        super.dispose();
    }

    /*
     * (non-Javadoc) Method declared on FieldEditor.
     */
    @Override
    public void setFocus() {
        if (_textField != null) {
            _textField.setFocus();
        }
    }

    /*
     * @see FieldEditor.setEnabled(boolean,Composite).
     */
    @Override
    public void setEnabled(final boolean enabled, final Composite parent) {
        super.setEnabled(enabled, parent);
        getTextControl(parent).setEnabled(enabled);
    }

    public Text getTextControl(final Composite parent) {
        if (_textField == null) {
            _textField = new Text(parent, SWT.SINGLE | SWT.BORDER);
            _textField.setFont(parent.getFont());

            _textField.addDisposeListener(event -> _textField = null);

            _textField.addKeyListener(this);
        } else {
            checkParent(_textField, parent);
        }
        return _textField;
    }

}
