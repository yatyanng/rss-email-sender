package app.rssemailsender.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import app.rssemailsender.Constants;

@Service
public class XalanService {

  private static final Logger log = LoggerFactory.getLogger(XalanService.class);

  @Value(Constants.CFG_COUCHDB_USERNAME)
  private String couchDbUsername;

  @Value(Constants.CFG_COUCHDB_PASSWORD)
  private String couchDbPassword;

  @Value(Constants.CFG_COUCHDB_READ_XALAN_URL)
  private String readXalanUrl;

  @Value(Constants.CFG_COUCHDB_UPDATE_XALAN_URL)
  private String updateXalanUrl;

  @Autowired
  private RestTemplate couchdbRestTemplate;

  @Autowired
  private RestTemplate xmlRestTemplate;

  @Autowired
  private EmailService emailService;

  private Set<String> errorSet = new HashSet<>();

  @SuppressWarnings("rawtypes")
  private void processXalan(String id, String xslUrl, String xmlUrl, String rev, String oldMd5) {
    log.info("[{}] xslUrl = {}, xmlUrl = {}", id, xslUrl, xmlUrl);
    try {
      xmlRestTemplate.getMessageConverters().add(0,
          new StringHttpMessageConverter(StandardCharsets.UTF_8));
      String xsl = xmlRestTemplate.getForObject(xslUrl, String.class, new Object[0]);
      String xml = xmlRestTemplate.getForObject(xmlUrl, String.class, new Object[0]);

      log.info("[{}] processXalan xml: {}", id, xml);

      StreamSource xslStreamSource =
          new StreamSource(new ByteArrayInputStream(xsl.getBytes(StandardCharsets.UTF_8)));
      StreamSource xmlStreamSource =
          new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

      TransformerFactory transformerFactory =
          TransformerFactory.newInstance("org.apache.xalan.processor.TransformerFactoryImpl", null);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      StreamResult result = new StreamResult(out);

      Transformer transformer = transformerFactory.newTransformer(xslStreamSource);
      transformer.transform(xmlStreamSource, result);

      String resultText = new String(out.toByteArray());
      String newMd5 = DigestUtils.md5Hex(resultText);
      log.info("[{}] newMd5 = {}, oldMd5 = {}", id, newMd5, oldMd5);
      if (!StringUtils.equals(oldMd5, newMd5) || StringUtils
          .equalsIgnoreCase(System.getenv(Constants.PARAM_FORCE_SEND), Boolean.TRUE.toString())) {
        emailService.sendEmail(id, resultText);

        ResponseEntity<Map> updateEntity =
            couchdbRestTemplate.exchange(updateXalanUrl, HttpMethod.PUT,
                new HttpEntity<Map>(
                    Map.of(Constants.PARAM_XML_URL, xmlUrl, Constants.PARAM_XSL_URL, xslUrl,
                        Constants.PARAM_MD5, newMd5),
                    BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
                Map.class, Map.of(Constants.PARAM_ID, id, Constants.PARAM_REV, rev));
        if (updateEntity.getStatusCode().is2xxSuccessful()) {
          log.info("[{}] update MD5 ok", id);
        }
      } else {
        log.info("[{}] md5 pair are same, email will be skipped", id);
      }
    } catch (Exception e) {
      log.error("[{}] processXalan error!", id, e);
      errorSet.add(String.format("processXalan error, id = %s, msg = %s", id, e.getMessage()));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void run() {
    try {
      ResponseEntity<Map> responseEntity =
          couchdbRestTemplate.exchange(readXalanUrl, HttpMethod.GET,
              new HttpEntity<Map>(BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
              Map.class, Map.of(Constants.PARAM_ID, Constants.ALL_DOCS));
      log.info("[run] read all-xalan-objects response-code: {}",
          responseEntity.getStatusCode().getReasonPhrase());
      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        Map<String, Object> response = responseEntity.getBody();
        if (response.containsKey(Constants.ROWS) && response.get(Constants.ROWS) instanceof List) {
          List<Object> rows = (List<Object>) response.get(Constants.ROWS);
          rows.stream().forEach(xalanInfo -> {
            Map<String, Object> xalan = (Map<String, Object>) xalanInfo;
            String id = (String) xalan.get(Constants.PARAM_ID);
            String target = System.getenv(Constants.PARAM_TARGET);
            log.info("[run] processing xalan id: {}, target: {}", id, target);
            if (target == null || StringUtils.equals(id, target)) {
              ResponseEntity<Map> subEntity =
                  couchdbRestTemplate.exchange(readXalanUrl, HttpMethod.GET,
                      new HttpEntity<Map>(
                          BasicAuthUtil.createHeaders(couchDbUsername, couchDbPassword)),
                      Map.class, Map.of(Constants.PARAM_ID, id));
              if (subEntity.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> subResponse = subEntity.getBody();
                String xmlUrl = (String) subResponse.get(Constants.PARAM_XML_URL);
                String xslUrl = (String) subResponse.get(Constants.PARAM_XSL_URL);
                processXalan(id, xslUrl, xmlUrl,
                    (String) subResponse.get(Constants.PARAM_UNDERSCORE_REV),
                    (String) subResponse.get(Constants.PARAM_MD5));
              } else {
                errorSet.add(String.format("read one xalan error, id = %s, error = %s", id,
                    subEntity.getStatusCode().getReasonPhrase()));
              }
            }

          });
        } else {
          errorSet
              .add(String.format("read all xalans error, missing 'rows': %s", response.keySet()));
        }
      } else {
        errorSet.add(String.format("read all xalans error, error = %s",
            responseEntity.getStatusCode().getReasonPhrase()));
      }
    } catch (Exception e) {
      log.error("run error!", e);
      errorSet.add(String.format("read all xalans error, error = %s", e.getMessage()));
    }
  }

  public Set<String> getErrorSet() {
    return errorSet;
  }
}
