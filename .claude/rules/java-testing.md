---
paths: [ "**/src/test/java/**" ]
---

# Java Testing Conventions

- Test method names must follow `should_<expected>_when_<condition>`convention
- Every test method body must be split into three explicitly commented sections,
  in this order: `// arrange`, `// act`, `// assert`.
- Tests for a given method under test must live in their own nested class (`@Nested`),
  one nested class per method.
- Write helper functions to avoid code duplication.
  Use them to set up the fixtures or create the objects needed for the test.

Example:

```java
@Test
void should_return_empty_set_and_skip_analysis_when_cycle_has_only_one_aircraft() {
    // arrange
    var a = pos("BA123", 0.0f, 20000.0f);

    // act
    var result = detector.detectInfringements(List.of(a), 1);

    // assert
    assertThat(result).isEmpty();
}
```