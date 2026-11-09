package com.atcsafety.restapi.application;

/** Helpers for writing data-derived text into the CSV export safely. */
final class CsvFields {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvFields() {
    }

    /**
     * Prevents CSV formula injection: a text value starting with =, +, -, @, tab or carriage return
     * is prefixed with a single quote so spreadsheet apps show it as text instead of evaluating it.
     * Apply only to free-text columns, not to numeric ones.
     */
    static String neutralizeFormula(String text) {
        if (text == null || text.isEmpty() || FORMULA_TRIGGERS.indexOf(text.charAt(0)) < 0) {
            return text;
        }
        return "'" + text;
    }
}
