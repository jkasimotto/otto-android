# Core

One job: shared infrastructure used by every feature while knowing none of them.

May import core and stdlib/libraries only. `core/db` may import trace and quest entities, DAOs, and converters because Room requires one database to name every entity.

Provisional decision (2026-07-20): revisit the database exception if a third feature adds tables.
