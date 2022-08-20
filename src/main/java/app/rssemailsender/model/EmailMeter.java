package app.rssemailsender.model;

public class EmailMeter {

  private String subject;
  private String emailSentBy;
  private String emailSentTime;

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getEmailSentBy() {
    return emailSentBy;
  }

  public void setEmailSentBy(String emailSentBy) {
    this.emailSentBy = emailSentBy;
  }

  public String getEmailSentTime() {
    return emailSentTime;
  }

  public void setEmailSentTime(String emailSentTime) {
    this.emailSentTime = emailSentTime;
  }

}
