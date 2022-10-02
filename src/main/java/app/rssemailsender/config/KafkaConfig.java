package app.rssemailsender.config;

import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import app.rssemailsender.Constants;

@Configuration
public class KafkaConfig {

  @Value(value = Constants.CFG_APP_KAFKA_BOOTSTRAP_SERVER)
  private String bootstrapAddress;

  @Value(value = Constants.CFG_APP_KAFKA_TOPIC)
  private String topic;

  @Autowired
  private BuildProperties buildProperties;

  @Bean
  public Consumer<String, String> kafkaConsumer() {

    final Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, buildProperties.getArtifact());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    // Create the consumer using props.
    final Consumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(List.of(topic));
    return consumer;
  }
}
