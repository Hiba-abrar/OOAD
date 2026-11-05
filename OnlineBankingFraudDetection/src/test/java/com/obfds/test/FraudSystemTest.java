package com.obfds.test;

import com.obfds.exception.AccountNotFoundException;
import com.obfds.exception.InsufficientFundsException;
import com.obfds.factory.AccountFactory;
import com.obfds.fraud.Decision;
import com.obfds.fraud.FraudDetectionEngine;
import com.obfds.fraud.ForeignLocationRule;
import com.obfds.fraud.HighAmountRule;
import com.obfds.fraud.UnusualHourRule;
import com.obfds.fraud.VelocityRule;
import com.obfds.fraud.RiskAssessment;
import com.obfds.model.Account;
import com.obfds.model.AccountType;
import com.obfds.model.Alert;
import com.obfds.model.CurrentAccount;
import com.obfds.model.Customer;
import com.obfds.model.SavingsAccount;
import com.obfds.model.Severity;
import com.obfds.model.Transaction;
import com.obfds.model.TransactionStatus;
import com.obfds.model.TransactionType;
import com.obfds.monitoring.AlertPublisher;
import com.obfds.monitoring.AuditLogObserver;
import com.obfds.service.Bank;
import com.obfds.service.BankingService;
import com.obfds.validation.InputValidator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Self-contained test runner (no external libraries). Exit code 1 if any check fails. */
public class FraudSystemTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 5, 15, 0);
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testFactoryCreatesCorrectSubtypes();
        testSavingsRespectsMinimumBalance();
        testCurrentAccountAllowsOverdraft();
        testHighAmountRule();
        testForeignLocationRule();
        testUnusualHourRule();
        testVelocityRuleDetectsBurst();
        testVelocityRuleIgnoresOldTransactions();
        testEngineDecisions();
        testBlockedTransferLeavesBalancesUnchanged();
        testCompletedTransferUpdatesBothAccounts();
        testAlertsReachObservers();
        testAccountNotFound();
        testInvalidAmountRejected();
        testCustomerAggregatesAccounts();
        testSelfTransferIsFlaggedAndBalanceUnchanged();
        testTransferToOutsideAccountDebitsSenderOnly();
        testLargeExternalTransferIsBlocked();
        testNewPayeeFlaggedOnlyFirstTime();
        testTransferToRandomAccountNumberWorks();
        testInvalidRecipientNumberRejected();
        testRulesHaveDescriptions();
        testSelfTransferFlaggedTwiceThenBlocked();
        testForeignTransferIsFlagged();
        testNamesRejectNumbers();
        testCnicMustBeFiveDigits();
        testAmountsRejectLetters();
        testCountryCodeMustExist();

        System.out.printf("%nResults: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFactoryCreatesCorrectSubtypes() {
        Customer c = new Customer("CUS-T", "Test Customer", "12345");
        Account s = AccountFactory.create(AccountType.SAVINGS, "ACC-S", c, 1000, "PK");
        Account cur = AccountFactory.create(AccountType.CURRENT, "ACC-C", c, 1000, "PK");
        check("Factory creates SavingsAccount for SAVINGS", s instanceof SavingsAccount && s.getType() == AccountType.SAVINGS);
        check("Factory creates CurrentAccount for CURRENT", cur instanceof CurrentAccount && cur.getType() == AccountType.CURRENT);
    }

    private static void testSavingsRespectsMinimumBalance() {
        Account a = newSavings(10_000);
        expectThrows("Savings debit above (balance - minimum) is rejected", InsufficientFundsException.class, () -> a.debit(6_000));
        a.debit(5_000);
        check("Savings debit down to minimum balance succeeds", a.getBalance() == 5_000);
    }

    private static void testCurrentAccountAllowsOverdraft() {
        Account c = new CurrentAccount("ACC-C2", new Customer("CUS-T", "Test Person", "12345"), 1_000, "PK", 5_000);
        c.debit(5_500);
        check("Current account may overdraw up to the overdraft limit", c.getBalance() == -4_500);
    }

    private static void testHighAmountRule() {
        HighAmountRule rule = new HighAmountRule(500_000);
        Optional<Alert> high = rule.evaluate(outgoing(600_000, BASE, "PK"), newSavings(0));
        Optional<Alert> critical = rule.evaluate(outgoing(1_200_000, BASE, "PK"), newSavings(0));
        Optional<Alert> small = rule.evaluate(outgoing(1_000, BASE, "PK"), newSavings(0));
        check("HighAmountRule raises HIGH for 600,000", high.isPresent() && high.get().getSeverity() == Severity.HIGH);
        check("HighAmountRule raises CRITICAL for 1,200,000", critical.isPresent() && critical.get().getSeverity() == Severity.CRITICAL);
        check("HighAmountRule ignores small amounts", small.isEmpty());
    }

    private static void testForeignLocationRule() {
        ForeignLocationRule rule = new ForeignLocationRule();
        check("ForeignLocationRule flags transaction from AE for PK account",
                rule.evaluate(outgoing(40_000, BASE, "AE"), newSavings(0)).isPresent());
        check("ForeignLocationRule ignores domestic transaction",
                rule.evaluate(outgoing(40_000, BASE, "PK"), newSavings(0)).isEmpty());
    }

    private static void testUnusualHourRule() {
        UnusualHourRule rule = new UnusualHourRule(100_000);
        check("UnusualHourRule flags 150,000 at 02:00",
                rule.evaluate(outgoing(150_000, LocalDateTime.of(2026, 11, 5, 2, 0), "PK"), newSavings(0)).isPresent());
        check("UnusualHourRule ignores 150,000 at 12:00",
                rule.evaluate(outgoing(150_000, LocalDateTime.of(2026, 11, 5, 12, 0), "PK"), newSavings(0)).isEmpty());
    }

    private static void testVelocityRuleDetectsBurst() {
        VelocityRule rule = new VelocityRule(5, Duration.ofMinutes(10));
        Account a = newSavings(1_000_000);
        for (int i = 0; i < 5; i++) {
            a.recordTransaction(outgoing(1_000, BASE.plusMinutes(i), "PK"));
        }
        check("VelocityRule flags the 6th outgoing transaction in 10 minutes",
                rule.evaluate(outgoing(1_000, BASE.plusMinutes(6), "PK"), a).isPresent());
    }

    private static void testVelocityRuleIgnoresOldTransactions() {
        VelocityRule rule = new VelocityRule(5, Duration.ofMinutes(10));
        Account a = newSavings(1_000_000);
        for (int i = 0; i < 5; i++) {
            a.recordTransaction(outgoing(1_000, BASE.minusMinutes(60 + i), "PK"));
        }
        check("VelocityRule ignores transactions outside the window",
                rule.evaluate(outgoing(1_000, BASE, "PK"), a).isEmpty());
    }

    private static void testEngineDecisions() {
        FraudDetectionEngine engine = FraudDetectionEngine.withDefaultRules();
        Account a = newSavings(10_000_000);
        check("Engine ALLOWs a routine transaction",
                engine.assess(outgoing(15_000, BASE, "PK"), a).getDecision() == Decision.ALLOW);
        check("Engine FLAGs a medium-severity transaction",
                engine.assess(outgoing(40_000, BASE, "AE"), a).getDecision() == Decision.FLAG);
        RiskAssessment blocked = engine.assess(outgoing(600_000, BASE, "PK"), a);
        check("Engine BLOCKs a high-severity transaction", blocked.getDecision() == Decision.BLOCK);
        check("Engine risk score adds HIGH_AMOUNT and NEW_PAYEE_LARGE weights",
                blocked.getRiskScore() == Severity.HIGH.getWeight() + Severity.MEDIUM.getWeight());
    }

    private static void testBlockedTransferLeavesBalancesUnchanged() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), s.a2.getAccountNumber(), 600_000, "PK", BASE);
        check("Blocked transfer is recorded with status BLOCKED", tx.getStatus() == TransactionStatus.BLOCKED);
        check("Blocked transfer does not change sender balance", s.a1.getBalance() == 800_000);
        check("Blocked transfer does not change receiver balance", s.a2.getBalance() == 0);
    }

    private static void testCompletedTransferUpdatesBothAccounts() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), s.a2.getAccountNumber(), 20_000, "PK", BASE);
        check("Completed transfer has status COMPLETED", tx.getStatus() == TransactionStatus.COMPLETED);
        check("Sender balance reduced by transfer amount", s.a1.getBalance() == 780_000);
        check("Receiver balance increased by transfer amount", s.a2.getBalance() == 20_000);
        check("Sender history holds a TRANSFER_OUT entry",
                s.a1.getTransactions().size() == 1 && s.a1.getTransactions().get(0).getType() == TransactionType.TRANSFER_OUT);
        check("Receiver history holds a TRANSFER_IN entry",
                s.a2.getTransactions().size() == 1 && s.a2.getTransactions().get(0).getType() == TransactionType.TRANSFER_IN);
    }

    private static void testAlertsReachObservers() {
        AuditLogObserver audit = new AuditLogObserver();
        AlertPublisher publisher = new AlertPublisher();
        publisher.subscribe(audit);
        Bank bank = new Bank("Test Bank");
        Account a1 = bank.openAccount(bank.registerCustomer("Alpha Khan", "11111").getCustomerId(), AccountType.SAVINGS, 800_000, "PK");
        Account a2 = bank.openAccount(bank.registerCustomer("Beta Khan", "22222").getCustomerId(), AccountType.CURRENT, 0, "PK");
        BankingService svc = new BankingService(bank, FraudDetectionEngine.withDefaultRules(), publisher);
        svc.transfer(a1.getAccountNumber(), a2.getAccountNumber(), 600_000, "PK", BASE);
        check("Observer receives the HIGH_AMOUNT alert", audit.countBySeverity(Severity.HIGH) == 1);
    }

    private static void testAccountNotFound() {
        Bank bank = new Bank("Test Bank");
        expectThrows("Unknown account number throws AccountNotFoundException", AccountNotFoundException.class,
                () -> bank.getAccount("ACC-UNKNOWN"));
    }

    private static void testInvalidAmountRejected() {
        Setup s = setup();
        expectThrows("Negative deposit is rejected", IllegalArgumentException.class,
                () -> s.service.deposit(s.a1.getAccountNumber(), -5));
    }

    private static void testCustomerAggregatesAccounts() {
        Setup s = setup();
        check("Customer aggregates the account opened for them", s.a1.getOwner().getAccounts().contains(s.a1));
    }

    private static void testSelfTransferIsFlaggedAndBalanceUnchanged() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), s.a1.getAccountNumber(), 20_000, "PK", BASE);
        check("Self-transfer is flagged (status COMPLETED, risk = MEDIUM)",
                tx.getStatus() == TransactionStatus.COMPLETED && tx.getRiskScore() == Severity.MEDIUM.getWeight());
        check("Self-transfer leaves the balance unchanged", s.a1.getBalance() == 800_000);
    }

    private static void testTransferToOutsideAccountDebitsSenderOnly() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), "EXT123456", 50_000, "PK", BASE);
        check("Transfer to an account outside the bank completes", tx.getStatus() == TransactionStatus.COMPLETED);
        check("Outside transfer is marked as external", tx.isExternalCounterparty());
        check("Outside transfer debits the sender", s.a1.getBalance() == 750_000);
        check("Outside transfer does not credit any local account", s.a2.getBalance() == 0);
    }

    private static void testLargeExternalTransferIsBlocked() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), "EXT123456", 250_000, "PK", BASE);
        check("Large transfer to an outside account is BLOCKED", tx.getStatus() == TransactionStatus.BLOCKED);
        check("Blocked outside transfer leaves balance unchanged", s.a1.getBalance() == 800_000);
    }

    private static void testNewPayeeFlaggedOnlyFirstTime() {
        Setup s = setup();
        Transaction first = s.service.transfer(s.a1.getAccountNumber(), "EXT999999", 150_000, "PK", BASE);
        check("First payment to a new recipient is flagged", first.getRiskScore() == Severity.MEDIUM.getWeight()
                && first.getStatus() == TransactionStatus.COMPLETED);
        Transaction second = s.service.transfer(s.a1.getAccountNumber(), "EXT999999", 150_000, "PK",
                BASE.plusMinutes(30));
        check("Second payment to the same recipient is not flagged as new payee", second.getRiskScore() == 0
                && second.getStatus() == TransactionStatus.COMPLETED);
    }

    private static void testTransferToRandomAccountNumberWorks() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), "pk12345678", 1_000, "PK", BASE);
        check("Transfer to a random account number works (case-insensitive)",
                tx.getStatus() == TransactionStatus.COMPLETED && tx.getCounterpartyAccount().equals("PK12345678"));
    }

    private static void testInvalidRecipientNumberRejected() {
        Setup s = setup();
        expectThrows("Recipient with invalid characters is rejected", IllegalArgumentException.class,
                () -> s.service.transfer(s.a1.getAccountNumber(), "BAD!ACC", 1_000, "PK", BASE));
        expectThrows("Recipient number that is too short is rejected", IllegalArgumentException.class,
                () -> s.service.transfer(s.a1.getAccountNumber(), "12", 1_000, "PK", BASE));
    }

    private static void testRulesHaveDescriptions() {
        boolean all = true;
        for (com.obfds.fraud.FraudRule r : FraudDetectionEngine.withDefaultRules().getRules()) {
            all &= r.getDescription() != null && !r.getDescription().isBlank();
        }
        check("Every fraud rule has a description for the UI", all);
    }

    private static void testSelfTransferFlaggedTwiceThenBlocked() {
        Setup s = setup();
        String me = s.a1.getAccountNumber();
        Transaction first = s.service.transfer(me, me, 10_000, "PK", BASE);
        Transaction second = s.service.transfer(me, me, 10_000, "PK", BASE.plusMinutes(1));
        Transaction third = s.service.transfer(me, me, 10_000, "PK", BASE.plusMinutes(2));
        check("Self-transfer #1 is flagged", first.getStatus() == TransactionStatus.COMPLETED
                && first.getRiskScore() == Severity.MEDIUM.getWeight());
        check("Self-transfer #2 is flagged", second.getStatus() == TransactionStatus.COMPLETED
                && second.getRiskScore() == Severity.MEDIUM.getWeight());
        check("Self-transfer #3 is BLOCKED", third.getStatus() == TransactionStatus.BLOCKED);
    }

    private static void testForeignTransferIsFlagged() {
        Setup s = setup();
        Transaction tx = s.service.transfer(s.a1.getAccountNumber(), s.a2.getAccountNumber(), 300_000, "AE", BASE);
        check("Very large transfer from another country is flagged (not blocked)",
                tx.getStatus() == TransactionStatus.COMPLETED && tx.getRiskScore() > 0);
    }

    private static void testNamesRejectNumbers() {
        expectThrows("Customer name containing digits is rejected", IllegalArgumentException.class,
                () -> new Customer("CUS-X", "Ali123", "12345"));
        check("Valid customer name with dot and apostrophe is accepted",
                new Customer("CUS-X", "Dr. O'Brien", "12345").getFullName().equals("Dr. O'Brien"));
    }

    private static void testCnicMustBeFiveDigits() {
        expectThrows("CNIC with 13 digits is rejected", IllegalArgumentException.class,
                () -> new Customer("CUS-X", "Ali Raza", "3520112345678"));
        expectThrows("CNIC with 4 digits is rejected", IllegalArgumentException.class,
                () -> new Customer("CUS-X", "Ali Raza", "1234"));
        expectThrows("CNIC with letters is rejected", IllegalArgumentException.class,
                () -> new Customer("CUS-X", "Ali Raza", "AB123"));
        check("CNIC of exactly 5 digits is accepted", new Customer("CUS-X", "Ali Raza", "12345").getMaskedNationalId().endsWith("2345"));
    }

    private static void testAmountsRejectLetters() {
        expectThrows("Amount with letters is rejected", IllegalArgumentException.class,
                () -> InputValidator.positiveAmount("12a", "Amount"));
        expectThrows("Amount with scientific notation is rejected", IllegalArgumentException.class,
                () -> InputValidator.positiveAmount("1e5", "Amount"));
        expectThrows("Negative amount is rejected", IllegalArgumentException.class,
                () -> InputValidator.positiveAmount("-500", "Amount"));
        check("Amount with commas is accepted", InputValidator.positiveAmount("6,50,000", "Amount") == 650_000);
    }

    private static void testCountryCodeMustExist() {
        expectThrows("Non-existent country code is rejected", IllegalArgumentException.class,
                () -> InputValidator.countryCode("ZZ"));
        check("Valid country code is accepted with its name",
                InputValidator.countryCode("ae").equals("AE") && InputValidator.countryName("AE").contains("Emirates"));
    }

    // ---- helpers -------------------------------------------------------------------------

    private record Setup(BankingService service, Account a1, Account a2) {}

    private static Setup setup() {
        Bank bank = new Bank("Test Bank");
        Account a1 = bank.openAccount(bank.registerCustomer("Alpha Khan", "11111").getCustomerId(), AccountType.SAVINGS, 800_000, "PK");
        Account a2 = bank.openAccount(bank.registerCustomer("Beta Khan", "22222").getCustomerId(), AccountType.CURRENT, 0, "PK");
        BankingService service = new BankingService(bank, FraudDetectionEngine.withDefaultRules(), new AlertPublisher());
        return new Setup(service, a1, a2);
    }

    private static Account newSavings(double opening) {
        Customer owner = new Customer("CUS-T", "Test Customer", "12345");
        return AccountFactory.create(AccountType.SAVINGS, "ACC-T1", owner, opening, "PK");
    }

    private static Transaction outgoing(double amount, LocalDateTime when, String location) {
        return new Transaction(TransactionType.TRANSFER_OUT, amount, "ACC-T1", "ACC-T2", location, when);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void expectThrows(String name, Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            check(name, false);
        } catch (RuntimeException e) {
            check(name, type.isInstance(e));
        }
    }
}
