package com.alves_dev.sos.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTests {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatesPrefixedUniqueKeysAndStableHashes() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).startsWith("sos_").hasSize(47);
        assertThat(second).isNotEqualTo(first);
        assertThat(generator.hash(first)).hasSize(64).isEqualTo(generator.hash(first));
        assertThat(generator.hash(second)).isNotEqualTo(generator.hash(first));
    }
}
