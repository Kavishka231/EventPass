package com.eventpass.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigurationTest {
  private static final long RETENTION = 604_800_000L;
  private static final long DLT_RETENTION = 2_592_000_000L;

  @Test
  void provisionsDomainAndDeadLetterTopicsWithProductionSettings() {
    KafkaTopicConfiguration configuration =
        new KafkaTopicConfiguration(6, 3, RETENTION, DLT_RETENTION);

    Map<String, NewTopic> topics =
        configuration.topics().stream()
            .collect(Collectors.toMap(NewTopic::name, Function.identity()));

    assertThat(topics.keySet())
        .containsExactlyInAnyOrder(
            "booking.events",
            "payment.events",
            "ticket.events",
            "event.events",
            "notification.events",
            "booking.events.DLT",
            "payment.events.DLT",
            "ticket.events.DLT");
    KafkaTopics.DOMAIN_TOPICS.forEach(topic -> assertTopic(topics.get(topic), RETENTION));
    KafkaTopics.CONSUMED_TOPICS.forEach(
        topic -> assertTopic(topics.get(KafkaTopics.deadLetter(topic)), DLT_RETENTION));
  }

  private void assertTopic(NewTopic topic, long retention) {
    assertThat(topic.numPartitions()).isEqualTo(6);
    assertThat(topic.replicationFactor()).isEqualTo((short) 3);
    assertThat(topic.configs())
        .containsEntry(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention))
        .containsEntry(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .containsEntry(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");
  }
}
