package app.rssemailsender.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import app.rssemailsender.Constants;

@Service
public class JsoupService {

  private static final Logger log = LoggerFactory.getLogger(JsoupService.class);

  @Value(Constants.CFG_COUCHDB_USERNAME)
  private String couchDbUsername;

  @Value(Constants.CFG_COUCHDB_PASSWORD)
  private String couchDbPassword;

  @Value(Constants.CFG_COUCHDB_READ_JSOUP_URL)
  private String readJsoupUrl;

  @Value(Constants.CFG_COUCHDB_UPDATE_JSOUP_URL)
  private String updateJsoupUrl;

  @Autowired
  private RestTemplate couchdbRestTemplate;

  @Autowired
  private EmailService emailService;

  private Set<String> errorSet = new HashSet<>();

  @SuppressWarnings("rawtypes")
  private void processXpath(String id, String url, String xpath, String rev, String oldMd5) {
    log.info("[{}] xpath = {}, rev = {}", id, xpath, rev);
    try {
      Document doc = Jsoup.connect(url).sslSocketFactory(BasicAuthUtil.socketFactory()).get();
      Elements elements = doc.selectXpath(xpath);
      for (Element element : elements) {
        String resultText = element.text();
        String newMd5 = DigestUtils.md5Hex(resultText);
        log.info("[{}] newMd5 = {}, oldMd5 = {}", id, newMd5, oldMd5);
        if (!StringUtils.equals(oldMd5, newMd5) || StringUtils
            .equalsIgnoreCase(System.getenv(Constants.PARAM_FORCE_SEND), Boolean.TRUE.toString())) {
          emailService.sendEmail(id, resultText);
          ResponseEntity<Map> updateEntity =
              couchdbRestTemplate.exchange(updateJsoupUrl, HttpMethod.PUT,
                  new HttpEntity<Map>(
                      Map.of(Constants.PARAM_URL, url, Constants.PARAM_XPATH, xpath,
                          Constants.PARAM_MD5, newMd5),
                      BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
                  Map.class, Map.of(Constants.PARAM_ID, id, Constants.PARAM_REV, rev));
          if (updateEntity.getStatusCode().is2xxSuccessful()) {
            log.info("[{}] update MD5 ok", id);
          }
        } else {
          log.info("[{}] md5 pair are same, email will be skipped", id);
        }
        break;
      }
    } catch (Exception e) {
      log.error("[{}] processXpath error!", id, e);
      errorSet.add(String.format("processXpath error, id = %s, msg = %s", id, e.getMessage()));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void run() {
    try {
      ResponseEntity<Map> responseEntity =
          couchdbRestTemplate.exchange(readJsoupUrl, HttpMethod.GET,
              new HttpEntity<Map>(BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
              Map.class, Map.of(Constants.PARAM_ID, Constants.ALL_DOCS));
      log.info("[run] read all-jsoup-objects response-code: {}",
          responseEntity.getStatusCode().getReasonPhrase());
      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        Map<String, Object> response = responseEntity.getBody();
        if (response.containsKey(Constants.ROWS) && response.get(Constants.ROWS) instanceof List) {
          List<Object> rows = (List<Object>) response.get(Constants.ROWS);
          rows.stream().forEach(jsoupInfo -> {
            Map<String, Object> jsoup = (Map<String, Object>) jsoupInfo;
            String id = (String) jsoup.get(Constants.PARAM_ID);
            String target = System.getenv(Constants.PARAM_TARGET);
            log.info("[run] processing jsoup id: {}, target: {}", id, target);
            if (target == null || StringUtils.equals(id, target)) {
              ResponseEntity<Map> subEntity =
                  couchdbRestTemplate.exchange(readJsoupUrl, HttpMethod.GET,
                      new HttpEntity<Map>(
                          BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
                      Map.class, Map.of(Constants.PARAM_ID, id));
              if (subEntity.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> subResponse = subEntity.getBody();
                String url = (String) subResponse.get(Constants.PARAM_URL);
                processXpath(id, url, (String) subResponse.get(Constants.PARAM_XPATH),
                    (String) subResponse.get(Constants.PARAM_UNDERSCORE_REV),
                    (String) subResponse.get(Constants.PARAM_MD5));
              } else {
                errorSet.add(String.format("read one jsoup error, id = %s, error = %s", id,
                    subEntity.getStatusCode().getReasonPhrase()));
              }
            }
          });
        } else {
          errorSet
              .add(String.format("read all jsoups error, missing 'rows': %s", response.keySet()));
        }
      } else {
        errorSet.add(String.format("read all jsoups error, error = %s",
            responseEntity.getStatusCode().getReasonPhrase()));
      }
    } catch (Exception e) {
      log.error("run error!", e);
      errorSet.add(String.format("read all jsoups error, error = %s", e.getMessage()));
    }
  }

  public Set<String> getErrorSet() {
    return errorSet;
  }
}
