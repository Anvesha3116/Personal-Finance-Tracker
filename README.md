# Personal Finance Tracker (Java)

A web-based personal finance tracker built with **Java**. Track income and expenses, set budgets, attach receipts, and view spending analytics and charts, all from a simple browser interface.

## Features

- Add and manage transactions (income and expenses)
- Set budgets and keep spending in check
- Upload and store receipts
- Spending analytics with a generated chart
- Input validation with clear error handling
- Local SQLite storage, no database server needed

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend / server | Java 17+ |
| Database | SQLite (via JDBC, `sqlite-jdbc`) |
| JSON handling | Jackson (`jackson-databind`) |
| Charts | Java2D |
| Build tool | Maven (single runnable jar via Maven Shade) |
| Frontend | HTML, CSS, JavaScript |

## Requirements

- **JDK 17 or newer** (`java -version` to check)
- **Maven 3.8+** (`mvn -version` to check)

## Quick Start

```bash
# Mac / Linux
bash run.sh

# Windows (Command Prompt)
run.bat

# Windows (PowerShell)
.\run.bat
```

The first run builds `target/personal-finance-tracker.jar`; after that the app just starts.
Then open **http://127.0.0.1:5000** (the browser opens automatically).

### Manual build and run

```bash
mvn package
java -jar target/personal-finance-tracker.jar
```

### Options

| Option | Description |
|--------|-------------|
| `--port=5001` | Run on a different port (or set the `PORT` environment variable) |
| `--no-browser` | Do not open the browser automatically |

Example:

```bash
java -jar target/personal-finance-tracker.jar --port=5001 --no-browser
```

## Data Storage

Data is stored in `finance.db` (SQLite) and the `receipts/` folder, both created in the directory you start the app from. To move your data to another machine, copy both.

The format is identical to the earlier Python version of this project, so existing data can be reused.

## Project Structure

```
.
├── pom.xml                      # Maven build configuration
├── run.sh / run.bat             # One-step build and run scripts
├── finance.db                   # SQLite database
└── src/main/
    ├── java/com/financetracker/
    │   ├── Main.java            # Entry point
    │   ├── web/                 # WebServer and API routes
    │   ├── models/              # Transaction, Budget, Receipt, Validator
    │   ├── exceptions/          # Custom exceptions
    │   ├── database/            # JDBC data access
    │   └── services/            # Budgets, analytics, chart generation
    └── resources/static/        # index.html, app.js, style.css
```

## What Is Java and What Is Not

| Part | Language |
|------|----------|
| Server, database, validation, budgets, analytics, chart | **Java** |
| Web page (`index.html`, `app.js`, `style.css`) | HTML / CSS / **JavaScript** |

Browsers only run JavaScript, so the page itself stays JavaScript. Java serves it and answers its API calls.

## Migrated from Python

This project is a Java port of the original Python version.

| Python | Java (`src/main/java/com/financetracker/`) |
|--------|-------------------------------------------|
| `main.py` (Flask routes) | `Main.java`, `web/WebServer.java` |
| `models/` (Pydantic) | `models/Transaction`, `Budget`, `Receipt`, `Validator` |
| `exceptions/` | `exceptions/` |
| `database/` (sqlite3) | `database/` (JDBC) |
| `services/` (pandas, matplotlib) | `services/` (plain Java, Java2D for the chart) |

## Troubleshooting

- **`mvn` is not recognized:** install Maven and add its `bin` folder to your `PATH`, then reopen the terminal.
- **Build failed on first run:** confirm `java -version` shows 17 or higher and `mvn -version` works.
- **Port already in use:** start with `--port=5001` (or any free port).