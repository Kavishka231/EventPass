package com.eventpass.common.health;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.actuate.health.*;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Component("kafka")
public class KafkaHealthIndicator extends AbstractHealthIndicator {
  private final KafkaAdmin kafkaAdmin;

  public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
    this.kafkaAdmin = kafkaAdmin;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    Map<String, Object> properties = new HashMap<>(kafkaAdmin.getConfigurationProperties());
    properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
    properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
    try (AdminClient client = AdminClient.create(properties)) {
      String clusterId =
          client
              .describeCluster()
              .clusterId()
              .get(Duration.ofSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      builder.up().withDetail("clusterId", clusterId);
    }
  }
}
