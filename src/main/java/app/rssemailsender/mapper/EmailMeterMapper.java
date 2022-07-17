package app.rssemailsender.mapper;

import org.apache.ibatis.annotations.Insert;
import app.rssemailsender.model.EmailCounter;

public interface EmailMeterMapper {
  
  @Insert("INSERT INTO email_meter(subject, email_sent_by, email_sent_time) "
      + "VALUES (#{subject}, #{emailSentBy}, str_to_date(#{emailSentTime},'%Y-%m-%d %H:%i'))")
  int insert(EmailCounter emailCounter);

}
