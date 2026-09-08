package com.carlink.qr.service;

import com.carlink.common.exception.BadRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Renders QR images with ZXing. The encoded payload contains only the public
 * contact URL ({@code {baseUrl}/c/{token}}) — never a phone number, never an
 * owner identity.
 *
 * <p>Images are returned as a base64 PNG data URI so the public page can
 * display them without an extra round-trip, and no QR image is ever
 * persisted on disk.</p>
 */
@Service
public class QrImageGenerator {

    /** Quiet-zone margin in modules (ZXing requires a minimum of 4). */
    private static final int MARGIN = 4;
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final QRCodeWriter writer = new QRCodeWriter();

    /** Generates a PNG data URI of the given payload, {@code size}x{@code size} pixels. */
    public String pngDataUri(String payload, int size) {
        try {
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, size, size, hints());
            BufferedImage image = toImage(matrix, size);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException e) {
            throw new BadRequestException("QR generation failed: " + e.getMessage());
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new BadRequestException("QR generation failed: " + e.getMessage());
        }
    }

    private Map<EncodeHintType, Object> hints() {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
        hints.put(EncodeHintType.MARGIN, MARGIN);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        return hints;
    }

    private BufferedImage toImage(BitMatrix matrix, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }
}