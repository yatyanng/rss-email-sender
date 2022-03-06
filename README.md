# rss-email-sender
An app which reads xml (mainly rss) from internet, converts it to html by xsl and sends it out by email

This app uses couchdb for storage. It is chosen for flexibility. More fields can be easily added in the future.
It also uses apache james for email and other open source libs like springboot, apache xalan and jsoup.
