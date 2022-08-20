package app.rssemailsender.service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import app.rssemailsender.Constants;
import app.rssemailsender.mapper.EmailMeterMapper;
import app.rssemailsender.model.EmailMeter;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  @Autowired
  private Properties javaMailProperties;

  @Value(Constants.CFG_EMAIL_PASSWORD)
  private String smtpPassword;

  @Value(Constants.CFG_EMAIL_FROM_ADDRESS)
  private String fromAddress;

  @Value(Constants.CFG_EMAIL_TO_ADDRESS)
  private String[] toAddresses;

  @Autowired
  @Qualifier(Constants.BEAN_MYSQL_SESSION_FACTORY)
  protected SqlSessionFactory mysqlSessionFactory;

  @Autowired
  protected KafkaPublisher exceptionPublisher;

  public boolean sendEmail(String subject, String content) {
    try {
      log.debug("[sendEmail] subject: {}, content: {}", subject, content);

      String emailHost = javaMailProperties.getProperty("mail.smtp.host");
      log.debug("[sendEmail] {} -> {}, mail.smtp.host: {}", fromAddress, List.of(toAddresses),
          emailHost);
      if (StringUtils.isNotBlank(emailHost) && StringUtils.isNotBlank(fromAddress)
          && toAddresses.length > 0) {
        log.debug("[sendEmail] starting to send message from: {}", fromAddress);
        Session session = Session.getInstance(javaMailProperties);

        MimeMessage message = new MimeMessage(session);
        message.setContent(content, "text/html; charset=utf-8");
        message.setFrom(new InternetAddress(fromAddress));

        Arrays.asList(toAddresses).forEach(toAddress -> {
          try {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
          } catch (Exception e) {
            log.error("sendEmail error!", e);
          }
        });
        message.setSubject(subject);

        Transport transport = session.getTransport("smtp");
        transport.connect(emailHost, fromAddress, smtpPassword);
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
        log.debug("sendEmail to: {}", Arrays.asList(toAddresses));

        return insertEmailMeter(subject);
      } else {
        log.warn("[sendEmail] missing either from-field, to-field or host address!");
      }
    } catch (Exception e) {
      log.error("sendEmail error!", e);
      exceptionPublisher.publishException(e);
    }
    return false;
  }

  private boolean insertEmailMeter(String subject) {
    try (SqlSession sqlSession = mysqlSessionFactory.openSession()) {
      EmailMeterMapper emailMeterMapper = sqlSession.getMapper(EmailMeterMapper.class);
      EmailMeter emailMeter = new EmailMeter();
      emailMeter.setEmailSentBy(fromAddress);
      emailMeter.setSubject(subject);
      emailMeter.setEmailSentTime(
          new SimpleDateFormat("yyyy-MM-dd HH:mm").format(System.currentTimeMillis()));
      emailMeterMapper.insert(emailMeter);
      sqlSession.commit();
      return true;
    } catch (Exception e) {
      log.error("insertEmailMeter error!", e);
      exceptionPublisher.publishException(e);
      return false;
    }
  }
}
