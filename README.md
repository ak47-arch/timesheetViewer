# Timesheet Viewer & Validator

Upload any Excel `.xlsx` workbook → view every sheet in lazy-loaded tabs → validate the
**Timesheet** and **Pivot** sheets against business rules that are fully configurable
from YAML and the database.

## Stack
- **Java 17** · **Spring Boot 2.7.18** (Spring Framework 5.3.x)
- **Spring MVC + Thymeleaf** server-rendered UI; vanilla JS for the grid
- **Spring Security** — form login, BCrypt, role-based access (ADMIN / MANAGER / USER)
- **Spring Data JPA / Hibernate** over **H2 in-memory** DB
- **Liquibase** schema management
- **Apache POI 5.2.5** — reads cells, formulas, data types
- Packaged as a single fat **JAR**

## Quick Start

```bash
mvn clean package -DskipTests
java -jar target/Timesheet-viewer-1.0.0.jar
```

Open **http://localhost:8080**. Seed logins: `admin / Admin123!`, `manager / Manager123!`,
and one `User123!` account per roster resource. ADMIN/MANAGER land on the upload page;
USER lands on the timesheet entry form.

---

## Features

### Sheet Viewer (lazy-loaded)
- Each sheet renders as a scrollable, Excel-like grid (A, B, C… / 1, 2, 3…).
- **Lazy per-tab loading** — the page ships only the tab bar + issues panel; each sheet's
  grid is fetched on first click from `GET /api/view/{sessionId}/sheet/{index}` and cached
  client-side. This removes the multi-MB single-page payload.
- **Text filter** — a search box above the grid hides non-matching rows in the current
  sheet (header rows always kept), with a live "N of M rows" count; the filter persists
  across tab switches.
- **Formula cells** highlighted in blue — hover to see `=FORMULA`.
- Cells with issues are highlighted red (critical) / amber (warning); hover shows the
  exact rule violation.

### Validation rules — tabbed & sheet-grouped
Rules are grouped by the sheet they validate and shown as **tabs** (Timesheet | Pivot) on
both the upload card and the viewer's "Edit Rules" modal. Each tab shows a live
selected/total count; the modal keeps its action footer pinned.

| Rule | Sheet | Severity | Description |
|------|-------|----------|-------------|
| TS-01 | Timesheet | CRITICAL | Max 8 hrs/day per resource |
| TS-02 | Timesheet | CRITICAL | No weekend entries |
| TS-03 | Timesheet | CRITICAL | No public holiday entries |
| TS-04 | Timesheet | CRITICAL | Hours must be positive |
| TS-05 | Timesheet | WARNING  | Resource must be in roster |
| TS-06 | Timesheet | CRITICAL | SOW must match expected |
| TS-07 | Timesheet | WARNING  | Date within engagement period |
| TS-08 | Timesheet | CRITICAL | All fields are mandatory |
| PS-01 | Pivot | CRITICAL | Resource present in Pivot sheet |
| PS-02 | Pivot | CRITICAL | Employee total hours match Timesheet |
| PS-03 | Pivot | CRITICAL | Date-wise total hours match Timesheet |
| PS-04 | Pivot | CRITICAL | Employee × date hours match Timesheet |
| PS-05 | Pivot | CRITICAL | Pivot grand total is correct |
| PS-06 | Pivot | CRITICAL | Working days match Timesheet |

- **Timesheet rules (TS-xx)** are selectable per upload.
- **Pivot rules (PS-xx)** are reconciliation checks that run automatically whenever a Pivot
  sheet is present ("always-on").

---

## Configurable rules (YAML + Database)

Rules are **defined in YAML**, **seeded into the database** on startup, and **served from a
cached, auto-refreshing catalog**. After seeding, the database is the source of truth.

```
application.yml (app.rules)  ──seed──▶  RULE_CONFIG table  ──auto-reload──▶  RuleCatalog cache
   defaults / structure                    runtime truth                       UI + engine
```

### Where rules live
`application.yml` → `app.rules`:

```yaml
app:
  rules:
    refresh-ms: 60000        # how often the catalog reloads from the DB
    reseed-on-start: false   # true = YAML overwrites existing DB rows on startup
    definitions:
      - { ruleId: TS-01, sheet: Timesheet, description: "Max 8 hrs/day per resource",
          severity: CRITICAL, enabled: true, alwaysOn: false, sortOrder: 1 }
      # ...
```

- **Seeding semantics:** a rule is inserted from YAML only if it doesn't already exist;
  existing rows are left untouched so admin edits survive restarts. Set
  `reseed-on-start: true` to force YAML to win.

### Dynamic loading (no restart)
`RuleCatalog` keeps an in-memory snapshot refreshed (a) on application ready,
(b) automatically every `app.rules.refresh-ms`, and (c) immediately whenever a rule is
changed through the admin UI. Direct H2-console edits are picked up within the refresh
interval.

### Enable / disable & CRUD from the Admin UI
**Admin → Validation Rules** (`/admin/rules`):
- **Enable/Disable** any rule. A disabled rule disappears from the upload UI and never
  runs — enforced centrally in the engine (a single global gate filters out issues whose
  rule is disabled, covering both TS-xx and PS-xx).
- **Add / Edit / Delete** rules (id, sheet, description, severity, sort order, always-on,
  enabled, and the violation message — see below).

> Note: adding a brand-new rule id registers it in the catalog and gates it, but a genuinely
> new *check* only produces issues once corresponding detection logic exists in
> `ValidationService`. Existing TS/PS rules are fully editable and toggleable.

### Configurable violation messages
Every rule has an optional **message template** (`MESSAGE_TEMPLATE`) editable on the rule
form. Blank = the engine's built-in message. Placeholders:

| Placeholder | Meaning |
|-------------|---------|
| `{detail}`   | The engine-computed message text (names, dates, hours, etc.) |
| `{ruleId}`   | e.g. `TS-02` |
| `{severity}` | `CRITICAL` / `WARNING` |
| `{field}`    | The column/field the issue is on |

Example template `[{ruleId}] {detail}` →
`[TS-02] Weekend entry not allowed: 01-Mar-26 (SUNDAY) for resource 'KS'`.
Messages can also be replaced with fully static text (omit `{detail}`). Rendering happens
at a single chokepoint, so all ~16 built-in violation messages across the 14 rules are
covered uniformly.

---

## Public Holidays (enable / disable)
**Admin → Holidays** (`/admin/holidays`): add, edit, delete, and **enable/disable** each
holiday. Disabled holidays are ignored by TS-03 (both at upload validation and timesheet
entry), so you can keep a holiday on record without it flagging entries.

---

## Roles & Screens
- **ADMIN** — upload/view + full admin (Resources, Holidays, Users, Validation Rules).
- **MANAGER** — upload, view, validate, export issues CSV.
- **USER** — daily timesheet entry form with live hour totals and SOW selection.

---

## Updating Master Data
Edit `src/main/resources/application.yml` and restart (`MasterDataLoader` re-seeds H2):

```yaml
app:
  master:
    holidays:
      - holidayDate: "2026-04-14"
        holidayName: Dr. Ambedkar Jayanti
        countryCode: IN
    resources:
      - resourceId: RES-019
        name: NEW
        dailyRateUsd: 180
        startDate: "2026-04-01"
        endDate: "2026-04-30"
    validation:
      max-hours-per-day: 8.0
      expected-sow: SOW_18_2026
```

---

## Key Endpoints
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET  | `/` | MANAGER, ADMIN | Upload page |
| POST | `/upload` | MANAGER, ADMIN | Parse + validate workbook |
| GET  | `/view/{sessionId}` | MANAGER, ADMIN | Viewer (tabs + issues) |
| GET  | `/api/view/{sessionId}/sheet/{index}` | MANAGER, ADMIN | Lazy sheet JSON |
| GET  | `/admin/rules` | ADMIN | Manage rules |
| POST | `/admin/rules/save` `/admin/rules/toggle/{ruleId}` `/admin/rules/delete/{id}` | ADMIN | Rule CRUD / toggle |
| GET  | `/admin/holidays` | ADMIN | Manage holidays |
| POST | `/admin/holidays/toggle/{id}` | ADMIN | Enable/disable holiday |

---

## H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:timesheetviewerdb`
- User: `sa` | Password: *(blank)*

Useful tables: `RULE_CONFIG` (rule enable/disable + message templates),
`PUBLIC_HOLIDAY` (with `ENABLED`), `VALIDATION_ISSUE`, `CELL_DATA`, `UPLOAD_SESSION`.

```sql
-- disable a rule at runtime (picked up on next refresh)
UPDATE RULE_CONFIG SET ENABLED = FALSE WHERE RULE_ID = 'TS-02';
-- custom message
UPDATE RULE_CONFIG SET MESSAGE_TEMPLATE = '[{ruleId}] {detail}' WHERE RULE_ID = 'TS-01';
```
