# Security Policy

## Supported Versions

Security fixes are applied to the latest released line only; there are no
long-term-support branches.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

Always upgrade to the newest version on Maven Central (`io.github.kotplat.roomql`) to receive fixes.

## Reporting a Vulnerability

**Please do not open a public issue for security problems.**

Report privately through GitHub's **[Report a vulnerability](https://github.com/KotPlat/RoomQL/security/advisories/new)**
(Security → Advisories → *Report a vulnerability*). This keeps the report
private to the maintainers and needs no email address.

If that is unavailable, reach out to the maintainer
[@ahmednobii on GitHub](https://github.com/ahmednobii) or via
[LinkedIn](https://www.linkedin.com/in/ahmednobii/) to arrange a private channel.

Please include:

- A description of the vulnerability and its impact.
- Steps to reproduce (a minimal query, entity, or build snippet is ideal).
- The affected RoomQL version and your Room/Kotlin/KSP versions.

### What to expect

- **Acknowledgement** within **72 hours**.
- An initial assessment (accepted / needs-more-info / declined) within **7 days**,
  with a brief explanation either way.
- For accepted reports: a fix on the latest line and a new Maven Central release as soon as
  practical, with credit to you in the advisory unless you prefer to remain anonymous.

## Scope notes

RoomQL builds SQL with **positional `?` placeholders** and binds all values through
Room's `SupportSQLiteQuery` — user-supplied values are never string-interpolated into
the SQL, so classic SQL-injection via bound arguments is not an expected vector.
Reports demonstrating injection, unbounded query construction, or a way to bypass the
positional binding are in scope and especially welcome.
