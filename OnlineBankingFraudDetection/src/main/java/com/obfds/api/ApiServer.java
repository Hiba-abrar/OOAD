package com.obfds.api;

import com.obfds.app.BankingSystem;
import com.obfds.exception.BankingException;
import com.obfds.fraud.FraudRule;
import com.obfds.model.Account;
import com.obfds.model.AccountType;
import com.obfds.model.Alert;
import com.obfds.model.Customer;
import com.obfds.model.Transaction;
import com.obfds.monitoring.AuditLogObserver;
import com.obfds.service.Bank;
import com.obfds.service.BankingService;
import com.obfds.validation.InputValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local HTTP API that exposes the banking system to the Streamlit UI.
 * Listens on 127.0.0.1 only. Uses the JDK's built-in HTTP server, so no libraries are needed.
 */
public final class ApiServer {
    public static final int DEFAULT_PORT = 8765;

    private static BankingSystem system = newSystem();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", ApiServer::handle);
        server.start();
        System.out.println("Fraud system API running at http://127.0.0.1:" + port);
    }

    /** Creates an empty bank. Customers and accounts are added through the UI. */
    private static BankingSystem newSystem() {
        return BankingSystem.create("Meridian National Bank");
    }

    private static synchronized void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object body;
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            body = route(ex.getRequestURI().getPath(), q);
        } catch (BankingException | IllegalArgumentException e) {
            status = 400;
            body = obj("ok", false, "error", e.getMessage());
        } catch (RuntimeException e) {
            status = 500;
            body = obj("ok", false, "error", "Internal error: " + e);
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Object route(String path, Map<String, String> q) {
        BankingService svc = system.getService();
        Bank bank = svc.getBank();
        AuditLogObserver audit = system.getAuditLog();

        if (path.equals("/api/health")) {
            return obj("ok", true);
        }
        if (path.equals("/api/reset")) {
            system = newSystem();
            return obj("ok", true);
        }
        if (path.equals("/api/state")) {
            return state(bank);
        }
        if (path.equals("/api/open")) {
            String country = loc(q);
            double opening = InputValidator.openingAmount(req(q, "opening"));
            AccountType type = req(q, "type").toUpperCase().startsWith("C") ? AccountType.CURRENT : AccountType.SAVINGS;
            Customer c = bank.registerCustomer(req(q, "name"), req(q, "nid"));
            Account a = bank.openAccount(c.getCustomerId(), type, opening, country);
            return obj("ok", true, "accountNumber", a.getAccountNumber(), "owner", c.getFullName(),
                    "type", type.name());
        }
        if (path.equals("/api/deposit")) {
            svc.deposit(req(q, "account"), InputValidator.positiveAmount(req(q, "amount"), "Amount"));
            return obj("ok", true, "balance", bank.getAccount(req(q, "account")).getBalance());
        }
        if (path.equals("/api/withdraw")) {
            Account src = bank.getAccount(req(q, "account"));
            double amount = InputValidator.positiveAmount(req(q, "amount"), "Amount");
            double available = src.getAvailableBalance();
            int before = audit.getAlerts().size();
            Transaction tx = svc.withdraw(src.getAccountNumber(), amount, loc(q), when(q));
            return result(tx, audit, before, available);
        }
        if (path.equals("/api/transfer")) {
            Account src = bank.getAccount(req(q, "from"));
            double amount = InputValidator.positiveAmount(req(q, "amount"), "Amount");
            double available = src.getAvailableBalance();
            int before = audit.getAlerts().size();
            Transaction tx = svc.transfer(src.getAccountNumber(), req(q, "to"), amount, loc(q), when(q));
            return result(tx, audit, before, available);
        }
        if (path.equals("/api/statement")) {
            return statement(bank.getAccount(req(q, "account")));
        }
        if (path.equals("/api/rules")) {
            List<Object> list = new ArrayList<>();
            for (FraudRule r : system.getService().getEngine().getRules()) {
                list.add(obj("name", r.getName(), "description", r.getDescription()));
            }
            return obj("ok", true, "rules", list);
        }
        if (path.equals("/api/alerts")) {
            return alerts(audit);
        }
        throw new IllegalArgumentException("Unknown endpoint: " + path);
    }

    // ---------- response builders ----------

    private static Map<String, Object> state(Bank bank) {
        List<Object> customers = new ArrayList<>();
        for (Customer c : bank.getCustomers()) {
            List<Object> accounts = new ArrayList<>();
            for (Account a : c.getAccounts()) {
                accounts.add(accountMap(a));
            }
            customers.add(obj("id", c.getCustomerId(), "name", c.getFullName(),
                    "maskedNationalId", c.getMaskedNationalId(), "accounts", accounts));
        }
        List<Object> flat = new ArrayList<>();
        for (Account a : bank.getAccounts()) {
            flat.add(accountMap(a));
        }
        return obj("ok", true, "bankName", bank.getName(), "customers", customers, "accounts", flat);
    }

    private static Map<String, Object> accountMap(Account a) {
        return obj("number", a.getAccountNumber(), "owner", a.getOwner().getFullName(),
                "type", a.getType().name(), "homeCountry", a.getHomeCountry(),
                "balance", a.getBalance(), "available", a.getAvailableBalance());
    }

    private static Map<String, Object> statement(Account a) {
        List<Object> txs = new ArrayList<>();
        for (Transaction t : a.getTransactions()) {
            txs.add(txMap(t));
        }
        return obj("ok", true, "account", accountMap(a), "transactions", txs);
    }

    private static Map<String, Object> result(Transaction tx, AuditLogObserver audit, int alertsBefore,
                                              double availableBefore) {
        List<Object> raised = new ArrayList<>();
        for (Alert a : audit.getAlerts().subList(alertsBefore, audit.getAlerts().size())) {
            raised.add(alertMap(a));
        }
        return obj("ok", true, "transactionId", tx.getTransactionId(), "status", tx.getStatus().name(),
                "type", tx.getType().name(), "amount", tx.getAmount(), "riskScore", tx.getRiskScore(),
                "availableBefore", availableBefore,
                "external", tx.isExternalCounterparty(),
                "counterparty", tx.getCounterpartyAccount() == null ? "" : tx.getCounterpartyAccount(),
                "location", tx.getLocation() == null ? "" : tx.getLocation(),
                "locationName", tx.getLocation() == null ? "" : InputValidator.countryName(tx.getLocation()),
                "sufficientBalance", tx.getAmount() <= availableBefore,
                "alerts", raised);
    }

    private static Map<String, Object> alerts(AuditLogObserver audit) {
        List<Object> list = new ArrayList<>();
        for (Alert a : audit.getAlerts()) {
            list.add(alertMap(a));
        }
        return obj("ok", true, "alerts", list);
    }

    private static Map<String, Object> alertMap(Alert a) {
        return obj("alertId", a.getAlertId(), "ruleName", a.getRuleName(), "severity", a.getSeverity().name(),
                "message", a.getMessage(), "account", a.getTransaction().getAccountNumber(),
                "transactionId", a.getTransaction().getTransactionId(),
                "time", a.getRaisedAt().toString());
    }

    private static Map<String, Object> txMap(Transaction t) {
        return obj("transactionId", t.getTransactionId(), "type", t.getType().name(),
                "amount", t.getAmount(), "time", t.getTimestamp().toString(),
                "location", t.getLocation(), "counterparty", t.getCounterpartyAccount(),
                "status", t.getStatus().name(), "riskScore", t.getRiskScore(),
                "external", t.isExternalCounterparty());
    }

    // ---------- request helpers ----------

    private static String req(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing value for: " + key);
        }
        return v.trim();
    }

    private static String loc(Map<String, String> q) {
        String v = q.getOrDefault("location", q.getOrDefault("country", "")).trim();
        return InputValidator.countryCode(v.isEmpty() ? "PK" : v);
    }

    private static LocalDateTime when(Map<String, String> q) {
        String v = q.get("when");
        if (v == null || v.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(v.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid date/time: " + v);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
