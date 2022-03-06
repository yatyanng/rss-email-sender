package app.rssemailsender.config;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HeaderIterator;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import app.rssemailsender.Constants;

@Configuration
public class RestConfig {

  @Value(Constants.CFG_COUCHDB_CONNECTION_TIMEOUT)
  private int connectTimeout;

  @Value(Constants.CFG_COUCHDB_READ_TIMEOUT)
  private int readTimeout;

  @Value(Constants.CFG_COUCHDB_CONNECTION_POOL_SIZE)
  private int internalConnectionPoolSize;

  @Bean
  @Scope(BeanDefinition.SCOPE_SINGLETON)
  public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
    PoolingHttpClientConnectionManager poolingConnectionManager =
        new PoolingHttpClientConnectionManager();
    poolingConnectionManager.setMaxTotal(internalConnectionPoolSize);
    poolingConnectionManager.setDefaultMaxPerRoute(internalConnectionPoolSize);
    return poolingConnectionManager;
  }

  @Bean
  @Scope(BeanDefinition.SCOPE_SINGLETON)
  public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
    return (httpResponse, httpContext) -> {
      HeaderIterator headerIterator = httpResponse.headerIterator(HTTP.CONN_KEEP_ALIVE);
      HeaderElementIterator elementIterator = new BasicHeaderElementIterator(headerIterator);
      while (elementIterator.hasNext()) {
        HeaderElement element = elementIterator.nextElement();
        String param = element.getName();
        String value = element.getValue();
        if (value != null && param.equalsIgnoreCase(Constants.PARAM_TIMEOUT)) {
          return Long.parseLong(value) * Constants.MILLISECONDS_IN_ONE_SECOND; // convert to ms
        }
      }
      return Constants.MILLISECONDS_IN_ONE_SECOND;
    };
  }

  @Bean(name = Constants.BEAN_COUCHDB_REST_CLIENT)
  public CloseableHttpClient couchdbRestClient()
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout((int) TimeUnit.MILLISECONDS.convert(connectTimeout, TimeUnit.SECONDS))
        .setConnectionRequestTimeout(connectTimeout)
        .setSocketTimeout((int) TimeUnit.MILLISECONDS.convert(readTimeout, TimeUnit.SECONDS))
        .build();
    return HttpClients.custom().setDefaultRequestConfig(requestConfig)
        .setKeepAliveStrategy(connectionKeepAliveStrategy())
        .setConnectionManager(poolingHttpClientConnectionManager()).build();
  }

  @Bean(name = Constants.BEAN_COUCHDB_REST_TEMPLATE)
  public RestTemplate couchdbRestTemplate(CloseableHttpClient restClient) {
    HttpComponentsClientHttpRequestFactory clientFactory =
        new HttpComponentsClientHttpRequestFactory();
    clientFactory.setHttpClient(restClient);
    return new RestTemplate(clientFactory);
  }
}
