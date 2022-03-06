package app.rssemailsender.service;

import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import app.rssemailsender.Constants;

@Service
public class JsonApiService extends BaseService {

  private static final Logger log = LoggerFactory.getLogger(XalanService.class);

  @Value(Constants.CFG_COUCHDB_READ_JSON_API_URL)
  private String readJsonApiUrl;

  @Value(Constants.CFG_COUCHDB_UPDATE_JSON_API_URL)
  private String updateJsonApiUrl;

  @Autowired
  private RestTemplate jsonRestTemplate;

  @Autowired
  private EmailService emailService;

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void processRow(String id) {
    ResponseEntity<Map> subEntity = couchdbRestTemplate.exchange(getDataURL(), HttpMethod.GET,
        new HttpEntity<Map>(BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
        Map.class, Map.of(Constants.PARAM_ID, id));
    if (subEntity.getStatusCode().is2xxSuccessful()) {
      Map<String, Object> subResponse = subEntity.getBody();
      String url = (String) subResponse.get(Constants.PARAM_URL);
      Map<String, String> httpHeaders =
          (Map<String, String>) subResponse.get(Constants.PARAM_HTTP_HEADERS);
      Map<String, String> httpParam =
          (Map<String, String>) subResponse.get(Constants.PARAM_HTTP_PARAM);
      processJsonApi(id, url, httpHeaders, httpParam,
          (String) subResponse.get(Constants.PARAM_UNDERSCORE_REV),
          (String) subResponse.get(Constants.PARAM_MD5));
    } else {
      getErrorSet().add(String.format("read one error, id = %s, error = %s", id,
          subEntity.getStatusCode().getReasonPhrase()));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processJsonApi(String id, String url, Map<String, String> httpHeaders,
      Map<String, String> httpParam, String rev, String oldMd5) {
    log.info("[{}] url = {}, httpHeaders = {}, httpParam = {}", id, url, httpHeaders, httpParam);
    try {
      ResponseEntity<String> subEntity = jsonRestTemplate.exchange(url, HttpMethod.GET,
          new HttpEntity<Map>(BasicAuthUtil.createHeaders(httpHeaders)), String.class, httpParam);
      if (subEntity.getStatusCode().is2xxSuccessful()) {
        String respBody = subEntity.getBody();
        Map<String, Object> resultMap = new ObjectMapper().readValue(respBody, Map.class);
        ObjectMapper yamlMapper = JsonMapper.builder(new YAMLFactory())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        String resultText = "<pre>" + yamlMapper.writeValueAsString(resultMap) + "</pre>";
        String newMd5 = DigestUtils.md5Hex(resultText);
        log.info("[{}] newMd5 = {}, oldMd5 = {}", id, newMd5, oldMd5);
        if (!StringUtils.equals(oldMd5, newMd5) || StringUtils
            .equalsIgnoreCase(System.getenv(Constants.PARAM_FORCE_SEND), Boolean.TRUE.toString())) {

          emailService.sendEmail(id, resultText);

          ResponseEntity<Map> updateEntity =
              couchdbRestTemplate.exchange(updateJsonApiUrl, HttpMethod.PUT,
                  new HttpEntity<Map>(
                      Map.of(Constants.PARAM_URL, url, Constants.PARAM_HTTP_HEADERS, httpHeaders,
                          Constants.PARAM_HTTP_PARAM, httpParam, Constants.PARAM_MD5, newMd5),
                      BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
                  Map.class, Map.of(Constants.PARAM_ID, id, Constants.PARAM_REV, rev));
          if (updateEntity.getStatusCode().is2xxSuccessful()) {
            log.info("[{}] update MD5 ok", id);
          }
        } else {
          log.info("[{}] md5 pair are same, email will be skipped", id);
        }
      } else {
        log.warn("[{}] url status code is not ok, email will be skipped: {}", id,
            subEntity.getStatusCode());
      }
    } catch (Exception e) {
      log.error("[{}] processJsonApi error!", id, e);
      getErrorSet()
          .add(String.format("processJsonApi error, id = %s, msg = %s", id, e.getMessage()));
    }
  }

  protected String getDataURL() {
    return readJsonApiUrl;
  }
}
