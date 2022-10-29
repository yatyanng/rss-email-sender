package app.rssemailsender;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    MutableInt rc = new MutableInt(0);
    String mode = System.getenv(Constants.ENV_MODE);
    String target = System.getenv(Constants.ENV_TARGET);
    log.debug("mode: {}, target: {}", mode, target);

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    Pair<Integer, Callable<Set<String>>> servicePair = switch (mode) {
      case Constants.CONST_JSOUP -> Pair.of(Constants.CODE_JSOUP_SERVICE, jsoupService);
      case Constants.CONST_XALAN -> Pair.of(Constants.CODE_XALAN_SERVICE, xalanService);
      case Constants.CONST_JSON_API -> Pair.of(Constants.CODE_JSON_API_SERVICE, jsonApiService);
      default -> throw new IllegalArgumentException("Unexpected value: " + mode);
    };

    List<Future<Set<String>>> futures = executor.invokeAll(List.of(servicePair.getRight()));
    futures.forEach(t -> {
      try {
        Set<String> errorSet = t.get();
        if (!errorSet.isEmpty()) {
          log.error("call service error: {}", errorSet);
          rc.add(servicePair.getLeft());
        }
      } catch (Exception e) {
        log.error("call service error!", e);
      }
    });

    log.info("{} {} has ended, rc={}", buildProperties.getArtifact(), buildProperties.getVersion(),
        rc);
    return rc.intValue();
  }

}
