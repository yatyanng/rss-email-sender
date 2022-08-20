package app.rssemailsender.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import app.rssemailsender.Constants;

@Service
public class JsonApiService extends BaseService {

  private static final Logger log = LoggerFactory.getLogger(XalanService.class);

  @Value(Constants.CFG_COUCHDB_READ_JSON_API_URL)
  private String readJsonApiUrl;

  @Value(Constants.CFG_COUCHDB_UPDATE_JSON_API_URL)
  private String updateJsonApiUrl;

  @Value(Constants.CFG_COUCHDB_SAVE_JSON_RESULT_URL)
  private String saveJsonResultUrl;

  @Autowired
  private RestTemplate jsonRestTemplate;

  @Autowired
  private EmailService emailService;

  @Autowired
  @Qualifier(Constants.BEAN_JSON_PATH_CONFIGURATION)
  protected com.jayway.jsonpath.Configuration jsonPathConfiguration;

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void processRow(String id) {
    ResponseEntity<Map> subEntity = couchdbRestTemplate.exchange(getDataURL(), HttpMethod.GET,
        new HttpEntity<Map>(BasicAuthUtil.createAuthHeader(couchDbUsername, couchDbPassword)),
        Map.class, Map.of(Constants.PARAM_ID, id));
    if (subEntity.getStatusCode().is2xxSuccessful()) {
      Map<String, Object> subResponse = subEntity.getBody();
      String url = (String) subResponse.get(Constants.PARAM_URL);
      Map<String, String> httpHeaders =
          (Map<String, String>) subResponse.get(Constants.PARAM_HTTP_HEADERS);
      Map<String, String> httpParam =
          (Map<String, String>) subResponse.get(Constants.PARAM_HTTP_PARAM);

      Map<String, Object> resultMap = processJsonApi(id, url, httpHeaders, httpParam,
          (String) subResponse.get(Constants.PARAM_JSON_PATH),
          (String) subResponse.get(Constants.PARAM_UNDERSCORE_REV),
          (String) subResponse.get(Constants.PARAM_MD5));

      if (resultMap != null && StringUtils.isNotBlank(saveJsonResultUrl)) {
        ResponseEntity<Map> saveEntity = couchdbRestTemplate.exchange(saveJsonResultUrl,
            HttpMethod.POST,
            new HttpEntity<Map>(
                Map.of(Constants.PARAM_UNDERSCORE_ID, UUID.randomUUID(), Constants.PARAM_GROUP, id,
                    Constants.PARAM_TIMESTAMP, Instant.now().toString(), Constants.PARAM_RESULT,
                    resultMap),
                BasicAuthUtil.createAuthHeader(couchDbUsername, couchDbPassword,
                    Map.of(Constants.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()))),
            Map.class);

        if (saveEntity.getStatusCode().is2xxSuccessful()) {
          log.info("[{}] save ok!", id);
        } else {
          log.error("[{}] save failed!", id);
        }
      }

    } else {
      getErrorSet().add(String.format("read one error, id = %s, error = %s", id,
          subEntity.getStatusCode().getReasonPhrase()));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Map<String, Object> processJsonApi(String id, String url, Map<String, String> httpHeaders,
      Map<String, String> httpParam, String jsonPath, String rev, String oldMd5) {
    log.info("[{}] url = {}, httpHeaders = {}, httpParam = {}, jsonPath = {}", id, url, httpHeaders,
        httpParam, jsonPath);
    try {
      ResponseEntity<String> subEntity = jsonRestTemplate.exchange(url, HttpMethod.GET,
          new HttpEntity<Map>(BasicAuthUtil.createHttpHeaders(httpHeaders)), String.class,
          httpParam);
      if (subEntity.getStatusCode().is2xxSuccessful()) {
        String jsonBody = subEntity.getBody();
        Map<String, Object> resultMap = new ObjectMapper().readValue(jsonBody, Map.class);

        ObjectMapper yamlMapper = JsonMapper.builder(new YAMLFactory())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        String resultText = "<pre>" + yamlMapper.writeValueAsString(resultMap) + "</pre>";

        if (jsonPath != null) {
          ReadContext readContext = JsonPath.using(jsonPathConfiguration).parse(jsonBody);
          String title = "<h1>"
              + JsonPathUtil.getValueByJsonPath(readContext, jsonPath, String.class, "") + "</h1>";
          resultText = title + resultText;
        }
        String newMd5 = DigestUtils.md5Hex(resultText);
        log.info("[{}] newMd5 = {}, oldMd5 = {}", id, newMd5, oldMd5);
        if (!StringUtils.equals(oldMd5, newMd5) || StringUtils
            .equalsIgnoreCase(System.getenv(Constants.ENV_FORCE_SEND), Boolean.TRUE.toString())) {

          if (!emailService.sendEmail(id, resultText)) {
            getErrorSet()
                .add(String.format("processJsonApi error, cannot send email, id = %s", id));
          }

          ResponseEntity<Map> updateEntity =
              couchdbRestTemplate.exchange(updateJsonApiUrl, HttpMethod.PUT,
                  new HttpEntity<Map>(
                      Map.of(Constants.PARAM_URL, url, Constants.PARAM_HTTP_HEADERS, httpHeaders,
                          Constants.PARAM_HTTP_PARAM, httpParam, Constants.PARAM_JSON_PATH,
                          jsonPath, Constants.PARAM_MD5, newMd5),
                      BasicAuthUtil.createAuthHeader(couchDbUsername, couchDbPassword)),
                  Map.class, Map.of(Constants.PARAM_ID, id, Constants.PARAM_REV, rev));
          if (updateEntity.getStatusCode().is2xxSuccessful()) {
            log.info("[{}] update MD5 ok", id);
          }
        } else {
          log.info("[{}] md5 pair are same, email will be skipped", id);
        }
        return resultMap;
      } else {
        log.warn("[{}] url status code is not ok, email will be skipped: {}", id,
            subEntity.getStatusCode());
      }
    } catch (Exception e) {
      log.error("[{}] processJsonApi error!", id, e);
      getErrorSet()
          .add(String.format("processJsonApi error, id = %s, msg = %s", id, e.getMessage()));
      publishException(e);
    }
    return null;
  }

  @Override
  protected String getDataURL() {
    return readJsonApiUrl;
  }
}
