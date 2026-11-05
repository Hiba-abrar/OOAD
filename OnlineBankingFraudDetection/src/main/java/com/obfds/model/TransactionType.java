package com.obfds.model;

public enum TransactionType {
    DEPOSIT(false),
    WITHDRAWAL(true),
    TRANSFER_OUT(true),
    TRANSFER_IN(false);

    private final boolean outgoing;

    TransactionType(boolean outgoing) {
        this.outgoing = outgoing;
    }

    /** True when money leaves the account; these are the movements fraud rules screen. */
    public boolean isOutgoing() {
        return outgoing;
    }
}
