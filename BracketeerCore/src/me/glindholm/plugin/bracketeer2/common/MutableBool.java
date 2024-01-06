package me.glindholm.plugin.bracketeer2.common;

public class MutableBool {
    private boolean _val;

    public MutableBool(final boolean val) {
        set(val);
    }

    public void set(final boolean val) {
        _val = val;
    }

    public boolean get() {
        return _val;
    }
}
