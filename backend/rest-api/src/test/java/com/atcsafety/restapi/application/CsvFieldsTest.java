package com.atcsafety.restapi.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFieldsTest {

    @Nested
    class NeutralizeFormula {

        @ParameterizedTest
        @ValueSource(strings = {"=HYPERLINK(\"x\")", "+1", "-1", "@SUM(A1)", "\tAF100", "\rAF100"})
        void should_prefix_with_single_quote_when_text_starts_with_formula_trigger(String text) {
            // arrange
            String expected = "'" + text;

            // act
            String result = CsvFields.neutralizeFormula(text);

            // assert
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void should_return_text_unchanged_when_it_does_not_start_with_formula_trigger() {
            // arrange
            String callsign = "AF100";

            // act
            String result = CsvFields.neutralizeFormula(callsign);

            // assert
            assertThat(result).isEqualTo("AF100");
        }

        @Test
        void should_return_null_when_text_is_null() {
            // arrange
            String text = null;

            // act
            String result = CsvFields.neutralizeFormula(text);

            // assert
            assertThat(result).isNull();
        }
    }

    @Nested
    class SafeTextCell {

        @Test
        void should_keep_injected_formula_inside_one_quoted_cell_when_text_contains_a_comma() {
            // arrange
            String callsign = "AF100,=HYPERLINK(\"http://x\")";

            // act
            String result = CsvFields.safeTextCell(callsign);

            // assert
            assertThat(result).isEqualTo("\"AF100,=HYPERLINK(\"\"http://x\"\")\"");
        }

        @Test
        void should_quote_text_when_it_contains_a_line_break() {
            // arrange
            String callsign = "AF100\n=1+1";

            // act
            String result = CsvFields.safeTextCell(callsign);

            // assert
            assertThat(result).isEqualTo("\"AF100\n=1+1\"");
        }

        @Test
        void should_neutralize_then_quote_when_text_starts_with_trigger_and_contains_a_comma() {
            // arrange
            String callsign = "=1,2";

            // act
            String result = CsvFields.safeTextCell(callsign);

            // assert
            assertThat(result).isEqualTo("\"'=1,2\"");
        }

        @Test
        void should_return_text_unchanged_when_it_needs_no_quoting() {
            // arrange
            String callsign = "AF100";

            // act
            String result = CsvFields.safeTextCell(callsign);

            // assert
            assertThat(result).isEqualTo("AF100");
        }
    }
}
