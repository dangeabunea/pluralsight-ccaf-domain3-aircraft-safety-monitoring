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
}
