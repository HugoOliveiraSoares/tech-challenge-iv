package br.com.fiap.feedbackapi.infra.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "feedback")
public interface FeedbackConfig {
    String tableName();
    String criticalTopicArn();
}
