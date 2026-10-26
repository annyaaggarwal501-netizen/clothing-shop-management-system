package com.vertex.csms.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vertex.csms.common.ApiException;

/**
 * Signed e-bill links like /ebill.html?t=154.9f2c1a7b3e0d44aa. The signature (HMAC-SHA256 with a secret
 * only the server knows) means nobody can open other bills by changing the number.
 */
@Service
public class BillLinkService {

    private final byte[] secret;

    public BillLinkService(@Value("${csms.link-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String token(long saleId) {
        return saleId + "." + sign(saleId);
    }

    public long saleId(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot < 1) {
            throw invalid();
        }
        long id;
        try {
            id = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            throw invalid();
        }
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, sign(id).getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
        return id;
    }

    private String sign(long saleId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal(("bill:" + saleId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return ApiException.notFound("ERR-RET-002", "This bill link is not valid.");
    }
}
