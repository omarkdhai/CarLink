package com.carlink.qr.dto;

import com.carlink.qr.model.QrCode;

/**
 * Response for the generation/re-generation action. Contains the raw public
 * token exactly once — the only moment the owner can see it. The token is
 * not returned again by any subsequent endpoint.
 *
 * @param rawToken the public token embedded in the QR (shown once, keep safe)
 * @param publicUrl the URL the QR encodes: {@code {publicUrl}/c/{token}}
 * @param imageDataUri base64 PNG of the QR, ready for the page
 * @param qr the persisted QR record (without token hash)
 */
public record QrIssuedResponse(
        String rawToken,
        String publicUrl,
        String imageDataUri,
        QrStatusResponse qr
) {

    public static QrIssuedResponse of(String rawToken, String publicUrl,
                                      String imageDataUri, QrCode qr) {
        return new QrIssuedResponse(
                rawToken, publicUrl, imageDataUri, QrStatusResponse.from(qr));
    }
}