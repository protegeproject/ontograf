package org.protege.ontograf.ui;

public enum EdgeLabelMode {
    OFF("Off"), SELECTED("Selected"), ALL("All");

    private final String title;

    EdgeLabelMode(String title) { this.title = title; }

    public static EdgeLabelMode fromPreference(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ex) { return SELECTED; }
    }

    @Override public String toString() { return title; }
}
