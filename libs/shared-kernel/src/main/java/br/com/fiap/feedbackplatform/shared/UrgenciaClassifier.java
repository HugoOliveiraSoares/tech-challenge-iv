package br.com.fiap.feedbackplatform.shared;

public final class UrgenciaClassifier {
    private UrgenciaClassifier() {
    }

    public static Urgencia classify(int nota) {
        if (nota < 0 || nota > 10) {
            throw new IllegalArgumentException("Nota deve estar entre 0 e 10.");
        }

        if (nota <= 3) {
            return Urgencia.CRITICA;
        }

        if (nota <= 6) {
            return Urgencia.MEDIA;
        }

        return Urgencia.BAIXA;
    }
}
