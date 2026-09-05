# KSP processor runs in aggregating mode, not incremental mode

The RoomQl KSP processor declares aggregating mode (sees all source files in every run) rather than incremental mode (per-file). This is required because column-ref generation for JOIN aliasing must have visibility into all `@Entity` classes simultaneously to detect conflicting column names across tables. Incremental mode would process each entity in isolation, making cross-entity collision detection impossible without a separate aggregation pass.

## Consequences

Every source change triggers a full KSP run for the RoomQl processor. This is the same behaviour as Room's own KSP processor and is acceptable for the same reason: correctness over build-speed optimization. If a future version can separate collision detection into a distinct processor pass, incremental mode becomes feasible for the column-ref generation step.
