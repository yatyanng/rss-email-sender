package app.rssemailsender.service;

import org.apache.commons.lang3.ObjectUtils;
import com.jayway.jsonpath.ReadContext;

public class JsonPathUtil {

  public static <T> T getValueByJsonPath(ReadContext readContext, String jsonPath, Class<T> clazz,
      T defaultValue) {
    return ObjectUtils.defaultIfNull(readContext.read(jsonPath, clazz), defaultValue);
  }

  private JsonPathUtil() {}
}
