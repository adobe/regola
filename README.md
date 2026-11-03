# regola

[![Maven Central](https://img.shields.io/badge/Maven%20Central-latest-blue)](https://central.sonatype.com/artifact/com.adobe.abp/regola)
[![CI](https://github.com/adobe/regola/actions/workflows/ci.yml/badge.svg)](https://github.com/adobe/regola/actions/workflows/ci.yml)

**regola** is a rule evaluator written in Java.


**Disclaimer**: This library is in development mode and there could be breaking changes as new versions are released.

## Goals

- be fast
- be reusable and extensible
- be well documented
- have high test coverage
- allow for efficient evaluation against data retrieved from external data sources
- have Json rules conversion builtin in the library
- run on Java 11+

## Basic usage

1. Add to your maven dependencies:
```xml
<dependency>
  <groupId>com.adobe.abp</groupId>
  <artifactId>regola</artifactId>
  <version>0.0.24</version>
</dependency>
```

2. Write a rule

```java
var rule = new StringRule();
rule.setKey("MARKET_SEGMENT");
rule.setOperator(Operator.EQUALS);
rule.setValue("COM");
rule.setDescription("The market segment should be COM");
```

You could also use fluent setters:

```java
var rule = new StringRule()
    .setValue("COM")
    .setOperator(Operator.EQUALS)
    .setKey("MARKET_SEGMENT")
    .setDescription("The market segment should be COM");
```

For some rules, you could also pass some parameters directly via the constructor for conciseness:

```java
var rule = new StringRule("MARKET_SEGMENT", Operator.EQUALS, "COM");
rule.setDescription("The market segment should be COM");
```

3. Define how data for the "MARKET_SEGMENT" `key` must be retrieved

```java
var factsResolver = new SimpleFactsResolver<>();
factsResolver.addFact(new Fact<>("MARKET_SEGMENT", data -> "COM"));
```

4. Evaluate

```java
var evaluationResult = new Evaluator().evaluate(rule, factsResolver);

// The evaluation is an asynchronous process, so the associated CompletableFuture must be executed to get a result.
// The following line returns the result value when complete, or throws an (unchecked) exception if completed exceptionally.
evaluationResult.status().join(); 

var result = evaluationResult.snapshot();
```

The result object will contain information on whether the evaluation was valid or not, plus
any relevant information about the rule run.

5. If we were to print the `result` as json

```json5
{
  "result" : "VALID",
  "type" : "STRING",
  "operator" : "EQUALS",
  "key" : "MARKET_SEGMENT",
  "description": "The market segment should be COM",
  "expectedValue" : "COM",
  "actualValue": "COM"
}
```

## Rules Vocabulary

### Boolean Rules

Boolean rules are used to combine rules together.

#### And Rule

The "And Rule" is used to combine multiple rules together, 
where all the rules must evaluate to VALID for it to evaluate to VALID.

```json5
{
  "type" : "AND",
  "rules" : [
    // list of other rules
  ]
}
```

| A     | B                       | A && B                  |
|-------|-------------------------|-------------------------|
| VALID | VALID                   | VALID                   |
| VALID | INVALID                 | INVALID                 |
| VALID | MAYBE                   | MAYBE                   |
| VALID | FAILED                  | FAILED                  |
| VALID | OPERATION_NOT_SUPPORTED | OPERATION_NOT_SUPPORTED |

The AND rule is commutative: `A && B = B && A`.

An empty `AND` evaluates to `VALID`. This follows the identity of conjunction: with no subrules, there is nothing to invalidate the expression.

#### Or Rule

The "Or Rule" is used to combine multiple rules together, 
where at least one rule must evaluate to VALID for it to evaluate to VALID.

```json5
{
  "type" : "OR",
  "rules" : [
    // list of other rules
  ]
}
```

| A       | B       | A \|\| B |
|---------|---------|----------|
| VALID   | any     | VALID    |
| INVALID | INVALID | INVALID  |

The order of precedence for non-VALID results is: FAILED, OPERATION_NOT_SUPPORTED, INVALID, MAYBE.
So, for example: `FAILED || INVALID == FAILED`, while `MAYBE || INVALID == INVALID` and so on. 

The OR rule is commutative: `A || B = B || A`.

An empty `OR` evaluates to `INVALID`. This follows the identity of disjunction: with no subrules, there is nothing that can make the expression valid.

#### Short-circuiting

AND and OR stop as soon as their result is decided: an AND by its first subrule that is not VALID,
an OR by its first VALID subrule. Ignored subrules never decide the result.

Subrules are started in the order they are listed, without waiting for one another. Once the result
is decided:
- subrules that have not been started yet are skipped.
- subrules that are still running are cancelled.

Skipped and cancelled subrules report `MAYBE` and their actions do not run. With asynchronous data
fetchers, all subrules are usually started before any of them completes, so most of the saving comes
from cancellation.

You can also cancel a whole evaluation, for example when the caller no longer needs the result:

```java
final var evaluationResult = rule.evaluate(factsResolver);
final var status = evaluationResult.status();
// ...
evaluationResult.cancel();
```

`status` then completes with a `CancellationException`, the rules that had not completed report
`MAYBE`, and their actions do not run. Cancelling an evaluation that has already completed has no
effect.

##### What cancellation stops, and what it does not

Cancellation is best effort. It stops work that has not started yet; it does not interrupt work
that is already running.

Stopped:
- subrules that have not been started yet.
- fact fetches still waiting for a thread on the `SimpleFactsResolver` executor: the data fetcher is
  not called.
- rule checks still waiting for a thread on a leaf rule's executor.
- actions of cancelled rules.

Not stopped:
- a data fetch that has already started. It runs to completion and its result is still cached and
  shared with other rules that need the same data.
- fetches made without a `SimpleFactsResolver` executor. They start on the calling thread as soon as a
  rule needs the fact, so there is nothing left to skip.
- a rule check or an action that is already running when the cancellation arrives.

Rules cancel the future returned by a `FactsResolver` only if its `supportsCancellation()` returns
`true`, as `SimpleFactsResolver` does. If you write your own `FactsResolver`, it keeps working
unchanged: by default rules stop waiting for an unneeded fact but leave its future untouched, so a
future shared between callers is never cancelled. Override `supportsCancellation()` to return `true`
only if each `resolveFact` call returns a new future. If you write your own rule, override
`EvaluationResult.cancel()` to stop its work; the default does nothing.

A subrule whose fact cannot be fetched evaluates to `FAILED`, whether the data fetcher returns a
failed future or throws.

#### Not Rule

The "Not Rule" is used to negate the result of another rule.

```json5
{
  "type" : "NOT",
  "rule" : {
    // rule to negate
  }
}
```

| A                       | !A                      |
|-------------------------|-------------------------|
| VALID                   | INVALID                 |
| INVALID                 | VALID                   |
| MAYBE                   | MAYBE                   |
| FAILED                  | FAILED                  |
| OPERATION_NOT_SUPPORTED | OPERATION_NOT_SUPPORTED |

A `NOT` rule must contain a non-null `rule`. If the operand is missing, the evaluation is treated as malformed configuration and fails.

### Fact-only Rules

#### Exists Rule

The "Exists Rule" is used to check whether a fact exists or not.

```json5
{
  "type": "EXISTS",
  "key": "foo"
}
```

##### Some examples

| Key   | Fact                 | Result  |
|-------|----------------------|---------|
| "foo" | { "foo": "bar" }     | VALID   |
| "foo" | { "foo": null }      | INVALID |
| "foo" | { "not_foo": "bar" } | INVALID |

### Fixed Rules

#### CONSTANT Rule

The "Constant Rule" is used to always return the same result, regardless of the fact.

```json5
{
  "type": "CONSTANT",
  "result": "VALID" // INVALID, MAYBE, FAILED, OPERATION_NOT_SUPPORTED
}
```

### Value-based Rules

These rules evaluate facts against a value set in the rule.
When creating a value-based rule, you must also set an operator (e.g., EQUALS, GREATER_THAN, IN, etc...).

The relationship between facts, values and operators is: `fact OPERATOR value`.

So, for example a rule having value `Cat`, operator `EQUALS`, and evaluated against the fact `Dog` reads as: `Dog EQUALS Cat` (false).
A rule having value `Cat`, operator `CONTAINS`, and evaluated against the fact `[Dog, Bird, Cat]` reads as: `[Dog, Bird, Cat] CONTAINS Cat` (true).


#### Number Rule

The "Number Rule" is used to evaluate facts against a number.
The number can be an integer or a double.

```json5
{
  "type": "NUMBER",
  "operator": "GREATER_THAN",
  "key": "foo",
  "value": 7 // you can also have 7.0
}
```

**Supported operators**: EQUALS, GREATER_THAN, GREATER_THAN_EQUAL, LESS_THAN, LESS_THAN_EQUAL, CONTAINS, DIVISIBLE_BY

Integer-Double comparisons between the rule's value and the data provided by the fact work for all operators except `CONTAINS`.

The `DIVISIBLE_BY` operator is valid only for integer values since divisibility is not well-defined for floating-point numbers.

##### Some examples

| Rule Value | Operator           | Fact         | Result  |
|------------|--------------------|--------------|---------|
| 7          | EQUALS             | 7            | VALID   |
| 7          | GREATER_THAN       | 7            | INVALID |
| 7          | GREATER_THAN       | 8            | VALID   |
| 7          | GREATER_THAN_EQUAL | 7            | VALID   |
| 7          | GREATER_THAN_EQUAL | 7.5          | VALID   |
| 7.4        | GREATER_THAN       | 7.5          | VALID   |
| 7.5        | GREATER_THAN       | 7.5          | INVALID |
| 7          | CONTAINS           | [ 6, 7, 8]   | VALID   |
| 7          | CONTAINS           | [ 6, 8]      | INVALID |
| 7          | CONTAINS           | [ 6, 7.0, 8] | INVALID |
| 7.0        | CONTAINS           | [ 6, 7.0, 8] | VALID   |
| 7          | DIVISIBLE_BY       | 7            | VALID   |
| 7          | DIVISIBLE_BY       | 8            | INVALID |
| 0          | DIVISIBLE_BY       | 8            | FAILED  |
| 7          | DIVISIBLE_BY       | 0            | VALID   |
| any number | supported operator | null         | INVALID |
| null       | supported operator | any number   | INVALID |

When using the CONTAINS operator, the Fact must be a `Set` of numbers.

#### String Rule

```json5
{
  "type": "STRING",
  "operator": "EQUALS",
  "key": "foo",
  "value": "bar"
}
```

**Supported operators**: EQUALS, GREATER_THAN, GREATER_THAN_EQUAL, LESS_THAN, LESS_THAN_EQUAL, CONTAINS

Comparisons are case-sensitive.

##### Some examples

| Rule Value | Operator           | Fact                  | Result  |
|------------|--------------------|-----------------------|---------|
| "bar"      | EQUALS             | "bar"                 | VALID   |
| "bar"      | EQUALS             | "BAR"                 | INVALID |
| "bar"      | EQUALS             | "baz"                 | INVALID |
| "bar"      | GREATER_THAN       | "car"                 | VALID   |
| "bar"      | GREATER_THAN_EQUAL | "bar"                 | VALID   |
| "bar"      | GREATER_THAN       | "are"                 | INVALID |
| "bar"      | STARTS_WITH        | "bar"                 | VALID   |
| "bar"      | STARTS_WITH        | "barfoo"              | VALID   |
| "bar"      | STARTS_WITH        | "foobar"              | INVALID |
| "bar"      | ENDS_WITH          | "bar"                 | VALID   |
| "bar"      | ENDS_WITH          | "foobar"              | VALID   |
| "bar"      | ENDS_WITH          | "barfoo"              | INVALID |
| "bar"      | CONTAINS           | ["are", "bar", "baz"] | VALID   |
| "bar"      | CONTAINS           | ["are", "baz"]        | INVALID |
| any string | supported operator | null                  | INVALID |
| null       | supported operator | any string            | INVALID |

#### Set Rule

A "Set rule" is a rule that evaluates a fact against a set of values.
The set rule has two operators: IN and INTERSECTS.

IN: evaluates to VALID if the fact is a subset of the rule's value.
INTERSECTS: evaluates to VALID if the fact and the rule's value have at least one item in common.

```json5
{
  "type": "SET",
  "operator": "IN",
  "key": "foo",
  "values": ["bar", "baz"]
}
```

**Supported operators**: IN, INTERSECTS

String comparisons are case-sensitive.

##### Some examples

| Rule Value     | Operator     | Fact           | Result  |
|----------------|--------------|----------------|---------|
| ["bar", "baz"] | IN           | "bar"          | VALID   |
| ["bar", "baz"] | IN           | "waz"          | INVALID |
| [1, 2, 3]      | IN           | 2              | VALID   |
| [1, 2, 3]      | IN           | 4              | INVALID |
| ["bar", "baz"] | IN           | []             | INVALID |
| []             | IN           | []             | VALID   |
| []             | IN           | ["bar"]        | INVALID |
| ["bar", "baz"] | IN           | ["bar"]        | VALID   |
| ["bar", "baz"] | IN           | ["waz"]        | INVALID |
| ["bar", "baz"] | IN           | ["bar", "waz"] | INVALID |
| ["bar", "baz"] | INTERSECTS   | "bar"          | VALID   |
| ["bar", "baz"] | INTERSECTS   | "waz"          | INVALID |
| ["bar", "baz"] | INTERSECTS   | ["bar", "waz"] | VALID   |
| ["bar", "baz"] | INTERSECTS   | ["wiz", "waz"] | INVALID |
| ["bar", "baz"] | INTERSECTS   | []             | INVALID |
| []             | INTERSECTS   | []             | INVALID |
| any set        | any operator | null           | INVALID |

##### Comparing complex objects

The Set rule can be used on more complex objects too. 

```java
SetRule<Patient> setRule = new SetRule<>();
setRule.setKey("PATIENT");
setRule.setOperator(Operator.IN);
setRule.setValues(bob, alice);
```

Where `bob` and `alice` are instances of `Patient`.
You must also make sure that the `Patient` class overrides the `equals` and `hashCode` methods.
Regola expects the `equals` method to perform a commutative comparison between objects.

Also note that regola does not support JSON serialization/deserialization for SET rules with complex objects.


#### Date Rule

The "Date Rule" is a rule that evaluates a fact against a date value.

```json5
{
  "type" : "DATE",
  "operator" : "GREATER_THAN",
  "key" : "foo",
  "value" : "2021-07-07T12:30:00Z"
}
```

**Supported operators**: EQUALS, GREATER_THAN, GREATER_THAN_EQUAL, LESS_THAN, LESS_THAN_EQUAL, CONTAINS

Dates must be parsable to an `OffsetDateTime`:

> A date-time with an offset from UTC/Greenwich in the ISO-8601 calendar system

##### Some examples

| Rule Value             | Operator           | Fact                   | Result  |
|------------------------|--------------------|------------------------|---------|
| "2021-07-07T12:30:00Z" | EQUALS             | "2021-07-07T12:30:00Z" | VALID   |
| "2021-07-07T12:30:00Z" | GREATER_THAN       | "2022-07-07T12:30:00Z" | VALID   |
| "2021-07-07T12:30:00Z" | GREATER_THAN       | "2020-07-07T12:30:00Z" | INVALID |
| "2021-07-07T12:30:00Z" | LESS_THAN          | "2020-07-07T12:30:00Z" | VALID   |
| any date               | supported operator | null                   | INVALID |
| null                   | supported operator | any date               | INVALID |


#### Null Rule

A "Null Rule" is a rule that evaluates a fact against a null value.

```json5
{
  "type": "NULL",
  "key": "foo"
}
```

##### Some examples

| Key   | Fact  | Result  |
|-------|-------|---------|
| "foo" | null  | VALID   |
| "foo" | "foo" | INVALID |

### Combining Rules

Rules can be combined using the boolean rules: AND, OR, NOT.

```json5
{
  "type" : "AND",
  "rules" : [ {
    "type" : "STRING",
    "operator" : "EQUALS",
    "key" : "foo",
    "value" : "bar"
  }, {
    "type" : "OR",
    "rules" : [ {
      "type" : "EXISTS",
      "key" : "waz"
    }, {
      "type" : "NUMBER",
      "operator" : "EQUALS",
      "key" : "foobar",
      "value" : 21
    }]
  }]
}
```

#### Ignoring results

Rules can be set to be ignored, so that the evaluation of AND/OR/NOT rules does not take them into account.

This matters for empty or effectively-empty boolean rules too:

- `AND` with no subrules evaluates to `VALID`.
- `OR` with no subrules evaluates to `INVALID`.
- `OR` where every subrule is present but marked as `ignored` evaluates to `VALID`, because all configured subrules are excluded from the final decision.
- `NOT` with an ignored subrule evaluates to `VALID`.
- `NOT` with no subrule fails.

Example of a rule marked as `ignored`:

```json5
{
  "type" : "STRING",
  "operator" : "EQUALS",
  "key" : "foo",
  "value" : "bar",
  "ignored" : true
}
```

This is useful when you want to run a rule but not have it affect the evaluation of the tree.

For example, the following tree evaluated to `VALID` even thought one of the subrules of `AND` was `INVALID`:

```json5
{
  "result": "VALID",
  "type": "AND",
  "description": "Example of a Composite Rule with subrules ignored", // Optional, can be added to any rule in the tree
  "ignored": false,
  "rules": [
    {
      "result": "INVALID",
      "type": "STRING",
      "ignored": true,
      "operator": "EQUALS",
      "key": "foo",
      "expectedValue": "bar",
      "actualValue": "not_bar"
    },
    {
      "result": "VALID",
      "type": "EXISTS",
      "ignored": false,
      "key": "foo",
      "expectedValue": "<any>",
      "actualValue": "not_bar"
    }
  ]
}
```

### Json Deserialization with Jackson

You can use [Jackson](https://github.com/FasterXML/jackson) to deserialize rules from regola.
These are the dependencies you will need:

```xml
<!-- pom.xml -->
<properties>
    <jackson.version>2.13.1</jackson.version>
</properties>

<depdendencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-core</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>${jackson.version}</version>
    </dependency>
</depdendencies>
```

And this is the minimum setup for your ObjectMapper.

```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .registerModule(new RuleModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

Then you can deserialize a rule as such:

```java
String jsonRule = readRuleFromDataSource(); // This method will vary depending on your application
Rule rule = mapper.readValue(jsonRule, Rule.class);
```

### New rules

1. Create a new Rule by extending `Rule` (or one of the provided abstract classes)

```java
public class YourRule extends Rule {
    public YourRule() {
        super("YOUR_TYPE"); // You should make sure this does not conflict with the type of any existing rule
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;

            @Override
            public RuleResult snapshot() {
                // Build and return a RuleResult
            }

            @Override
            public CompletableFuture<Result> status() {
                return facts.resolveFact(getKey())
                        .thenCompose(fact -> CompletableFuture.supplyAsync(() -> {
                            result = ... // perform the relevant checks for this rule and update the result
                            return result;
                        }))
                        .exceptionally(throwable -> {
                            result = Result.FAILED;
                            return result;
                        });
            }
        };
    }
}
```

2. (Optional) If you need to parse rules from Json, then you must extend the `RuleModule` as such:

```java
mapper.registerModule(new RuleModule()
    .addRule("YOUR_TYPE", YourRule.class));
```

3. Start using your new rule!

### Programmatic Rule Creation

While transforming rules from Json is convenient, sometimes you may want to create rules programmatically.

Here is an example of how to do that:

```java
SetRule<String> stringSetRule = new SetRule<>();
stringSetRule.setKey("MARKET_SEGMENT");
stringSetRule.setOperator(Operator.IN);
stringSetRule.setValues(Set.of("COM", "EDU"));

NumberRule<Integer> numberRule = new NumberRule<>();
numberRule.setKey("capacity");
numberRule.setOperator(Operator.EQUALS);
numberRule.setValue(3);

OrRule orRule = new OrRule();
orRule.setRules(List.of(numberRule, stringSetRule));
```

## Facts

Now that we have got some rules, we want to do something with it.

We do that by creating **facts** and supplying those to the **evaluator** which will check whether
they satisfy our rule or not.

An example of a fact for the `foo` data point is:

```java
var fact = new Fact<>("foo", data -> "bar");
```

In regola, we can also write facts that use custom **data fetchers** to retrieve additional data:

```java
Fact<Offer> fact = new Fact<>("segment", CustomDataSources.OFFER, Offer::getSegment);
```

### The FactsResolver

Defining **facts**, if using a custom data source, is not enough. 
We must tell our evaluator how to get those facts when a rule is run.
This is done using a `FactsResolver` as shown below:

```java
Map<DataSource, DataFetcher<?, YourContext>> dataFetchers = Map.of(
        CustomDataSource.OFFER, offerDataFetcher,
        ...
);
FactsResolver factsResolver = new SimpleFactsResolver<>(yourContext, dataFetchers);
factsResolver.addFact(new Fact<>("segment", CustomDataSources.OFFER, Offer::getSegment));
```

### Writing a Data Fetcher

Example of a data fetcher getting data over HTTP:

```java
public class OfferDataFetcher implements DataFetcher<Offer, YourContext> {

    private final OfferHttpConnector offerHttpConnector;
    private final Executor ioExecutor;

    public OfferDataFetcher(OfferHttpConnector offerHttpConnector, Executor ioExecutor) {
        this.offerHttpConnector = offerHttpConnector;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public CompletableFuture<FetchResponse<Offer>> fetchResponse(YourContext context) {
        GetOffersRequest request = buildRequest(context);
        // The connector blocks, so run it on an executor you own, not the common ForkJoinPool.
        // See "Executors and blocking data fetchers" below.
        return CompletableFuture.supplyAsync(() -> {
            final var response = new FetchResponse<>();
            response.setData(offerHttpConnector.getOffers(request)
                    .stream()
                    .findFirst());
            return response;
        }, ioExecutor);
    }

    // Do not override this method if you do not want to cache results from this data fetcher.
    @Override
    public String calculateRequestKey(YourContext context) {
        GetOffersRequest request = buildRequest(context);
        return request.URL().toString();
    }

    private Request buildRequest(YourContext context) {
        return new GetOffersRequest(Set.of(requestContext.getOfferId()), Set.of());
    }
}

public class YourContext implements Context {
    private String offerId;
    
    public String getOfferId() {
        return offerId;
    }
    
    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }
}
```

The initialization of a data fetcher can be expensive, depending on your implementation, 
so it is recommended that data fetchers are re-used across multiple evaluations.

#### Caching in the Data Fetcher

The abstract **DataFetcher** uses [caffeine](https://github.com/ben-manes/caffeine) to cache the results of the fetch results.

The default cache is setup with an **expiry policy of 1 minute** and **max size of 1_000 entries**,
but a custom configuration can be setup by passing a `DataFetcherConfiguration` to the `DataFetcher`'s constructor.

##### Custom Cache

If the default caffeine-based cache does not satisfy your requirements, you can provide your own implementation.

First, create a class for your custom cache:

```java
class YourCustomCache<V> implements DataFetcherCache<V> {

    public YourCustomCache(DataCacheConfiguration configuration) {
        // (optional) construct your cache using the given configuration
    }
    
    @Override
    public CompletableFuture<V> get(String key, Function<String, CompletableFuture<V>> mappingFunction) {
        // implement your "if cached, return; otherwise create, cache and return" cache function here
    }
}
```

Then pass an instance of your custom cache to your data fetchers:

```java
public class OfferDataFetcher implements DataFetcher<Offer, YourContext> {

    private final OfferHttpConnector offerHttpConnector;

    public OfferDataFetcher(OfferHttpConnector offerHttpConnector, YourCustomCache<Offer> cache) {
        super(cache);
        this.offerHttpConnector = offerHttpConnector;
    }
    
    // rest of this data fetcher implementation
}
```

#### SLA handling within the Data Fetcher

By default, your custom data fetcher will not have any SLA failure handling.
However, if needed you can specify an SLA on the fetch `requestTime` and override the
`whenFailingSlaFetchTime` to handle any SLA failures.

```java
public class OfferDataFetcher implements DataFetcher<Offer, YourContext> {

    public OfferDataFetcher(/* other params */, long slaFetchTime) {
        super(new DataFetcherConfiguration().setSlaFetchTime(slaFetchTime));
        // any other initialization
    }

    @Override
    public void whenFailingSlaFetchTime(String requestKey, long slaFetchTime, double requestTime) {
        // This method gets called whenever "requestTime > slaFetchTime"
    }
}
```

### Executors and blocking data fetchers

Evaluation is asynchronous, but regola does not create or manage threads of its own.
You can supply an executor in three places:

| Executor | Supplied via | Use it to |
|----------|--------------|-----------|
| **Resolver** | `new SimpleFactsResolver<>(context, dataFetchers, executor)` | Run your data fetchers off the thread that starts the evaluation. |
| **Cache** | `new DataCacheConfiguration().setExecutor(executor)` | Control where the default cache loads data. |
| **Rule** | e.g. `new SetRule<>(executor)`, `new StringRule(executor)` | Control where a rule checks a fact once it is available. |

If your data fetchers may block, supply a **resolver** executor: it is the only one of
the three that protects evaluation from blocking data fetchers.

#### The blocking contract

- `fetchResponse(...)` should return promptly whenever practical, placing blocking or
  long-running work in the returned future, on an executor you own.
- `AND` and `OR` rules start their children one after the other on the calling thread.
  If a data fetcher blocks before returning its future, and no resolver executor is
  supplied, the children are effectively fetched one at a time instead of concurrently.
- Supply a resolver executor whenever a data fetcher may do synchronous work, including
  an expensive `calculateRequestKey` or a custom `DataCache` that blocks.
- Avoid `ForkJoinPool.commonPool()` for blocking I/O: it is small, shared with the rest
  of the JVM, and is what regola falls back to when an executor is not supplied.
- Keep `Fact` functions cheap: they run on the thread that completes the fetch.

```java
Executor fetchExecutor = ...; // an executor you own

FactsResolver factsResolver = new SimpleFactsResolver<>(yourContext, dataFetchers, fetchExecutor);
```

Regola never shuts down an executor you pass to it. You own its lifecycle: create it
once, share it across evaluations, and close it when your application stops.

## Actions

Actions are used to define operations we want to perform after a rule is evaluated.

### Basic usage

```java
var action = new Action()
    .setDescription("Print 'Hello' if VALID")
    .setOnCompletion((result, throwable, ruleResult) -> {
        if (result == Result.VALID) {
            System.out.println("Hello");
        }
    });
rule.setAction(action);
```

In this particular example, this action will be executed when the rule is evaluated and the result is `VALID`.

An action does not run for a subrule that was skipped or cancelled by [short-circuiting](#short-circuiting).

### Chaining

It is possible to chain actions using the `andThen` method on the `TriConsumer`:

```java
TriConsumer<Result, Throwable, RuleResult> actionConsumer = (result, throwable, ruleResult) -> {
    if (result == Result.VALID) {
        System.out.println("Hello");
    }
};

// Chain the action to always print "World"
actionConsumer = actionConsumer.andThen((result, throwable, ruleResult) -> System.out.println("World"));

var action = new Action()
    .setDescription("Print 'Hello' if VALID and the word 'World' irrespective of result")
    .setOnCompletion(actionConsumer);
rule.setAction(action);
```

## Understanding the Results of a Rule Evaluation

The following is an example of a result (pretty printed in json) returned upon evaluating a tree of rules:

```json5
{
  "result": "VALID",
  "type": "AND",
  "description": "Example of a Composite Rule", // Optional, can be added to any rule in the tree
  "ignored": false,
  "rules": [
    {
      "result": "VALID",
      "type": "STRING",
      "ignored": false,
      "operator": "EQUALS",
      "key": "foo",
      "expectedValue": "bar",
      "actualValue": "bar"
    },
    {
      "result": "VALID",
      "type": "OR",
      "ignored": false,
      "rules": [
        {
          "result": "VALID",
          "type": "EXISTS",
          "ignored": false,
          "key": "waz",
          "expectedValue": "<any>", // <any> is a special keyword matching any actual value for the EXISTS rule
          "actualValue": "wazab"
        },
        {
          "result": "MAYBE",
          "type": "NUMBER",
          "ignored": false,
          "operator": "EQUALS",
          "key": "foobar",
          "expectedValue": 21,
          // no actual value for MAYBE, since the rule was not evaluated due to short-circuiting
        }
      ]
    }
  ]
}
```

The top-level `"result"` is the overall result of the rule.
- If VALID, the subresults must be all VALID or MAYBE (i.e., rule did not need to be evaluated due to short-circuiting).
- If not VALID, one or more of the subresults are: INVALID, OPERATION_NOT_SUPPORTED or FAILED.

## Oh, by the way, what does "regola" mean?

**regola** is the italian word for **rule**.
