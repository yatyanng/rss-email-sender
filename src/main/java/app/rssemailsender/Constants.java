package app.rssemailsender;

public class Constants {

  public static final String CFG_EMAIL_PASSWORD = "${app.email.password}";
  public static final String CFG_EMAIL_FROM_ADDRESS = "${app.email.from-address}";
  public static final String CFG_EMAIL_TO_ADDRESS = "${app.email.to-address}";

  public static final String CFG_COUCHDB_USERNAME = "${app.couchdb.username}";
  public static final String CFG_COUCHDB_PASSWORD = "${app.couchdb.password}";
  
  public static final String CFG_COUCHDB_READ_JSOUP_URL = "${app.couchdb.read-jsoup-url}";
  public static final String CFG_COUCHDB_READ_XALAN_URL = "${app.couchdb.read-xalan-url}";
  
  public static final String CFG_COUCHDB_UPDATE_JSOUP_URL = "${app.couchdb.update-jsoup-url}";
  public static final String CFG_COUCHDB_UPDATE_XALAN_URL = "${app.couchdb.update-xalan-url}";
  
  public static final String CFG_COUCHDB_CONNECTION_TIMEOUT = "${app.couchdb.connect-timeout}";
  public static final String CFG_COUCHDB_READ_TIMEOUT = "${app.couchdb.read-timeout}";
  public static final String CFG_COUCHDB_CONNECTION_POOL_SIZE =
      "${app.couchdb.connection-pool-size}";

  public static final String CONST_UTF8 = "UTF-8";
  public static final int MILLISECONDS_IN_ONE_SECOND = 1000;
  public static final String ALL_DOCS = "_all_docs";
  public static final String ROWS = "rows";

  public static final String PARAM_TARGET = "target";
  public static final String PARAM_FORCE_SEND = "forceSend";
  public static final String PARAM_TIMEOUT = "timeout";
  public static final String PARAM_ID = "id";
  public static final String PARAM_URL = "url";
  public static final String PARAM_XPATH = "xpath";
  public static final String PARAM_TEXT = "text";
  public static final String PARAM_UNDERSCORE_REV = "_rev";
  public static final String PARAM_REV = "rev";
  public static final String PARAM_MD5 = "md5";
  
  public static final String PARAM_XML_URL = "xml_url";
  public static final String PARAM_XSL_URL = "xsl_url";

  public static final String BEAN_COUCHDB_REST_TEMPLATE = "couchdbRestTemplate";
  public static final String BEAN_COUCHDB_REST_CLIENT = "couchdbRestClient";
  public static final String BEAN_JAVAMAIL_PROPERTIES = "javaMailProperties";

  private Constants() {}
}
