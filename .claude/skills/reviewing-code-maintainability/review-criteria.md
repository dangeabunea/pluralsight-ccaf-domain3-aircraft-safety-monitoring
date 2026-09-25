# Review criteria

- **Naming and intent**: names that hide or mislead about what the code does;
  logic that needs a comment only because the code isn't clear
- **Size and responsibility**: methods or classes doing more than one job;
  deep nesting; long parameter lists; mixed levels of abstraction
- **Complexity**: speculative generality, needless indirection, magic
  numbers or strings, dead code, commented-out code
- **Duplication**: copy-pasted logic; parallel structures (e.g. two switch
  statements over the same enum) that will silently drift apart; the same
  business rule or constant defined in more than one place
- **Coupling and cohesion**: feature envy (a method that mostly operates on
  another class's data); inappropriate intimacy between classes; law-of-
  Demeter violations (long chains reaching through objects to get to other
  objects); circular dependencies; a class that changes for more than one
  reason
- **Boundaries and dependency direction**: code that reaches across a
  layer or module boundary it shouldn't (e.g. domain code depending on
  infrastructure, or a lower layer depending on a higher one); abstractions
  that leak implementation details (e.g. a persistence type surfacing
  through a domain or public API); a shared/common module absorbing logic
  that belongs to one specific caller
- **Error handling**: swallowed or overly broad catches; exceptions used
  for ordinary control flow; inconsistent error-handling strategy for
  similar failures (e.g. one path throws, a similar path returns null);
  errors caught and logged but not actually handled; missing context in
  rethrown exceptions
- **Testability**: logic that's hard to unit test because it's entangled
  with I/O, static/global state, or the current time/randomness; hidden
  dependencies (a class reaching out to statics or singletons instead of
  receiving what it needs); side effects mixed into what looks like a pure
  computation
- **Consistency**: the same kind of problem solved a different way
  elsewhere in the codebase (e.g. two different validation patterns, two
  different ways of mapping DTOs); new code that doesn't follow an
  established convention without a stated reason
- **Data and control structures**: primitive obsession (a cluster of
  related primitives that should be their own type); data clumps (the same
  group of parameters passed around together); boolean/flag parameters
  that silently switch behavior; large or growing conditional/switch
  blocks that should be a lookup or polymorphism
- **API and extension surface**: public methods or classes that expose
  more than callers need; mutable state exposed where an immutable view
  would do; overly generic or configurable code built for a flexibility
  nothing currently uses
