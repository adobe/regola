# regola

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

1. Write a rule

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

But because of inheritance, you need to make sure that the fluent setters are called in the right order.

For some rules, you could also pass some parameters directly via the constructor for conciseness:

```java
var rule = new StringRule("MARKET_SEGMENT", Operator.EQUALS, "COM");
rule.setDescription("The market segment should be COM");
```

2. Define how data for the "MARKET_SEGMENT" `key` must be retrieved

```java
var factsResolver = new SimpleFactsResolver<>();
factsResolver.addFact(new Fact<>("MARKET_SEGMENT", data -> "COM"));
```

3. Evaluate

```java
var evaluationResult = new Evaluator().evaluate(rule, factsResolver);

// The evaluation is an asynchronous process, so the associated CompletableFuture must be executed to get a result.
// The following line returns the result value when complete, or throws an (unchecked) exception if completed exceptionally.
evaluationResult.status().join(); 

var result = evaluationResult.snapshot();
```

The result object will contain information on whether the evaluation was valid or not, plus
any relevant information about the rule run.

4. If we were to print the `result` as json

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
| ----- | ----------------------- | ----------------------- |
| VALID | VALID                   | VALID                   |
| VALID | INVALID                 | INVALID                 |
| VALID | MAYBE                   | MAYBE                   |
| VALID | FAILED                  | FAILED                  |
| VALID | OPERATION_NOT_SUPPORTED | OPERATION_NOT_SUPPORTED |

The AND rule is commutative: `A && B = B && A`.

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
| ------- | ------- | -------- |
| VALID   | any     | VALID    |
| INVALID | INVALID | INVALID  |

The order of precedence for non-VALID results is: FAILED, OPERATION_NOT_SUPPORTED, INVALID, MAYBE.
So, for example: `FAILED || INVALID == FAILED`, while `MAYBE || INVALID == INVALID` and so on. 

The OR rule is commutative: `A || B = B || A`.

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
| ----------------------- | ----------------------- |
| VALID                   | INVALID                 |
| INVALID                 | VALID                   |
| MAYBE                   | MAYBE                   |
| FAILED                  | FAILED                  |
| OPERATION_NOT_SUPPORTED | OPERATION_NOT_SUPPORTED |

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
| ----- | -------------------- | ------- |
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

**Supported operators**: EQUALS, GREATER_THAN, GREATER_THAN_EQUAL, LESS_THAN, LESS_THAN_EQUAL, CONTAINS

Integer-Double comparisons between the rule's value and the data provided by the fact work for all operators except `CONTAINS`.

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
| ---------- | ------------------ | --------------------- | ------- |
| "bar"      | EQUALS             | "bar"                 | VALID   |
| "bar"      | EQUALS             | "BAR"                 | INVALID |
| "bar"      | EQUALS             | "baz"                 | INVALID |
| "bar"      | GREATER_THAN       | "car"                 | VALID   |
| "bar"      | GREATER_THAN_EQUAL | "bar"                 | VALID   |
| "bar"      | GREATER_THAN       | "are"                 | INVALID |
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
  "value": ["bar", "baz"]
}
```

**Supported operators**: IN, INTERSECTS

String comparisons are case-sensitive.

##### Some examples

| Rule Value     | Operator     | Fact           | Result  |
| -------------- | ------------ |----------------|---------|
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
| ---------------------- | ------------------ | ---------------------- | ------- |
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

| Key   | Fact                 | Result  |
| ----- |----------------------| ------- |
| "foo" | null                 | VALID   |
| "foo" | "foo"                | INVALID |

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
        super("YOUR_TYPE"); // You should make sure this does not conflict with the type of an existing rule
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
Fact<Offer> fact = new Fact<>("segment", CustomDataSources.OFFER, Offer::getSegment)
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

    public OfferDataFetcher(OfferHttpConnector offerHttpConnector) {
        this.offerHttpConnector = offerHttpConnector;
    }

    @Override
    public CompletableFuture<FetchResponse<Offer>> fetchResponse(YourContext context) {
        GetOffersRequest request = buildRequest(context);
        return CompletableFuture.supplyAsync(() -> {
            final var response = new FetchResponse<>();
            response.setData(offerHttpConnector.getOffers(request)
                    .stream()
                    .findFirst());
            return response;
        });
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

## Actions

Actions are used to define operations we want to perform when a rule is evaluated.

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

### Chaining

Chaining actions is possible using the `andThen` method on the `TriConsumer`:

```java
TriConsumer<Result, Throwable, RuleResult> actionConsumer = (result, throwable, ruleResult) -> {
    if (result == Result.VALID) {
        System.out.println("Hello");
    }
};
actionConsumer = actionConsumer.andThen((result, throwable, ruleResult) -> System.out.println("World"));

var action = new Action()
    .setDescription("Print 'Hello' if VALID and the word 'World' irrespective of result")
    .setOnCompletion(actionConsumer);
rule.setAction(action);
```

## Understanding the Results

The following is an example of a result (pretty printed in json) returned upon evaluating a tree of rules:

```json5
{
  "result": "VALID",
  "type": "AND",
  "rules": [
    {
      "result": "VALID",
      "type": "STRING",
      "operator": "EQUALS",
      "key": "foo",
      "expectedValue": "bar",
      "actualValue": "bar"
    },
    {
      "result": "VALID",
      "type": "OR",
      "rules": [
        {
          "result": "VALID",
          "type": "EXISTS",
          "key": "waz",
          "expectedValue": "<any>", // <any> is the special keyword matching any actual value for the EXISTS rule
          "actualValue": "wazab"
        },
        {
          "result": "MAYBE",
          "type": "NUMBER",
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

## Building and Releasing

### Building

To build/release, set the following shell variables:

```sh
ARTIFACTORY_USER=ldapusername
ARTIFACTORY_API_TOKEN=<artifactory token>
```

### Pre-Merge Checks

Before merging the following should be run to ensure that a release would be successful from a build, test and documentation point of view:

```sh
mvn clean install
```

### Releasing new versions

To produce a release you should:

- Switch to the `main` branch and ensure you have the latest HEAD revision
- Run `mvn release:prepare --settings .mvn/settings.xml`, accepting the defaults
- Run `mvn release:perform --settings .mvn/settings.xml`.

This will:
- Drop the `-SNAPSHOT` qualifier from the version number
- Create a tag in git
- Push the commit and tag to Github
- Publish the artefacts to the `maven-abp-toolbox-release` repository
- Increase the version number and add the SNAPSHOT qualifier


## Oh, by the way, what does "regola" mean?

**regola** is the italian word for **rule**.
