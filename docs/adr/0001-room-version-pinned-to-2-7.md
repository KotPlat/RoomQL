# Room version pinned to ≤2.7.x to preserve API 21 support

The `:runtime` module declares Room as `compileOnly` with a maximum version of 2.7.x. Room 2.8.0 raised `minSdk` to API 23, which would exclude a large share of the Android device base this library targets (API 21+). We chose breadth of device support over access to Room 2.8.x features, and pinned the ceiling accordingly.

## Considered Options

- **Pin ≤2.7.x (chosen)** — keeps API 21 support, misses any 2.8.x-only APIs.
- **Target 2.8.x+** — drops API 21/22 devices (~5–8 % of the global install base at time of decision).
- **Dual artifact** — publish a `room-2.7` and a `room-2.8` variant. Rejected: doubles maintenance burden for no clear API benefit in v1.

## Consequences

When Room 2.8.x adoption is high enough and/or the API 21 constraint is relaxed, bump the ceiling, raise `minSdk`, and publish a new major version.
