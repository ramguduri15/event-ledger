package com.eventledger.account.exception;

public class InvalidTransactionTypeException extends RuntimeException {

    public InvalidTransactionTypeException(String type) {
        super("Invalid transaction type '" + type + "'. Allowed values: CREDIT, DEBIT");
    }
}
