package app.rssemailsender.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.jayway.jsonpath.Option;
import app.rssemailsender.Constants;

@Configuration
public class AppConfig {

  @Bean(Constants.BEAN_JAVAMAIL_PROPERTIES)
  public Properties javaMailProperties() throws IOException {
    Properties javaMailProperties = new Properties();
    FileInputStream fis = new FileInputStream(System.getProperty("javamail.config"));
    javaMailProperties.load(fis);
    fis.close();
    return javaMailProperties;
  }

  @Bean(Constants.BEAN_JSON_PATH_CONFIGURATION)
  public com.jayway.jsonpath.Configuration jsonPathConfiguration() {
    com.jayway.jsonpath.Configuration configuration =
        com.jayway.jsonpath.Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
    return configuration.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
  }
}
