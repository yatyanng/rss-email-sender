package app.rssemailsender.service;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import app.rssemailsender.Constants;

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

  public void sendEmail(String subject, String content) {
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
      } else {
        log.warn("[sendEmail] missing either from-field, to-field or host address!");
      }
    } catch (Exception e) {
      log.error("sendEmail error!", e);
    }
  }
}
