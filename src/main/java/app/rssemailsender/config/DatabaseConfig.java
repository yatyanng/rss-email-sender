package app.rssemailsender.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import app.rssemailsender.Constants;
import app.rssemailsender.mapper.EmailMeterMapper;

@Configuration
public class DatabaseConfig {

  @Value(Constants.CFG_APP_DATABASE_URL)
  private String dbUrl;

  @Value(Constants.CFG_APP_DATABASE_USERNAME)
  private String dbUsername;

  @Value(Constants.CFG_APP_DATABASE_PASSWORD)
  private String dbPassword;

  @Bean(Constants.BEAN_MYSQL_SESSION_FACTORY)
  public SqlSessionFactory sqlSessionFactory() throws IOException {
    org.apache.ibatis.logging.LogFactory.useSlf4jLogging();
    InputStream inputStream =
        new ClassPathResource(Constants.CONST_MYBATIS_MYSQL_CONFIG).getInputStream();
    Properties props = new Properties();
    props.setProperty(Constants.PARAM_DB_URL, dbUrl);
    props.setProperty(Constants.PARAM_DB_USERNAME, dbUsername);
    props.setProperty(Constants.PARAM_DB_PASSWORD, dbPassword);
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream, props);
    factory.getConfiguration().addMapper(EmailMeterMapper.class);
    inputStream.close();
    return factory;
  }

}
