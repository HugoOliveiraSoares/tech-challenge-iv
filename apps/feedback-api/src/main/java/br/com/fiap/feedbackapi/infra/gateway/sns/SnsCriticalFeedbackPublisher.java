package br.com.fiap.feedbackapi.infra.gateway.sns;

import br.com.fiap.feedbackapi.core.exception.NotificationException;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;


@ApplicationScoped
public class SnsCriticalFeedbackPublisher implements CriticalFeedbackPublisher {

    private static final Logger LOGGER = Logger.getLogger(SnsCriticalFeedbackPublisher.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;

    public SnsCriticalFeedbackPublisher(SnsClient snsClient,
                                        ObjectMapper objectMapper,
                                        @ConfigProperty(name="feedback.critical-topic-arn")
                                        String topicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(CriticalFeedbackEvent event) {
        try{
            var message = objectMapper.writeValueAsString(event);

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .build();

            var messageId = snsClient.publish(request).messageId();

            LOGGER.infof("Evento de Feedback critico publicado. feedbackId=%s correlationId=%s messageId=%s",
                    event.feedbackId(), event.correlationId(), messageId);

        }catch (JsonProcessingException exception){
            LOGGER.errorf(exception,
                    "Falha ao serializar evento de feedback critico. feedbackId=%s correlationId=%s", event.feedbackId(), event.correlationId());

            throw new NotificationException("Falha ao serializar evento de feedback critico",
                    exception);

        } catch (RuntimeException exception){
            LOGGER.errorf(exception,
                    "Falha ao publicar evento de Feedback critico. feedbackId=%s correlationId=%s", event.feedbackId(), event.correlationId());

            throw new NotificationException("Falha ao publicar evento de feedback critico",
                    exception);
        }
    }
}
