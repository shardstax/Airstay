package com.shardx.hotelManagement.notification.mail;

/** A plain-text e-mail, ready to send. */
public record EmailMessage(String to, String subject, String body) {
}
