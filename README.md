# XYZ SoftDev — Excel Viewer & Validator

Upload any Excel `.xlsx` workbook → view all sheets in tabs → automatic validation.

## Stack
- **Java 17** · **Spring Boot 2.7.18** (Spring Framework 5.3.x)
- **Thymeleaf** UI with tabbed sheet view and formula hover tooltips
- **Apache POI 5.2.5** — reads cells, formulas, data types
- **H2 In-Memory DB** + **Liquibase** schema management
- Packaged as a single fat **JAR**

## Quick Start

```bash
mvn clean package -DskipTests
java -jar target/excel-viewer-1.0.0.jar
```

Open **http://localhost:8080**

## Features

### Sheet Viewer
- Every sheet rendered as a scrollable Excel-like grid
- **Tab per sheet** — click to switch
- **Formula cells** highlighted in blue — hover to see `=FORMULA`
- **Row/column headers** like Excel (A, B, C... / 1, 2, 3...)

### Validation (Timesheet sheet)
| Rule | Severity | Description |
|------|----------|-------------|
| TS-01 | CRITICAL | Max 8 hrs/day per resource |
| TS-02 | CRITICAL | No weekend entries |
| TS-03 | CRITICAL | No public holiday entries |
| TS-04 | CRITICAL | Hours must be positive |
| TS-05 | WARNING  | Resource must exist in roster |
| TS-06 | CRITICAL | SOW must match expected |
| TS-07 | WARNING  | Date within engagement period |

- Cells with issues are **highlighted** red/yellow
- Hover the cell → tooltip shows the exact rule violation

## Updating Master Data
Edit `src/main/resources/application.yml` and restart:

```yaml
app:
  master:
    holidays:
      - holidayDate: "2026-04-14"   # ← add/remove/change
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

Restart → `MasterDataLoader` re-seeds H2 automatically.

## H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:excelviewerdb`
- User: `sa` | Password: *(blank)*
