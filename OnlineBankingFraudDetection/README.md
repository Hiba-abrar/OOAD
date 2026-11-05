# Online Banking Fraud Detection & Monitoring System

A Java banking engine with a Streamlit web interface. Every withdrawal and transfer is screened
for fraud and risk before money moves. Alerts go to observers and an audit trail is kept.
No external Java libraries are required.

## Requirements
- Java Development Kit (JDK) 17 or later
- Python 3.9 or later (only for the Streamlit web UI)

## Web UI (Streamlit)
    python -m pip install -r requirements.txt
    ./run_ui.sh           # Git Bash / macOS / Linux
    run_ui.bat            # Windows Command Prompt

Open http://localhost:8501. The Java engine starts automatically in the background.
The bank starts empty. Open accounts first, then deposit, withdraw, transfer, view statements,
and review alerts. "Clear all data" in the sidebar empties the bank.

## Console application and tests
    ./build.sh     # compile application and tests into ./out
    ./test.sh      # run the automated test suite (60 checks)
    ./run.sh       # start the console application

On Windows Command Prompt:
    dir /s /b src\main\java\*.java > sources.txt
    javac -d out @sources.txt
    java -cp out com.obfds.app.Main

## Input rules (enforced by the engine, so console, API and UI all follow them)
- Customer name: 2 to 60 characters, letters, spaces, dots, apostrophes and hyphens only. No numbers.
- CNIC: exactly 5 digits.
- Amounts: numbers only (commas and spaces are accepted as separators, e.g. 6,50,000). Letters and
  scientific notation are rejected. Withdrawals, transfers and deposits must be greater than zero.
  Opening balances may be zero.
- Country code: must be a real ISO 3166 two-letter code (for example PK, AE, US). The UI and alerts
  show the country name.
- Recipient account number: 6 to 20 letters or digits. It can be an account at this bank or any
  account number outside it.

## Fraud and risk conditions
Blocked payments leave balances unchanged. Flagged payments are completed and marked for review.

| Condition | Triggers when | Result |
|---|---|---|
| HIGH_AMOUNT | Any withdrawal or transfer of PKR 5,00,000 or more | HIGH, blocked. PKR 10,00,000 or more: CRITICAL, blocked |
| VELOCITY | More than 5 outgoing payments from one account within 10 minutes | HIGH, blocked. Blocked and failed attempts count |
| FOREIGN_LOCATION | Any withdrawal or transfer started outside PK, including very large amounts | MEDIUM, flagged |
| UNUSUAL_HOUR | PKR 1,00,000 or more between 00:00 and 04:59 | LOW, flagged |
| SELF_TRANSFER | Transfer to your own account number. Attempts 1 and 2 are flagged; attempt 3 onwards is blocked | MEDIUM, then HIGH (blocked) |
| NEW_PAYEE_LARGE | First successful transfer to a recipient, for PKR 1,00,000 or more | MEDIUM, flagged |
| EXTERNAL_LARGE_TRANSFER | Transfer of PKR 2,00,000 or more to an account outside this bank | HIGH, blocked |

Decision: any HIGH or CRITICAL alert blocks the payment. Only MEDIUM or LOW alerts flag it.
Risk score = sum of severity weights (LOW 5, MEDIUM 20, HIGH 40, CRITICAL 70), capped at 100.
The fraud check runs before the balance check, so a blocked payment that is also over the balance
shows both reasons.

Balance rules: savings accounts cannot drop below PKR 5,000; current accounts may be overdrawn by
up to PKR 50,000. Anything beyond that is rejected as insufficient funds. Deposits are not screened.

## Design patterns
Strategy (fraud rules), Observer (alerts), Factory (accounts), Facade (BankingService).

## Package layout
    com.obfds.model       domain entities (Account, SavingsAccount, CurrentAccount, Customer, Transaction, Alert)
    com.obfds.factory     AccountFactory
    com.obfds.fraud       FraudRule, rule implementations, FraudDetectionEngine, RiskAssessment
    com.obfds.monitoring  AlertPublisher, AlertObserver, ConsoleAlertObserver, AuditLogObserver
    com.obfds.service     Bank (repository) and BankingService (facade)
    com.obfds.validation  InputValidator (names, CNIC, amounts, country codes, account numbers)
    com.obfds.api         local HTTP API used by the Streamlit UI (127.0.0.1:8765)
    com.obfds.exception   BankingException and subclasses
    com.obfds.app         Main (console UI), BankingSystem (composition root)
    com.obfds.test        FraudSystemTest

## Folder ui/
app.py (Streamlit pages) and backend.py (starts the Java API and sends requests).

## Folder docs/
UML diagram sources (PlantUML .puml) and PNG images, plus captured test output.
