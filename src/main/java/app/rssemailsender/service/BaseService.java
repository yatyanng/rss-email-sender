package app.rssemailsender.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import app.rssemailsender.Constants;

abstract public class BaseService {

  private static final Logger log = LoggerFactory.getLogger(BaseService.class);
  
  abstract String getDataURL();
  
  abstract void processRow(String id);
  
  @Autowired
  protected RestTemplate couchdbRestTemplate;
  
  @Value(Constants.CFG_COUCHDB_USERNAME)
  protected String couchDbUsername;

  @Value(Constants.CFG_COUCHDB_PASSWORD)
  protected String couchDbPassword;
  
  private Set<String> errorSet = new HashSet<>();
  
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void run() {
    try {
      ResponseEntity<Map> responseEntity =
          couchdbRestTemplate.exchange(getDataURL(), HttpMethod.GET,
              new HttpEntity<Map>(BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
              Map.class, Map.of(Constants.PARAM_ID, Constants.ALL_DOCS));
      log.info("[run] read response-code: {}",
          responseEntity.getStatusCode().getReasonPhrase());
      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        Map<String, Object> response = responseEntity.getBody();
        if (response.containsKey(Constants.ROWS) && response.get(Constants.ROWS) instanceof List) {
          List<Object> rows = (List<Object>) response.get(Constants.ROWS);
          rows.stream().forEach(xalanInfo -> {
            Map<String, Object> xalan = (Map<String, Object>) xalanInfo;
            String id = (String) xalan.get(Constants.PARAM_ID);
            String target = System.getenv(Constants.PARAM_TARGET);
            log.info("[run] processing id: {}, target: {}", id, target);
            if (target == null || StringUtils.equals(id, target)) {
              processRow(id);
            }
          });
        } else {
          errorSet
              .add(String.format("read all error, missing 'rows': %s", response.keySet()));
        }
      } else {
        errorSet.add(String.format("read all error, error = %s",
            responseEntity.getStatusCode().getReasonPhrase()));
      }
    } catch (Exception e) {
      log.error("run error!", e);
      errorSet.add(String.format("read all error, error = %s", e.getMessage()));
    }
  }
  
  public Set<String> getErrorSet() {
    return errorSet;
  }
}
