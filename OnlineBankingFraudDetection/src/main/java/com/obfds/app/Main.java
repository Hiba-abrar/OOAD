package com.obfds.app;

import com.obfds.exception.BankingException;
import com.obfds.model.Account;
import com.obfds.model.AccountType;
import com.obfds.model.Customer;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import com.obfds.monitoring.AuditLogObserver;
import com.obfds.model.Alert;
import com.obfds.service.Bank;
import com.obfds.service.BankingService;
import com.obfds.validation.InputValidator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/** Console front end for the Online Banking Fraud Detection & Monitoring System. */
public class Main {
    private static final Scanner IN = new Scanner(System.in);
    private static final String HOME_COUNTRY = "PK";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd-MMM HH:mm");

    public static void main(String[] args) {
        BankingSystem system = BankingSystem.create("Meridian National Bank");
        System.out.println("=== Online Banking Fraud Detection & Monitoring System ===");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Select option");
            try {
                switch (choice) {
                    case "1" -> openAccount(system);
                    case "2" -> deposit(system);
                    case "3" -> withdraw(system);
                    case "4" -> transfer(system);
                    case "5" -> statement(system);
                    case "6" -> alertDashboard(system);
                    case "0" -> running = false;
                    default -> System.out.println("Invalid option, please try again.");
                }
            } catch (BankingException | IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
        System.out.println("Session closed.");
    }

    private static void printMenu() {
        System.out.println("""
                ----------------------------------------
                 1. Open customer and account
                 2. Deposit
                 3. Withdraw
                 4. Transfer
                 5. Account statement
                 6. Fraud alert dashboard
                 0. Exit
                ----------------------------------------""");
    }

    private static void openAccount(BankingSystem system) {
        Bank bank = system.getService().getBank();
        Customer customer = bank.registerCustomer(prompt("Customer full name"), prompt("National ID (CNIC)"));
        String typeText = prompt("Account type (S = Savings, C = Current)").toUpperCase();
        AccountType type = typeText.startsWith("C") ? AccountType.CURRENT : AccountType.SAVINGS;
        double opening = InputValidator.openingAmount(prompt("Opening balance (PKR)"));
        Account account = bank.openAccount(customer.getCustomerId(), type, opening, HOME_COUNTRY);
        System.out.printf("Account %s opened for %s (%s).%n", account.getAccountNumber(),
                customer.getFullName(), type);
    }

    private static void deposit(BankingSystem system) {
        String accountNumber = prompt("Account number");
        double amount = promptAmount("Amount (PKR)");
        Transaction tx = system.getService().deposit(accountNumber, amount);
        System.out.printf("Deposit %s complete. New balance: PKR %,.2f%n", tx.getTransactionId(),
                system.getService().getBank().getAccount(accountNumber).getBalance());
    }

    private static void withdraw(BankingSystem system) {
        String accountNumber = prompt("Account number");
        double amount = promptAmount("Amount (PKR)");
        String location = promptLocation();
        printOutcome(system.getService().withdraw(accountNumber, amount, location, LocalDateTime.now()));
    }

    private static void transfer(BankingSystem system) {
        String from = prompt("From account number");
        String to = prompt("To account number");
        double amount = promptAmount("Amount (PKR)");
        String location = promptLocation();
        printOutcome(system.getService().transfer(from, to, amount, location, LocalDateTime.now()));
    }

    private static void statement(BankingSystem system) {
        Account account = system.getService().getBank().getAccount(prompt("Account number"));
        System.out.printf("%n%s  %s  (%s)%n", account.getAccountNumber(), account.getOwner().getFullName(),
                account.getOwner().getMaskedNationalId());
        System.out.printf("Type: %s    Balance: PKR %,.2f%n", account.getType(), account.getBalance());
        if (account.getTransactions().isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        System.out.printf("%-11s %-12s %-12s %16s %-10s %5s%n", "TXN ID", "TIME", "TYPE", "AMOUNT (PKR)", "STATUS", "RISK");
        for (Transaction t : account.getTransactions()) {
            System.out.printf("%-11s %-12s %-12s %,16.2f %-10s %5d%n", t.getTransactionId(),
                    t.getTimestamp().format(TIME_FORMAT), t.getType(), t.getAmount(), t.getStatus(), t.getRiskScore());
        }
    }

    private static void alertDashboard(BankingSystem system) {
        AuditLogObserver audit = system.getAuditLog();
        List<Alert> alerts = audit.getAlerts();
        System.out.printf("Total alerts: %d%n", alerts.size());
        for (Severity severity : Severity.values()) {
            System.out.printf("  %-9s %d%n", severity, audit.countBySeverity(severity));
        }
        alerts.forEach(a -> System.out.println("  " + a));
    }

    private static void printOutcome(Transaction tx) {
        System.out.printf("Outcome: %s | %s | risk score %d/100%n", tx.getStatus(),
                tx.getTransactionId(), tx.getRiskScore());
    }

    private static String prompt(String label) {
        System.out.print(label + ": ");
        return IN.hasNextLine() ? IN.nextLine().trim() : "0";
    }

    private static double promptAmount(String label) {
        return InputValidator.positiveAmount(prompt(label), "Amount");
    }

    private static String promptLocation() {
        String code = prompt("Transaction country code (blank = " + HOME_COUNTRY + ")");
        return InputValidator.countryCode(code.isEmpty() ? HOME_COUNTRY : code);
    }
}
