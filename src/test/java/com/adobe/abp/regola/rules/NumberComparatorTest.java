/*
 *  Copyright 2026 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.rules;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Testing NumberRule.NumberComparator")
class NumberComparatorTest {

    private final NumberRule.NumberComparator<Long> comparator = new NumberRule.NumberComparator<>();

    @Test
    @DisplayName("compare large longs of same type without precision loss")
    void compareLargeLongsOfSameType() {
        // These two longs differ by 1, but are far too close together to be told apart once
        // converted to double (doubles only have ~15-17 significant decimal digits).
        // Comparing via a.doubleValue() - b.doubleValue() would collapse to 0/overflow,
        // while Long#compareTo (used when types match) keeps full precision.
        long a = Long.MAX_VALUE;
        long b = Long.MAX_VALUE - 1;

        assertThat(comparator.compare(a, b)).isPositive();
        assertThat(comparator.compare(b, a)).isNegative();
        assertThat((double) a - (double) b).as("sanity check: double subtraction loses precision here").isZero();
    }

    @Test
    @DisplayName("compare extreme longs without overflow")
    void compareExtremeLongsWithoutOverflow() {
        // a.doubleValue() - b.doubleValue() converted back through a lossy subtraction would
        // overflow/underflow for values near the long range extremes.
        long a = Long.MAX_VALUE;
        long b = Long.MIN_VALUE;

        assertThat(comparator.compare(a, b)).isPositive();
        assertThat(comparator.compare(b, a)).isNegative();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("compare mixed number types without truncating a small but real difference to zero")
    void compareMixedTypesDoesNotTruncateSmallDifferenceToZero() {
        // When runtime types differ (e.g. Long fact vs Double value), the comparator falls back
        // to comparing doubleValue()s. The old buggy implementation computed
        // (int) (a.doubleValue() - b.doubleValue()), which truncates any difference smaller
        // than 1.0 down to zero, incorrectly reporting numbers as "equal".
        NumberRule.NumberComparator mixedComparator = new NumberRule.NumberComparator();

        assertThat(mixedComparator.compare(5L, 4.6)).isPositive();
        assertThat(mixedComparator.compare(4.6, 5L)).isNegative();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("compare mixed number types by falling back to double value")
    void compareMixedTypesFallsBackToDouble() {
        // Runtime types can differ from the declared type V (e.g. Integer fact vs Double value),
        // in which case the comparator falls back to comparing doubleValue()s.
        NumberRule.NumberComparator mixedComparator = new NumberRule.NumberComparator();

        assertThat(mixedComparator.compare(2, 1.5)).isPositive();
        assertThat(mixedComparator.compare(1, 1.5)).isNegative();
    }

    @Test
    @DisplayName("compare equal and null values")
    void compareEqualAndNullValues() {
        assertThat(comparator.compare(1L, 1L)).isZero();
        assertThat(comparator.compare(null, null)).isZero();
        assertThat(comparator.compare(null, 1L)).isNegative();
        assertThat(comparator.compare(1L, null)).isPositive();
    }

    @Test
    @DisplayName("be serializable")
    void isSerializable() {
        assertThat(comparator).isInstanceOf(Serializable.class);

        var bytes = new ByteArrayOutputStream();
        assertThatCode(() -> {
            try (var out = new ObjectOutputStream(bytes)) {
                out.writeObject(comparator);
            }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(in.readObject()).isInstanceOf(NumberRule.NumberComparator.class);
            }
        }).doesNotThrowAnyException();
    }
}
