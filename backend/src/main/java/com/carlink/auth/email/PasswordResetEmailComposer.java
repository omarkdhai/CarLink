package com.carlink.auth.email;

import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailComposer {
    public record EmailContent(String subject, String html, String text) {}

    public EmailContent compose(String firstName, String resetUrl, int expiryMinutes) {
        String subject = "Reset your password";
        String preheader = "Click the link to reset your password. Expires in " + expiryMinutes + " minutes.";
        String html = htmlTemplate()
                .replace("{{PREHEADER}}", escape(preheader))
                .replace("{{NAME}}", escape(firstName))
                .replace("{{URL}}", escape(resetUrl))
                .replace("{{EXPIRY}}", String.valueOf(expiryMinutes))
                .replace("{{TITLE}}", escape(subject));
        String text = "Hello " + firstName + ",\n\n"
                + "Click this link to reset your password:\n"
                + resetUrl + "\n\nThis link expires in " + expiryMinutes + " minutes.";
        return new EmailContent(subject, html, text);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String htmlTemplate() {
        return """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>{{TITLE}}</title>
                  <style>
                    @import url('https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400..800&display=swap');
                    body{margin:0;padding:0;width:100%!important;background:#f1f5f9;font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;color:#0f172a}
                  </style>
                </head>
                <body>
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f1f5f9;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;overflow:hidden;">
                        <tr><td style="background:#2563eb;padding:22px 32px;">
                          <span style="font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:22px;font-weight:800;color:#ffffff;">Car<span style="color:#bfdbfe;">Link</span></span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h1 style="margin:0 0 12px;font-size:24px;font-weight:800;color:#0f172a;">Reset your password</h1>
                          <p style="margin:0 0 20px;font-size:16px;line-height:26px;color:#475569;">Hello {{NAME}}, click the button below to reset your password.</p>
                          <p style="margin:0 0 24px;"><a href="{{URL}}" style="display:inline-block;padding:12px 24px;background:#2563eb;color:#ffffff;font-weight:700;border-radius:12px;text-decoration:none;">Reset my password</a></p>
                          <p style="margin:0;font-size:13px;color:#64748b;">This link expires in {{EXPIRY}} minutes. If you didn't request this reset, ignore this email.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """;
    }
}
