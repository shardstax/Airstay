package com.shardx.hotelManagement.notification.mail;

import com.shardx.hotelManagement.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSenderTest {

    @Test
    void anInvalidRecipientAddressFailsAsAMailParseExceptionWhichIsNotRetried() {
        // Parsing the address happens before any connection, so no mail server is needed. MailParseException is on the
        // listener's exclude list: retrying cannot make an invalid address valid.
        EmailSender sender = new EmailSender(new JavaMailSenderImpl(),
                new NotificationProperties("AirStay <no-reply@airstay.local>", Duration.ofDays(7)));

        assertThatThrownBy(() -> sender.send(new EmailMessage("@@", "subject", "body")))
                .isInstanceOf(MailParseException.class);
    }
}
