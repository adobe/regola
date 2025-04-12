/*
 *  Copyright 2023 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola;

import com.adobe.abp.regola.facts.Fact;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.facts.SimpleFactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.rules.AndRule;
import com.adobe.abp.regola.rules.DateRule;
import com.adobe.abp.regola.rules.ExistsRule;
import com.adobe.abp.regola.rules.MultiaryBooleanRule;
import com.adobe.abp.regola.rules.ConstantRule;
import com.adobe.abp.regola.rules.NotRule;
import com.adobe.abp.regola.rules.NullRule;
import com.adobe.abp.regola.rules.NumberRule;
import com.adobe.abp.regola.rules.Operator;
import com.adobe.abp.regola.rules.OrRule;
import com.adobe.abp.regola.rules.RangeRule;
import com.adobe.abp.regola.rules.Rule;
import com.adobe.abp.regola.rules.RuleType;
import com.adobe.abp.regola.rules.SetRule;
import com.adobe.abp.regola.rules.StringRule;
import com.adobe.abp.regola.rules.UnaryBooleanRule;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

public class FuzzyTestCaseGenerator {

    private final Random randomizer = new Random();
    private final List<RuleType> BOOLEAN_RULES = List.of(RuleType.AND, RuleType.OR, RuleType.NOT);
    private final List<RuleType> DATA_RULES = List.of(RuleType.NUMBER, RuleType.SET, RuleType.STRING, RuleType.EXISTS,
            RuleType.DATE, RuleType.NULL, RuleType.CONSTANT, RuleType.RANGE);
    private static final int MAX_NESTING_LEVEL = 20;
    private int nestingLevel = 0;
    private static final int MAX_NUMBER_SUBRULES = 20;

    private final List<OffsetDateTime> DATES = List.of(
            OffsetDateTime.parse("2020-01-03T08:00:00Z", ISO_DATE_TIME),
            OffsetDateTime.parse("2020-06-03T08:00:00Z", ISO_DATE_TIME),
            OffsetDateTime.parse("2020-12-03T08:00:00Z", ISO_DATE_TIME),
            OffsetDateTime.parse("2020-12-01T08:00:00Z", ISO_DATE_TIME),
            OffsetDateTime.parse("2020-12-05T08:00:00Z", ISO_DATE_TIME)
    );

    private FactsResolver resolver;

    public Rule generate() {
        resolver = new SimpleFactsResolver<>(); // reset on generate
        return generateBooleanRule();
    }

    public FactsResolver getFactsResolver() {
        return resolver;
    }

    public Rule generateBooleanRule() {
        nestingLevel++;
        RuleType pickedRuleType = BOOLEAN_RULES.get(randomizer.nextInt(BOOLEAN_RULES.size()));
        switch (pickedRuleType) {
            case AND:
            case OR:
                return generateMultiaryRule(pickedRuleType);
            case NOT:
                return generateNotRule();
            default:
                throw new IllegalArgumentException(pickedRuleType + " is invalid");
        }
    }

    public MultiaryBooleanRule generateMultiaryRule(RuleType pickedRuleType) {
        MultiaryBooleanRule rule = pickedRuleType == RuleType.AND ? new AndRule() : new OrRule();
        int numberOfSubrules = getRandomNumber(1, MAX_NUMBER_SUBRULES);
        List<Rule> subrules = new ArrayList<>();
        for (int i = 0; i < numberOfSubrules; i++) {
            subrules.add(generateRandomRule());
        }
        rule.setRules(subrules);
        return rule;
    }

    public UnaryBooleanRule generateNotRule() {
        return new NotRule(generateRandomRule());
    }

    public Rule generateRandomRule() {
        List<RuleType> allRules;
        if (nestingLevel < MAX_NESTING_LEVEL) {
            allRules = Stream.concat(BOOLEAN_RULES.stream(), DATA_RULES.stream())
                    .collect(Collectors.toList());
        } else {
            allRules = DATA_RULES;
        }

        RuleType pickedRuleType = allRules.get(randomizer.nextInt(allRules.size()));
        switch (pickedRuleType) {
            case AND:
            case OR:
            case NOT:
                // will actually re-pick the rule among AND, OR, NOT, but that is fine
                return generateBooleanRule();
            case NUMBER:
                return generateNumberRule();
            case STRING:
                return generateStringRule();
            case EXISTS:
                return generateExistsRule();
            case DATE:
                return generateDateRule();
            case SET:
                return generateSetRule();
            case NULL:
                return generateNullRule();
            case CONSTANT:
                return generateConstantRule();
            case RANGE:
                return generateRangeRule();
            default:
                throw new IllegalArgumentException(pickedRuleType + " is invalid");
        }
    }

    public NumberRule<?> generateNumberRule() {
        NumberRule<Integer> rule = new NumberRule<>();
        rule.setOperator(pickRandomOperator());
        final var key = getRandomLetter(5);
        final var value = getRandomNumber(1, 10);
        resolver.addFact(new Fact<>(key, data -> randomizer.nextBoolean() ? value : getRandomNumber(1, 10)));

        rule.setKey(key);
        rule.setValue(value);
        return rule;
    }

    public StringRule generateStringRule() {
        StringRule rule = new StringRule();
        rule.setOperator(pickRandomOperator());
        final var key = getRandomLetter(5);
        final var value = getRandomLetter(1);
        resolver.addFact(new Fact<>(key, data -> randomizer.nextBoolean() ? value : getRandomLetter(1)));

        rule.setKey(key);
        rule.setValue(value);

        return rule;
    }

    public ExistsRule generateExistsRule() {
        ExistsRule rule = new ExistsRule();
        final var key = getRandomLetter(5);
        resolver.addFact(new Fact<>(randomizer.nextBoolean() ? key : getRandomLetter(5), data -> "whatever value"));

        rule.setKey(key);

        return rule;
    }

    public DateRule generateDateRule() {
        DateRule rule = new DateRule();
        rule.setOperator(pickRandomOperator());
        final var key = getRandomLetter(5);
        final var value = getRandomDate();
        resolver.addFact(new Fact<>(key, data -> randomizer.nextBoolean() ? value : getRandomDate()));

        rule.setKey(key);
        rule.setValue(value);
        return rule;
    }

    public SetRule<?> generateSetRule() {
        SetRule<Integer> rule = new SetRule<>();
        rule.setOperator(pickRandomOperator());
        final var key = getRandomLetter(5);

        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            numbers.add(getRandomNumber(1, 100)); // repetition wil be cancelled out, which is fine
        }

        resolver.addFact(new Fact<>(key, data -> randomizer.nextBoolean() ?
                numbers.get(randomizer.nextInt(numbers.size())) :
                getRandomNumber(1, 10)));

        rule.setKey(key);
        rule.setValues(Set.copyOf(numbers));
        return rule;
    }

    public NullRule generateNullRule() {
        NullRule rule = new NullRule();
        final var key = getRandomLetter(5);
        resolver.addFact(new Fact<>(randomizer.nextBoolean() ? key : getRandomLetter(5),
                randomizer.nextBoolean() ? data -> null : data -> "whatever value"));

        rule.setKey(key);

        return rule;
    }

    public ConstantRule generateConstantRule() {
        return new ConstantRule(pickRandomResult());
    }

    public RangeRule<?> generateRangeRule() {
        RangeRule<Integer> rule = new RangeRule<>();
        rule.setOperator(pickRandomOperator());

        final var key = getRandomLetter(5);
        final var min = getRandomNumber(-200, 200);
        final var max = getRandomNumber(-200, 200);

        resolver.addFact(new Fact<>(key, data -> randomizer.nextInt(600) - 300));

        rule.setKey(key);
        rule.setMin(min);
        rule.setMax(max);
        rule.setMinExclusive(randomizer.nextBoolean());
        rule.setMaxExclusive(randomizer.nextBoolean());

        return rule;
    }

    public Result pickRandomResult() {
        return Result.values()[randomizer.nextInt(Result.values().length)];
    }

    public Operator pickRandomOperator() {
        return Operator.values()[randomizer.nextInt(Operator.values().length)];
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public String getRandomLetter(int length) {
        return RandomStringUtils.secure().nextAlphabetic(length);
    }

    public OffsetDateTime getRandomDate() {
        return DATES.get(randomizer.nextInt(DATES.size()));
    }
}
