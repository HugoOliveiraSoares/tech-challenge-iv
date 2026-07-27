package br.com.fiap.criticalnotifier.core.exception;

public class EmailSendAmbiguousException extends RuntimeException {
    public EmailSendAmbiguousException(String message, Throwable cause) {
        super(message, cause);
    }
}
