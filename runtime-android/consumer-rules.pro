# RoomQL consumer ProGuard/R8 rules.
#
# This file is packaged into the AAR as proguard.txt and applied automatically to any
# app that depends on roomql-runtime-android. It is intentionally empty of keep rules.
#
# RoomQL needs none, and the reason is structural rather than incidental: the KSP
# processor bakes table and column names into generated code as string literals —
#
#     object UserEntityTable : EntityTable {
#         val createdAt: Column<Long> = Column("created_at", "users")
#     }
#
# — so the SQL RoomQL emits is built from data, never from a class or member name read
# back at runtime. R8 is free to rename, merge, and inline every RoomQL type and every
# entity; the generated statement is byte-for-byte unchanged. There is no reflection, no
# Class.forName, and no runtime service lookup anywhere in the published artifacts.
# scripts/verify-no-reflection.sh enforces that against the real published jar and aar on
# every CI run, so this file stays empty only as long as it is true.
#
# The generated *Table objects live in your module, not this one, and your own code
# references them directly, so R8 keeps them by ordinary reachability.
#
# roomql-runtime (the pure-JVM DSL) likewise needs no rules and ships none.
#
# Room's own requirements are a separate matter and are covered by the consumer rules
# that room-runtime already ships.
#
# If RoomQL ever does need a keep rule, it belongs here — consumers will pick it up on
# upgrade without changing anything in their own configuration.
