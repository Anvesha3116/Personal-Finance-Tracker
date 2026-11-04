# Personal Finance Tracker (Java)

Java port of the Python/Flask app. Same web page, same features, same `/api/...` endpoints.

## Run it

You need **JDK 17 or newer** and **Maven**.

```bash
# Mac / Linux
bash run.sh

# Windows
run.bat
```

The first run builds `target/personal-finance-tracker.jar`; after that it just starts.
Open **http://127.0.0.1:5000** (the browser opens by itself).

Manual way:

```bash
mvn package
java -jar target/personal-finance-tracker.jar
```

Options: `--port=5001` (or set the `PORT` variable) and `--no-browser`.

Data is stored in `finance.db` and `receipts/` in the folder you start the app from.
They use the same format as the Python version, so you can copy both over to keep your data.

## What is Java and what is not

| Part | Language |
|------|----------|
| Server, database, validation, budgets, analytics, chart | **Java** |
| Web page (`src/main/resources/static/`: `index.html`, `app.js`, `style.css`) | HTML / CSS / **JavaScript** (unchanged) |

Browsers only run JavaScript, so the page itself stays JavaScript. Java serves it and answers its API calls.

## Where each Python file went

| Python | Java (`src/main/java/com/financetracker/`) |
|--------|-------------------------------------------|
| `main.py` (Flask routes) | `Main.java`, `web/WebServer.java` |
| `models/` (Pydantic) | `models/Transaction`, `Budget`, `Receipt`, `Validator` |
| `exceptions/` | `exceptions/` |
| `database/` (sqlite3) | `database/` (JDBC) |
| `services/` (pandas, matplotlib) | `services/` (plain Java, Java2D for the chart) |
