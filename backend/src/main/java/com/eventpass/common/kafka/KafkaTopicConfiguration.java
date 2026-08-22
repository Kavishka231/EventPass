package com.eventpass.common.kafka;

import java.util.*;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnProperty(
    name = "eventpass.kafka.provisioning-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaTopicConfiguration {
  private final int partitions;
  private final int replicationFactor;
  private final long retentionMilliseconds;
  private final long deadLetterRetentionMilliseconds;

  public KafkaTopicConfiguration(
      @Value("${eventpass.kafka.topics.partitions:6}") int partitions,
      @Value("${eventpass.kafka.topics.replication-factor:1}") int replicationFactor,
      @Value("${eventpass.kafka.topics.retention:604800000}") long retentionMilliseconds,
      @Value("${eventpass.kafka.topics.dlt-retention:2592000000}")
          long deadLetterRetentionMilliseconds) {
    this.partitions = partitions;
    this.replicationFactor = replicationFactor;
    this.retentionMilliseconds = retentionMilliseconds;
    this.deadLetterRetentionMilliseconds = deadLetterRetentionMilliseconds;
  }

  @Bean
  KafkaAdmin.NewTopics eventPassTopics() {
    return new KafkaAdmin.NewTopics(topics().toArray(NewTopic[]::new));
  }

  List<NewTopic> topics() {
    List<NewTopic> topics = new ArrayList<>();
    KafkaTopics.DOMAIN_TOPICS.forEach(name -> topics.add(topic(name, retentionMilliseconds)));
    KafkaTopics.CONSUMED_TOPICS.forEach(
        name -> topics.add(topic(KafkaTopics.deadLetter(name), deadLetterRetentionMilliseconds)));
    return List.copyOf(topics);
  }

  private NewTopic topic(String name, long retention) {
    return TopicBuilder.name(name)
        .partitions(partitions)
        .replicas(replicationFactor)
        .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention))
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .config(
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            Integer.toString(replicationFactor > 1 ? 2 : 1))
        .build();
  }
}
