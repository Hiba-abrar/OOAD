"""Connects the Streamlit UI to the Java banking engine.

The Java side runs as a small local HTTP server (com.obfds.api.ApiServer). This module
compiles the Java sources if needed, starts the server, and sends it requests.
"""
import glob
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PORT = 8765
BASE = f"http://127.0.0.1:{PORT}/api/"
OUT = ROOT / "out"
LOG = ROOT / "ui" / "backend.log"


class BankError(Exception):
    """Raised when the banking engine rejects a request (for example insufficient funds)."""


def _request(endpoint, timeout=5, **params):
    params = {k: v for k, v in params.items() if v is not None}
    url = BASE + endpoint
    if params:
        url += "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        data = json.loads(e.read().decode("utf-8"))
    if not data.get("ok", False):
        raise BankError(data.get("error", "Unknown error"))
    return data


def _healthy():
    try:
        _request("health", timeout=1)
        return True
    except Exception:
        return False


def _compile():
    sources = glob.glob(str(ROOT / "src" / "main" / "java" / "**" / "*.java"), recursive=True)
    OUT.mkdir(exist_ok=True)
    try:
        subprocess.run(["javac", "-d", str(OUT), *sources], cwd=ROOT, check=True, capture_output=True, text=True)
    except FileNotFoundError:
        raise BankError("Java compiler (javac) not found. Install JDK 17+ and restart the UI.")
    except subprocess.CalledProcessError as e:
        raise BankError("Compilation failed:\n" + (e.stderr or e.stdout))


def ensure_backend():
    """Starts the Java API server if it is not already running."""
    if _healthy():
        return
    _compile()
    log = open(LOG, "w")
    try:
        subprocess.Popen(["java", "-cp", str(OUT), "com.obfds.api.ApiServer", str(PORT)],
                         cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    except FileNotFoundError:
        raise BankError("Java runtime (java) not found. Install JDK 17+ and restart the UI.")
    for _ in range(50):
        if _healthy():
            return
        time.sleep(0.2)
    raise BankError(f"The Java backend did not start. See {LOG} for details.")


def state():
    return _request("state")


def reset():
    return _request("reset")


def open_account(name, national_id, account_type, opening, country):
    return _request("open", name=name, nid=national_id, type=account_type, opening=opening, country=country)


def deposit(account, amount):
    return _request("deposit", account=account, amount=amount)


def withdraw(account, amount, location, when=None):
    return _request("withdraw", account=account, amount=amount, location=location, when=when)


def transfer(source, target, amount, location, when=None):
    return _request("transfer", **{"from": source, "to": target, "amount": amount,
                                   "location": location, "when": when})


def statement(account):
    return _request("statement", account=account)


def alerts():
    return _request("alerts")


def rules():
    return _request("rules")
