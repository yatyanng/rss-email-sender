package app.rssemailsender.service;

import java.util.HashSet;
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
import app.rssemailsender.Constants;

@Service
public class JsoupService extends BaseService {

  private static final Logger log = LoggerFactory.getLogger(JsoupService.class);

  @Value(Constants.CFG_COUCHDB_READ_JSOUP_URL)
  private String readJsoupUrl;

  @Value(Constants.CFG_COUCHDB_UPDATE_JSOUP_URL)
  private String updateJsoupUrl;

  @Autowired
  private EmailService emailService;

  private Set<String> errorSet = new HashSet<>();

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void processRow(String id) {
    ResponseEntity<Map> subEntity = couchdbRestTemplate.exchange(getDataURL(), HttpMethod.GET,
        new HttpEntity<Map>(BasicAuthUtil.createAuthHeader(couchDbUsername, couchDbPassword)),
        Map.class, Map.of(Constants.PARAM_ID, id));
    if (subEntity.getStatusCode().is2xxSuccessful()) {
      Map<String, Object> subResponse = subEntity.getBody();
      String url = (String) subResponse.get(Constants.PARAM_URL);
      String xpath = (String) subResponse.get(Constants.PARAM_XPATH);
      processJsoup(id, url, xpath, (String) subResponse.get(Constants.PARAM_UNDERSCORE_REV),
          (String) subResponse.get(Constants.PARAM_MD5));
    } else {
      getErrorSet().add(String.format("read one error, id = %s, error = %s", id,
          subEntity.getStatusCode().getReasonPhrase()));
    }
  }

  @SuppressWarnings("rawtypes")
  private void processJsoup(String id, String url, String xpath, String rev, String oldMd5) {
    log.info("[{}] xpath = {}, rev = {}", id, xpath, rev);
    try {
      Document doc = Jsoup.connect(url).sslSocketFactory(BasicAuthUtil.socketFactory()).get();
      Elements elements = doc.selectXpath(xpath);
      for (Element element : elements) {
        String resultText = element.text();
        String newMd5 = DigestUtils.md5Hex(resultText);
        log.info("[{}] newMd5 = {}, oldMd5 = {}", id, newMd5, oldMd5);
        if (!StringUtils.equals(oldMd5, newMd5) || StringUtils
            .equalsIgnoreCase(System.getenv(Constants.ENV_FORCE_SEND), Boolean.TRUE.toString())) {

          emailService.sendEmail(id, resultText);

          ResponseEntity<Map> updateEntity =
              couchdbRestTemplate.exchange(updateJsoupUrl, HttpMethod.PUT,
                  new HttpEntity<Map>(
                      Map.of(Constants.PARAM_URL, url, Constants.PARAM_XPATH, xpath,
                          Constants.PARAM_MD5, newMd5),
                      BasicAuthUtil.createAuthHeader(couchDbUsername, couchDbPassword)),
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
      log.error("[{}] processJsoup error!", id, e);
      errorSet.add(String.format("processJsoup error, id = %s, msg = %s", id, e.getMessage()));
    }
  }

  protected String getDataURL() {
    return readJsoupUrl;
  }
}
