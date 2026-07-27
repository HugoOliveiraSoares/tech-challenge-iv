package br.com.fiap.criticalnotifier.infra.gateway.ses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.exception.EmailSendAmbiguousException;
import br.com.fiap.criticalnotifier.core.exception.EmailSendRetryableException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

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
    void traduzFalhaRetryableDoSes() {
        doThrow(SesException.builder().statusCode(503).message("Service unavailable").build())
                .when(sesClient)
                .sendEmail(any(SendEmailRequest.class));

        CriticalNotificationEmail email = new CriticalNotificationEmail("Assunto", "Corpo");

        EmailSendRetryableException exception = assertThrows(
                EmailSendRetryableException.class, () -> gateway.sendCriticalNotification(email));

        assertEquals("SES rejected the request before acceptance.", exception.getMessage());
    }

    @Test
    void traduzTimeoutComoFalhaAmbigua() {
        doThrow(SdkClientException.builder()
                        .message("Request timed out")
                        .cause(new SocketTimeoutException("Read timed out"))
                        .build())
                .when(sesClient)
                .sendEmail(any(SendEmailRequest.class));

        CriticalNotificationEmail email = new CriticalNotificationEmail("Assunto", "Corpo");

        EmailSendAmbiguousException exception = assertThrows(
                EmailSendAmbiguousException.class, () -> gateway.sendCriticalNotification(email));

        assertEquals("SES request timed out with indeterminate result.", exception.getMessage());
        assertInstanceOf(SocketTimeoutException.class, exception.getCause().getCause());
    }
}
