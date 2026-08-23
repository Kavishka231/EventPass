package com.eventpass.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionInfrastructureValidator implements InitializingBean {
  private final String redisPassword;
  private final boolean redisTlsEnabled;
  private final String kafkaSecurityProtocol;
  private final String kafkaSaslMechanism;
  private final String kafkaJaasConfig;

  public ProductionInfrastructureValidator(
      @Value("${spring.data.redis.password:}") String redisPassword,
      @Value("${spring.data.redis.ssl.enabled:false}") boolean redisTlsEnabled,
      @Value("${spring.kafka.properties.security.protocol:}") String kafkaSecurityProtocol,
      @Value("${spring.kafka.properties.sasl.mechanism:}") String kafkaSaslMechanism,
      @Value("${spring.kafka.properties.sasl.jaas.config:}") String kafkaJaasConfig) {
    this.redisPassword = redisPassword;
    this.redisTlsEnabled = redisTlsEnabled;
    this.kafkaSecurityProtocol = kafkaSecurityProtocol;
    this.kafkaSaslMechanism = kafkaSaslMechanism;
    this.kafkaJaasConfig = kafkaJaasConfig;
  }

  @Override
  public void afterPropertiesSet() {
    require(redisPassword, "Production Redis authentication requires REDIS_PASSWORD.");
    if (!redisTlsEnabled) {
      throw new IllegalStateException("Production Redis connections must use TLS.");
    }
    if (!"SASL_SSL".equalsIgnoreCase(kafkaSecurityProtocol)) {
      throw new IllegalStateException(
          "Production Kafka must use authenticated TLS with KAFKA_SECURITY_PROTOCOL=SASL_SSL.");
    }
    require(kafkaSaslMechanism, "Production Kafka requires KAFKA_SASL_MECHANISM.");
    require(kafkaJaasConfig, "Production Kafka authentication requires KAFKA_SASL_JAAS_CONFIG.");
  }

  private void require(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalStateException(message);
  }
}
