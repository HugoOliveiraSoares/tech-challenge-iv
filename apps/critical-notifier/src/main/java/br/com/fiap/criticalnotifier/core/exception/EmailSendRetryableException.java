package br.com.fiap.criticalnotifier.core.exception;

public class EmailSendRetryableException extends RuntimeException {
    public EmailSendRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
