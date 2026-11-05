"""Streamlit front end for the Online Banking Fraud Detection & Monitoring System.

Run from the project folder with:  streamlit run ui/app.py
The Java banking engine starts automatically in the background.
"""
import re
import sys
from pathlib import Path

import pandas as pd
import streamlit as st

sys.path.insert(0, str(Path(__file__).resolve().parent))
import backend as bk  # noqa: E402

st.set_page_config(page_title="Fraud Detection & Monitoring", page_icon="🛡️", layout="wide")

SEVERITY_BOX = {"CRITICAL": st.error, "HIGH": st.error, "MEDIUM": st.warning, "LOW": st.info}


@st.cache_resource(show_spinner="Starting the banking engine...")
def _start_backend():
    bk.ensure_backend()
    return True


try:
    _start_backend()
except bk.BankError as e:
    st.error(str(e))
    st.stop()


def fmt_pkr(value):
    """Formats an amount in PKR with Indian digit grouping, e.g. 650000 -> PKR 6,50,000.00."""
    sign = "-" if value < 0 else ""
    whole, _, frac = f"{abs(value):.2f}".partition(".")
    if len(whole) > 3:
        head, tail = whole[:-3], whole[-3:]
        parts = []
        while len(head) > 2:
            parts.insert(0, head[-2:])
            head = head[:-2]
        if head:
            parts.insert(0, head)
        whole = ",".join(parts + [tail])
    return f"{sign}PKR {whole}.{frac}"


AMOUNT_PATTERN = re.compile(r"\d+(\.\d{1,2})?")


def parse_amount(text, label="Amount", allow_zero=False):
    """Digits only (commas and spaces are allowed as separators, e.g. 6,50,000)."""
    value = (text or "").strip().replace(",", "").replace(" ", "")
    if not value:
        raise ValueError(f"Enter the {label.lower()}.")
    if not AMOUNT_PATTERN.fullmatch(value):
        raise ValueError(f"{label} must contain numbers only, for example 650000.")
    amount = float(value)
    if amount <= 0 and not allow_zero:
        raise ValueError(f"{label} must be greater than zero.")
    return amount


def check_name(name):
    value = " ".join((name or "").split())
    if len(value) < 2 or len(value) > 60:
        raise ValueError("Customer name must be 2 to 60 characters.")
    if not all(c.isalpha() or c in " .'-" for c in value):
        raise ValueError("Customer name can only contain letters, spaces, dots, apostrophes and hyphens (no numbers).")
    return value


def check_cnic(cnic):
    value = (cnic or "").strip()
    if not re.fullmatch(r"\d{5}", value):
        raise ValueError("CNIC must be exactly 5 digits.")
    return value


def check_country(code):
    value = (code or "").strip().upper()
    if not re.fullmatch(r"[A-Z]{2}", value):
        raise ValueError("Country code must be 2 letters, for example PK, AE or US.")
    return value


def account_label(a):
    return f"{a['number']}  |  {a['owner']}  ({a['type'].title()})  {fmt_pkr(a['balance'])}"


def pick_account(accounts, key, label="Account"):
    return st.selectbox(label, accounts, format_func=account_label, key=key)["number"]


def need_accounts(accounts):
    if not accounts:
        st.info("There are no accounts yet. Open one on the **Open account** page first.")
        return False
    return True


def show_rules_table():
    st.table(pd.DataFrame(bk.rules()["rules"]).rename(
        columns={"name": "Condition", "description": "What triggers it"}))


def show_outcome(res, amount):
    status = res["status"]
    if res.get("counterparty"):
        where = "outside this bank" if res["external"] else "inside this bank"
        st.caption(f"Recipient: {res['counterparty']} ({where})")
    if res.get("location"):
        st.caption(f"Location: {res['location']} ({res['locationName']})")
    st.caption(f"Amount processed: {fmt_pkr(amount)}  |  Balance available before this: "
               f"{fmt_pkr(res['availableBefore'])}  |  Reference {res['transactionId']}")
    if status == "BLOCKED":
        st.error(f"BLOCKED by fraud screening. Risk score {res['riskScore']}/100. "
                 f"Balances were not changed.")
        if not res["sufficientBalance"]:
            st.error(f"The amount is also more than your available balance of "
                     f"{fmt_pkr(res['availableBefore'])}.")
    elif status == "COMPLETED" and res["alerts"]:
        st.warning(f"COMPLETED but FLAGGED for review. Risk score {res['riskScore']}/100.")
    elif status == "COMPLETED":
        st.success(f"COMPLETED. Risk score {res['riskScore']}/100 (no fraud indicators).")
    for a in res["alerts"]:
        SEVERITY_BOX.get(a["severity"], st.info)(f"**{a['severity']} - {a['ruleName']}**: {a['message']}")


# ---------------- sidebar ----------------
st.sidebar.title("🛡️ Fraud Monitor")
st.sidebar.caption("Online Banking Fraud Detection & Monitoring System")
PAGES = ["Dashboard", "Open account", "Deposit", "Withdraw", "Transfer", "Statement",
         "Fraud alerts", "Risk rules"]
page = st.sidebar.radio("Go to", PAGES)
st.sidebar.divider()
if st.sidebar.button("Clear all data", width="stretch"):
    bk.reset()
    st.sidebar.success("All customers and accounts were cleared.")

# ---------------- pages ----------------
try:
    data = bk.state()
    accounts = data["accounts"]

    if page == "Dashboard":
        st.title(data["bankName"])
        total_alerts = len(bk.alerts()["alerts"])
        c1, c2, c3, c4 = st.columns(4)
        c1.metric("Customers", len(data["customers"]))
        c2.metric("Accounts", len(accounts))
        c3.metric("Total balance", fmt_pkr(sum(a["balance"] for a in accounts)))
        c4.metric("Alerts raised", total_alerts)
        st.subheader("Accounts")
        if accounts:
            df = pd.DataFrame(accounts)[["number", "owner", "type", "homeCountry", "balance", "available"]]
            df["balance"] = df["balance"].map(fmt_pkr)
            df["available"] = df["available"].map(fmt_pkr)
            st.dataframe(df.rename(columns={"homeCountry": "home", "available": "available to debit"}),
                         hide_index=True, width="stretch")
        else:
            st.info("No accounts yet. Use **Open account** to create one.")

    elif page == "Open account":
        st.title("Open a new account")
        with st.form("open_form"):
            name = st.text_input("Customer full name")
            nid = st.text_input("CNIC (5 digits)", placeholder="e.g. 12345")
            acc_type = st.radio("Account type", ["SAVINGS", "CURRENT"], horizontal=True,
                                help="Savings keeps a PKR 5,000 minimum. Current allows an overdraft of PKR 50,000.")
            opening_text = st.text_input("Opening balance (PKR)", value="0", placeholder="numbers only, e.g. 9000")
            country = st.text_input("Home country code (ISO, e.g. PK)", value="PK")
            submitted = st.form_submit_button("Open account")
        if submitted:
            try:
                clean_name = check_name(name)
                clean_cnic = check_cnic(nid)
                clean_country = check_country(country)
                opening = parse_amount(opening_text, "Opening balance", allow_zero=True)
                res = bk.open_account(clean_name, clean_cnic, acc_type, opening, clean_country)
                st.success(f"Account **{res['accountNumber']}** opened for {res['owner']} "
                           f"({res['type'].title()}) with an opening balance of {fmt_pkr(opening)}.")
            except (ValueError, bk.BankError) as e:
                st.error(str(e))

    elif page == "Deposit":
        st.title("Deposit funds")
        if need_accounts(accounts):
            with st.form("deposit_form"):
                acc = pick_account(accounts, "dep_acc")
                amount_text = st.text_input("Amount (PKR)", placeholder="numbers only, e.g. 9000 or 6,50,000")
                submitted = st.form_submit_button("Deposit")
            if submitted:
                try:
                    amount = parse_amount(amount_text)
                    res = bk.deposit(acc, amount)
                    st.success(f"Deposited {fmt_pkr(amount)}. New balance: {fmt_pkr(res['balance'])}")
                except (ValueError, bk.BankError) as e:
                    st.error(str(e))

    elif page == "Withdraw":
        st.title("Withdraw funds")
        if need_accounts(accounts):
            with st.form("withdraw_form"):
                src = pick_account(accounts, "withdraw_src", "Account")
                amount_text = st.text_input("Amount (PKR)", placeholder="numbers only, e.g. 3000 or 6,50,000")
                location = st.text_input("Transaction country code (ISO, e.g. PK)", value="PK",
                                         help="Use a country other than PK to trigger the foreign-location rule.")
                when = st.text_input("Date and time (optional, YYYY-MM-DDTHH:MM:SS)", value="",
                                     help="Leave blank to use the current time.")
                submitted = st.form_submit_button("Withdraw")
            if submitted:
                try:
                    amount = parse_amount(amount_text)
                    res = bk.withdraw(src, amount, check_country(location), when.strip() or None)
                    show_outcome(res, amount)
                except (ValueError, bk.BankError) as e:
                    st.error(str(e))

    elif page == "Transfer":
        st.title("Transfer funds")
        st.write("Send money to any account number. The recipient can be an account at this bank or an "
                 "account at another bank. The recipient does not need to be registered here.")
        if need_accounts(accounts):
            with st.form("transfer_form"):
                src = pick_account(accounts, "transfer_src", "From account")
                to_text = st.text_input("Recipient account number", placeholder="e.g. ACC5002 or any account number")
                amount_text = st.text_input("Amount (PKR)", placeholder="numbers only, e.g. 3000 or 2,50,000")
                location = st.text_input("Transaction country code (ISO, e.g. PK)", value="PK")
                when = st.text_input("Date and time (optional, YYYY-MM-DDTHH:MM:SS)", value="",
                                     help="Leave blank to use the current time.")
                submitted = st.form_submit_button("Send transfer")
            if submitted:
                try:
                    to = to_text.strip().replace(" ", "").upper()
                    if not to:
                        raise ValueError("Enter the recipient account number.")
                    amount = parse_amount(amount_text)
                    res = bk.transfer(src, to, amount, check_country(location), when.strip() or None)
                    show_outcome(res, amount)
                except (ValueError, bk.BankError) as e:
                    st.error(str(e))
        with st.expander("Conditions checked on every transfer"):
            show_rules_table()

    elif page == "Statement":
        st.title("Account statement")
        if need_accounts(accounts):
            acc = pick_account(accounts, "stmt_acc")
            s = bk.statement(acc)
            a = s["account"]
            c1, c2, c3 = st.columns(3)
            c1.metric("Balance", fmt_pkr(a["balance"]))
            c2.metric("Available to debit", fmt_pkr(a["available"]))
            c3.metric("Account type", a["type"].title())
            if s["transactions"]:
                df = pd.DataFrame(s["transactions"])
                df["time"] = df["time"].str.replace("T", " ").str[:16]
                df["amount"] = df["amount"].map(fmt_pkr)
                df["counterparty"] = [
                    (f"{c} (outside this bank)" if ext else c) if c else ""
                    for c, ext in zip(df["counterparty"], df["external"])]
                st.dataframe(df[["transactionId", "time", "type", "amount", "counterparty", "location",
                                 "status", "riskScore"]], hide_index=True, width="stretch")
            else:
                st.info("No transactions yet for this account.")

    elif page == "Fraud alerts":
        st.title("Fraud alert monitor")
        rows = bk.alerts()["alerts"]
        cols = st.columns(5)
        cols[0].metric("Total", len(rows))
        for col, sev in zip(cols[1:], ["CRITICAL", "HIGH", "MEDIUM", "LOW"]):
            col.metric(sev.title(), sum(1 for r in rows if r["severity"] == sev))
        if rows:
            df = pd.DataFrame(rows)[["time", "severity", "ruleName", "account", "transactionId", "message"]]
            df["time"] = df["time"].str.replace("T", " ").str[:16]
            st.dataframe(df.rename(columns={"ruleName": "rule", "transactionId": "transaction"}).iloc[::-1],
                         hide_index=True, width="stretch")
        else:
            st.info("No alerts raised yet. Try a large transfer or a withdrawal from another country.")

    elif page == "Risk rules":
        st.title("Risk and fraud conditions")
        st.write("Every withdrawal and transfer is checked against the conditions below. HIGH and CRITICAL "
                 "conditions block the payment and leave balances unchanged. MEDIUM and LOW conditions let "
                 "it through but flag it for review. Deposits are not screened.")
        show_rules_table()

except bk.BankError as e:
    st.error(str(e))
