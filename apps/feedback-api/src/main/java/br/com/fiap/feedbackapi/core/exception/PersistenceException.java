package br.com.fiap.feedbackapi.core.exception;

public class PersistenceException extends RuntimeException {
    public PersistenceException(String message, Throwable cause){
        super(message, cause);
    }
}
