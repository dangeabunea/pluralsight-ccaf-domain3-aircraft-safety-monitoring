# Frontend testing conventions

- **IMPORTANT: `it(...)` titles must follow `should <expected> when <condition>`** — space-separated words, not the Java underscore style used in backend tests. Do not mix the two conventions across languages.
- Every test body must be split into three explicitly commented sections, in this order: `// arrange`, `// act`, `// assert`.
- Tests must be grouped with `describe`, one `describe` block per action/scenario being tested.

Example:

```ts
describe('some action', () => {
  it('should issue GET request', () => {
    // arrange
    const expected: EventSummary = { pendingCount: 3, escalatedCount: 1 };
    let actual: EventSummary | undefined;

    // act
    service.getSummary().subscribe(s => (actual = s));

    // assert
    const req = controller.expectOne('/api/v1/events/summary');
    expect(req.request.method).toBe('GET');
    req.flush(expected);
    expect(actual).toEqual(expected);
  });
});
```
