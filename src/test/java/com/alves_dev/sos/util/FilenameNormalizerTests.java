package com.alves_dev.sos.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameNormalizerTests {

    private final FilenameNormalizer normalizer = new FilenameNormalizer();

    @Test
    void derivesSafeNamesAndUsesDeterministicConflictPrefix() {
        assertThat(normalizer.derive("Currículo final 2026.pdf")).isEqualTo("Curr_culo_final_2026.pdf");
        assertThat(normalizer.normalize("Report.PDF")).isEqualTo("report.pdf");
        assertThat(normalizer.disambiguate("report.pdf", "abcdef123456"))
                .isEqualTo("abcdef12_report.pdf");
    }
}
