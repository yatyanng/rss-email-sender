package app.rssemailsender.service;

import java.text.SimpleDateFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.rssemailsender.Constants;

@Component
public class KafkaPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Value(value = Constants.CFG_APP_KAFKA_TOPIC)
  private String topicName;

  protected void publishException(Exception e) {
    try {
      String json = new ObjectMapper().writeValueAsString(Map.of("time",
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(System.currentTimeMillis()),
          "message", e.toString()));
      ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topicName, json);
      future.addCallback(result -> log.info("The exception is published: {}", result.toString()),
          ex -> log.error("The exception is not published.", ex));
    } catch (JsonProcessingException jpe) {
      log.error("publishException error!", jpe);
    }
  }
}
