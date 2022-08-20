package app.rssemailsender.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.HttpHeaders;

public class BasicAuthUtil {

  public static SSLSocketFactory socketFactory() {
    TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
      @Override
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }

      @Override
      public void checkClientTrusted(X509Certificate[] arg0, String arg1)
          throws CertificateException {}

      @Override
      public void checkServerTrusted(X509Certificate[] arg0, String arg1)
          throws CertificateException {}
    }};

    try {
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      SSLSocketFactory result = sslContext.getSocketFactory();
      return result;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create a SSL socket factory", e);
    }
  }

  @SuppressWarnings("serial")
  public static HttpHeaders createHttpHeaders(Map<String, String> httpHeaders) {
    return new HttpHeaders() {
      {
        httpHeaders.entrySet().stream().forEach(entry -> {
          set(entry.getKey(), entry.getValue());
        });
      }
    };
  }

  @SuppressWarnings("serial")
  public static HttpHeaders createAuthHeader(String username, String password) {
    return new HttpHeaders() {
      {
        String auth = username + ":" + password;
        byte[] encodedAuth = org.apache.commons.codec.binary.Base64
            .encodeBase64(auth.getBytes(java.nio.charset.Charset.forName("US-ASCII")));
        String authHeader = "Basic " + new String(encodedAuth);
        set("Authorization", authHeader);
      }
    };
  }

  @SuppressWarnings("serial")
  public static HttpHeaders createAuthHeader(String username, String password,
      Map<String, String> httpHeaders) {
    return new HttpHeaders() {
      {
        String auth = username + ":" + password;
        byte[] encodedAuth = org.apache.commons.codec.binary.Base64
            .encodeBase64(auth.getBytes(java.nio.charset.Charset.forName("US-ASCII")));
        String authHeader = "Basic " + new String(encodedAuth);
        set("Authorization", authHeader);
        httpHeaders.entrySet().stream().forEach(entry -> {
          set(entry.getKey(), entry.getValue());
        });
      }
    };
  }

  private BasicAuthUtil() {}
}
