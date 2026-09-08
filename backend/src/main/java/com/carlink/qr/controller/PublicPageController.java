package com.carlink.qr.controller;

import com.carlink.common.exception.NotFoundException;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.qr.service.PublicQrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile-first public page rendered at {@code /c/{token}} — the exact URL a
 * scanned QR encodes. The page shows only a safe vehicle summary and contact
 * channels; it never contains a phone number and never leaks the license
 * plate. The raw token is used only for the hash lookup and rate-limit key.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public page")
public class PublicPageController {

    private final PublicQrService publicQrService;

    @GetMapping(value = "/c/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Mobile-first public contact page behind a scanned QR")
    public ResponseEntity<String> page(@PathVariable String token,
                                       HttpServletRequest request) {
        try {
            QrPublicView view = publicQrService.resolve(token, request.getRemoteAddr());
            String vehicleLabel = vehicleLabel(view.vehicle());
            String page = PAGE_TEMPLATE.replace("@VEHICLE@", escaped(vehicleLabel));
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(NOT_FOUND_PAGE);
        }
    }

    private String vehicleLabel(QrPublicView.VehicleSafe v) {
        String nickname = v.nickname();
        String brandModel = joinNonEmpty(joinNonEmpty(v.brand(), v.model(), " "), v.color(), ", ");
        if (nickname == null || nickname.isBlank()) {
            return brandModel.isBlank() ? "a vehicle" : brandModel;
        }
        if (brandModel.isBlank()) {
            return nickname;
        }
        return nickname + " — " + brandModel;
    }

    private String joinNonEmpty(String a, String b, String separator) {
        if (a == null || a.isBlank()) return b == null ? "" : b;
        if (b == null || b.isBlank()) return a;
        return a + separator + b;
    }

    /** Minimal HTML escaping so a nickname cannot inject markup into the page. */
    private String escaped(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final String CSS = """
            body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
                 background:#f1f5f9;color:#0f172a;display:flex;align-items:center;
                 justify-content:center;min-height:100vh;padding:24px;box-sizing:border-box}
            .card{background:#fff;border-radius:16px;padding:32px 24px;max-width:400px;
                  width:100%;box-shadow:0 10px 30px rgba(2,6,23,.08);text-align:center}
            h1{font-size:20px;margin:0 0 8px}
            .vehicle{color:#475569;font-size:15px;margin:0 0 24px}
            textarea{width:100%;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:12px;
                     padding:14px;font-size:15px;resize:vertical;min-height:96px;margin-bottom:12px}
            textarea:focus{outline:2px solid #2563eb;border-color:transparent}
            .channels{display:flex;gap:10px;margin-bottom:16px}
            button{flex:1;border:none;border-radius:12px;padding:14px 0;font-size:15px;
                   font-weight:600;cursor:pointer;color:#fff;transition:filter .15s}
            button:active{filter:brightness(.92)}
            .whatsapp{background:#25D366}.sms{background:#2563eb}
            .send{width:100%;background:#0f172a}
            .sel{border:3px solid gold !important;box-shadow:0 0 0 2px gold}
            .status{margin-top:14px;font-size:14px;min-height:20px}
            .ok{color:#16a34a}.err{color:#dc2626}.dim{color:#64748b}
            a{color:#2563eb;text-decoration:none}
            """;

    private static final String NOT_FOUND_PAGE = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Link not active</title>
            <style>%s</style></head>
            <body><main class="card">
              <h1>This QR link is not active</h1>
              <p>The code may have been deactivated by the owner. If you think
              this is a mistake, please try again later.</p>
            </main></body></html>
            """.formatted(CSS);

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="description" content="Contact this vehicle's owner">
            <title>Contact owner</title><style>%s</style></head>
            <body><main class="card">
              <h1>Contact this owner</h1>
              <p class="vehicle">@VEHICLE@</p>
              <form id="f" autocomplete="off">
                <textarea id="msg" maxlength="500" required
                  placeholder="Leave a short message (max 500 characters)..."></textarea>
                <div class="channels">
                  <button type="button" id="wa" class="whatsapp">WhatsApp</button>
                  <button type="button" id="sms" class="sms">SMS</button>
                </div>
                <button type="submit" class="send">Send message</button>
                <p class="status dim" id="st" role="status"></p>
              </form>
            </main>
            <script>
            // The token is the last path segment the visitor scanned; never embed it.
            const token=decodeURIComponent(location.pathname.split('/').filter(Boolean).pop());
            let channel="WHATSAPP";
            const setCh=c=>{channel=c;document.getElementById("wa").classList.toggle("sel",c==="WHATSAPP");
              document.getElementById("sms").classList.toggle("sel",c==="SMS");};
            document.getElementById("wa").onclick=()=>setCh("WHATSAPP");
            document.getElementById("sms").onclick=()=>setCh("SMS");
            setCh(channel);
            document.getElementById("f").onsubmit=async e=>{
              e.preventDefault();
              const st=document.getElementById("st");
              const msg=document.getElementById("msg").value.trim();
              if(!msg){st.className="status err";st.textContent="Please write a message.";return;}
              st.className="status dim";st.textContent="Sending…";
              try{
                const r=await fetch("/api/v1/public/qr/"+encodeURIComponent(token)+"/contact",{
                  method:"POST",headers:{"Content-Type":"application/json"},
                  body:JSON.stringify({channel,message:msg})});
                if(r.status===429){const wait=r.headers.get("Retry-After")||"60";
                  st.className="status err";
                  st.textContent="Too many requests. Please wait "+wait+" seconds.";return;}
                if(!r.ok){st.className="status err";st.textContent="This link is no longer active.";return;}
                st.className="status ok";st.textContent="Your message was sent to the owner. Thank you!";
                document.getElementById("msg").value="";
              }catch(_){st.className="status err";st.textContent="Could not reach the server. Try again.";}
            };
            </script>
            </body></html>
            """.formatted(CSS);
}