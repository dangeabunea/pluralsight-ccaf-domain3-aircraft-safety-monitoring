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
    /**
     * Makes a free-text value safe as one CSV cell: neutralizes formulas first, then wraps the value
     * in quotes (escaping embedded quotes, RFC 4180) when it contains a comma, quote or line break,
     * so it cannot spill into a neighbouring cell that would bypass the formula check.
     */
    static String safeTextCell(String text) {
        String neutralized = neutralizeFormula(text);
        if (neutralized == null || neutralized.chars().noneMatch(c -> ",\"\r\n".indexOf(c) >= 0)) {
            return neutralized;
        }
        return "\"" + neutralized.replace("\"", "\"\"") + "\"";
    }

    static String neutralizeFormula(String text) {
        if (text == null || text.isEmpty() || FORMULA_TRIGGERS.indexOf(text.charAt(0)) < 0) {
            return text;
        }
        return "'" + text;
    }
}
