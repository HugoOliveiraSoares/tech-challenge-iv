package br.com.fiap.criticalnotifier.infra.gateway.ses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

@ExtendWith(MockitoExtension.class)
class SesCriticalEmailGatewayTest {
    @Mock
    SesClient sesClient;

    private SesCriticalEmailGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SesCriticalEmailGateway(sesClient, "admin@example.com", "no-reply@example.com");
    }

    @Test
    void enviaEmailComContratoEsperado() {
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("message-1").build());

        CriticalNotificationEmail email = new CriticalNotificationEmail(
                "ALERTA: Feedback critico recebido",
                "Corpo do alerta");

        gateway.sendCriticalNotification(email);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(requestCaptor.capture());

        SendEmailRequest request = requestCaptor.getValue();
        assertEquals("no-reply@example.com", request.source());
        assertEquals("admin@example.com", request.destination().toAddresses().getFirst());
        assertEquals("ALERTA: Feedback critico recebido", request.message().subject().data());
        assertEquals("Corpo do alerta", request.message().body().text().data());
    }

    @Test
    void propagaFalhaSesSemExporSegredos() {
        doThrow(new RuntimeException("Service unavailable"))
                .when(sesClient)
                .sendEmail(any(SendEmailRequest.class));

        CriticalNotificationEmail email = new CriticalNotificationEmail("Assunto", "Corpo");

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> gateway.sendCriticalNotification(email));

        assertEquals("Service unavailable", exception.getMessage());
    }
}
