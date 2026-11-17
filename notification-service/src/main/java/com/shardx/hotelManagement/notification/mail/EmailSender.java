package com.shardx.hotelManagement.notification.mail;

import com.shardx.hotelManagement.notification.config.NotificationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Hands an e-mail to the SMTP server. A failure is thrown (a {@code MailException}) so the message is retried. */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(EmailMessage email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.body());
        mailSender.send(message);
    }
}
