/*
 *  Copyright 2025 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.results;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResultReducerTest {

    private static final Predicate<RuleResult> FAILED_RESULT_PREDICATE = resultTree -> resultTree.getResult() == Result.FAILED;
    private static final Predicate<RuleResult> VALID_RESULT_PREDICATE = resultTree -> resultTree.getResult() == Result.VALID;

    @ParameterizedTest
    @EnumSource(value = Result.class, names = {"FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should reduce non-FAILED result to an empty list")
    void reduceResultNotMatchingPredicate(Result result) {
        final var resultTree = ValuesRuleResult.builder()
                .with(vr -> {
                    vr.key = "foo";
                    vr.result = result;
                })
                .build();

        List<RuleResult> results = ResultReducer.reduce(resultTree, FAILED_RESULT_PREDICATE);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should reduce FAILED result to list with such result")
    void reduceFailedResult() {
        final var resultTree = ValuesRuleResult.builder()
                .with(vr -> {
                    vr.key = "foo";
                    vr.result = Result.FAILED;
                })
                .build();

        List<RuleResult> results = ResultReducer.reduce(resultTree, FAILED_RESULT_PREDICATE);

        assertThat(results)
                .hasSize(1)
                .contains(resultTree);
    }

    @Test
    @DisplayName("should reduce FAILED results from tree of combined rules")
    void reduceFailedResultFromCombinedRules() {
        final var resultTree = MultiaryBooleanRuleResult.builder()
                .with(r -> {
                    r.result = Result.FAILED;
                    r.rules = Set.of(
                            ValuesRuleResult.builder()
                                    .with(vr -> {
                                        vr.key = "foo";
                                        vr.result = Result.FAILED;
                                    })
                                    .build(),
                            ValuesRuleResult.builder()
                                    .with(vr -> {
                                        vr.key = "bar";
                                        vr.result = Result.VALID; // Skipped as predicate not satisfied
                                    })
                                    .build(),
                            MultiaryBooleanRuleResult.builder()
                                    .with(mr -> {
                                        mr.result = Result.FAILED;
                                        mr.rules = Set.of(
                                                ValuesRuleResult.builder()
                                                        .with(vr -> {
                                                            vr.key = "fab";
                                                            vr.result = Result.FAILED;
                                                        })
                                                        .build(),
                                                UnaryBooleanRuleResult.builder()
                                                        .with(ur -> {
                                                            ur.result = Result.FAILED;
                                                            ur.rule = ValuesRuleResult.builder()
                                                                    .with(uvr -> {
                                                                        uvr.key = "faz";
                                                                        uvr.result = Result.FAILED;
                                                                    })
                                                                    .build();
                                                        })
                                                        .build()
                                        );
                                    })
                                    .build(),
                            UnaryBooleanRuleResult.builder()
                                    .with(ur -> {
                                        ur.result = Result.FAILED;
                                        ur.rule = ValuesRuleResult.builder()
                                                .with(uvr -> {
                                                    uvr.key = "waz";
                                                    uvr.result = Result.FAILED;
                                                })
                                                .build();
                                    })
                                    .build()
                    );
                })
                .build();

        List<RuleResult> results = ResultReducer.reduce(resultTree, FAILED_RESULT_PREDICATE);

        assertThat(results)
                .hasSize(4)
                .extracting(x -> ((ValuesRuleResult<?>) x).getKey())
                .containsExactlyInAnyOrder("foo", "waz", "faz", "fab");
    }

    class TargetObject {
        public String name;
        public TargetObject(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("should reduce results and transform via function")
    void reduceWithTransformation() {
        final var resultTree = ValuesRuleResult.builder()
                .with(vr -> {
                    vr.key = "foo";
                    vr.result = Result.VALID;
                    vr.description = "This is a valid result!";
                })
                .build();

        List<TargetObject> results = ResultReducer.reduce(resultTree, VALID_RESULT_PREDICATE, x -> new TargetObject(x.getDescription()));

        assertThat(results)
                .singleElement()
                .extracting(x -> x.name)
                .isEqualTo("This is a valid result!");
    }
}
