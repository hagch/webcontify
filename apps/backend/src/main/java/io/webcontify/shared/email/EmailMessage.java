package io.webcontify.shared.email;

public record EmailMessage(
        String to,
        String subject,
        String body,
        String fromAddress,
        String fromName) {}
