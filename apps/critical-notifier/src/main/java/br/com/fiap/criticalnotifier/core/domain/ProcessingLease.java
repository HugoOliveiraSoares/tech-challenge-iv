package br.com.fiap.criticalnotifier.core.domain;

public record ProcessingLease(String ownerToken) {
    public ProcessingLease {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Owner token e obrigatorio.");
        }
    }
}
