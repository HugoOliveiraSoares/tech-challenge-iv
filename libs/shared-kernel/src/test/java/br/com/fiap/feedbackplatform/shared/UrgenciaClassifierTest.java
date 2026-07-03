package br.com.fiap.feedbackplatform.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrgenciaClassifierTest {
    @Test
    void classificaNotasCriticas() {
        assertEquals(Urgencia.CRITICA, UrgenciaClassifier.classify(0));
        assertEquals(Urgencia.CRITICA, UrgenciaClassifier.classify(3));
    }

    @Test
    void classificaNotasMedias() {
        assertEquals(Urgencia.MEDIA, UrgenciaClassifier.classify(4));
        assertEquals(Urgencia.MEDIA, UrgenciaClassifier.classify(6));
    }

    @Test
    void classificaNotasBaixas() {
        assertEquals(Urgencia.BAIXA, UrgenciaClassifier.classify(7));
        assertEquals(Urgencia.BAIXA, UrgenciaClassifier.classify(10));
    }

    @Test
    void rejeitaNotasForaDoIntervalo() {
        assertThrows(IllegalArgumentException.class, () -> UrgenciaClassifier.classify(-1));
        assertThrows(IllegalArgumentException.class, () -> UrgenciaClassifier.classify(11));
    }
}
