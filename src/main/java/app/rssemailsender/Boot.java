package app.rssemailsender;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import app.rssemailsender.service.JsonApiService;
import app.rssemailsender.service.JsoupService;
import app.rssemailsender.service.XalanService;

@SpringBootApplication
public class Boot {

  private static final Logger log = LoggerFactory.getLogger(Boot.class);

  @Autowired
  private BuildProperties buildProperties;

  @Autowired
  private JsoupService jsoupService;

  @Autowired
  private XalanService xalanService;

  @Autowired
  private JsonApiService jsonApiService;

  @Autowired
  private Consumer<String, String> kafkaConsumer;

  @Value(Constants.CFG_APP_KAFKA_CONSUMER_POLL_TIMEOUT)
  private Long pollTimeout;

  public static void main(String[] args) throws Exception {
    String configDirectory = "conf";
    if (args.length > 0) {
      configDirectory = args[0];
    }
    log.info("config directory: {}", configDirectory);
    System.setProperty("spring.config.location", configDirectory + "/springboot.yml");
    System.setProperty("logging.config", configDirectory + "/logback.xml");
    System.setProperty("javamail.config", configDirectory + "/javamail.properties");

    ApplicationContext ac = SpringApplication.run(Boot.class, args);
    int rc = ac.getBean(Boot.class).run(configDirectory);
    System.exit(rc);
  }

  public int run(String configDirectory) throws Exception {
    log.info("{} {} is started with configDirectory: {}", buildProperties.getArtifact(),
        buildProperties.getVersion(), configDirectory);
    int rc = 0;
    String mode = System.getenv(Constants.ENV_MODE);
    String target = System.getenv(Constants.ENV_TARGET);
    log.debug("mode: {}, target: {}", mode, target);
    
    if (StringUtils.equals(Constants.CONST_KAFKA, mode)) {
      while (!Thread.currentThread().isInterrupted()) {
        final ConsumerRecords<String, String> consumerRecords =
            kafkaConsumer.poll(Duration.of(pollTimeout, ChronoUnit.SECONDS));
        log.info("Consumer Record count={}", consumerRecords.count());
    
        if (consumerRecords.count() > 0) {
          consumerRecords.forEach(record -> {
            log.info("Consumer Record (\n\tkey={},\n\tvalue={},\n\tpartition={},\n\toffset={}\n)",
                record.key(), record.value(), record.partition(), record.offset());
          });
          kafkaConsumer.commitAsync();
        }
      }
    }
    if (StringUtils.equals(Constants.CONST_JSOUP, mode)) {
      jsoupService.run();
      if (!jsoupService.getErrorSet().isEmpty()) {
        log.error("jsoup service error: {}", jsoupService.getErrorSet());
        rc += Constants.CODE_JSOUP_SERVICE;
      }
    }
    if (StringUtils.equals(Constants.CONST_XALAN, mode)) {
      xalanService.run();
      if (!xalanService.getErrorSet().isEmpty()) {
        log.error("xalan service error: {}", xalanService.getErrorSet());
        rc += Constants.CODE_XALAN_SERVICE;
      }
    }
    if (StringUtils.equals(Constants.CONST_JSON_API, mode)) {
      jsonApiService.run();
      if (!jsonApiService.getErrorSet().isEmpty()) {
        log.error("jsonApi service error: {}", jsonApiService.getErrorSet());
        rc += Constants.CODE_JSON_API_SERVICE;
      }
    }
    log.info("{} {} has ended, rc={}", buildProperties.getArtifact(), buildProperties.getVersion(),
        rc);
    return rc;
  }

}
