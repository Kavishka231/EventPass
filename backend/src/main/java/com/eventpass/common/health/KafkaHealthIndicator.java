package com.eventpass.common.health;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

@Component("kafka")
public class KafkaHealthIndicator extends AbstractHealthIndicator {
  private final String bootstrapServers;

  public KafkaHealthIndicator(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    try (AdminClient client =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                2000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                2000))) {
      String clusterId =
          client
              .describeCluster()
              .clusterId()
              .get(Duration.ofSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      builder.up().withDetail("clusterId", clusterId);
    }
  }
}
