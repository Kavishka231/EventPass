package com.eventpass.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionInfrastructureValidatorTest {
  @Test
  void acceptsAuthenticatedTlsInfrastructure() {
    ProductionInfrastructureValidator validator =
        validator("redis-secret", true, "SASL_SSL", "SCRAM-SHA-512", "required credentials");

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void rejectsRedisWithoutAuthenticationOrTls() {
    assertThatThrownBy(
            () ->
                validator("", true, "SASL_SSL", "SCRAM-SHA-512", "credentials")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("REDIS_PASSWORD");
    assertThatThrownBy(
            () ->
                validator("secret", false, "SASL_SSL", "SCRAM-SHA-512", "credentials")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Redis connections must use TLS");
  }

  @Test
  void rejectsKafkaWithoutAuthenticatedTls() {
    assertThatThrownBy(
            () ->
                validator("secret", true, "PLAINTEXT", "SCRAM-SHA-512", "credentials")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SASL_SSL");
    assertThatThrownBy(
            () -> validator("secret", true, "SASL_SSL", "SCRAM-SHA-512", "").afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("KAFKA_SASL_JAAS_CONFIG");
  }

  private ProductionInfrastructureValidator validator(
      String redisPassword,
      boolean redisTls,
      String kafkaProtocol,
      String kafkaMechanism,
      String kafkaJaas) {
    return new ProductionInfrastructureValidator(
        redisPassword, redisTls, kafkaProtocol, kafkaMechanism, kafkaJaas);
  }
}
