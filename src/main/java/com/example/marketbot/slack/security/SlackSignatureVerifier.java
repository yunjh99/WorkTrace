package com.example.marketbot.slack.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Slack이 보낸 timestamp와 원본 요청 본문으로 HMAC-SHA256 서명을 검증합니다.
 */
@Component
public class SlackSignatureVerifier {

    private static final String SIGNATURE_VERSION = "v0";
    private static final long MAX_REQUEST_AGE_SECONDS = 5 * 60;

    private final String signingSecret;

    public SlackSignatureVerifier(@Value("${slack.signing-secret:}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public boolean isValid(String timestamp, String receivedSignature, byte[] rawBody) {
        if (signingSecret.isBlank()
                || timestamp == null
                || receivedSignature == null
                || rawBody == null) {
            return false;
        }

        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }

        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - requestTimestamp);
        if (ageSeconds > MAX_REQUEST_AGE_SECONDS) {
            return false;
        }

        String baseString = SIGNATURE_VERSION + ":" + timestamp + ":"
                + new String(rawBody, StandardCharsets.UTF_8);
        String expectedSignature = SIGNATURE_VERSION + "=" + hmacSha256(baseString);

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Slack 요청 서명을 계산할 수 없습니다.", exception);
        }
    }
}
