# WhatsApp alerts (Meta WhatsApp Cloud API)

Every alert goes to the owner by **SMS** (TunisieSMS) and, when this is set up,
also to their **WhatsApp**. The visitor doesn't choose. The owner can turn
WhatsApp off in their profile ("Also send my alerts on WhatsApp", on by default).

The alert counts as delivered as soon as **one** channel reaches the owner. The
visitor sees "could not be delivered" only when both SMS and WhatsApp fail.

## How it works

- CarLink never receives a message from the owner first, so WhatsApp only lets
  it send an **approved template**. The template is `carlink_alert`, with two
  variables:
  - `{{1}}` is the reason line in Derja, the same as the SMS (e.g. `🚧 كرهبتك سادّة عليّ`).
  - `{{2}}` is the visitor's note exactly as typed, flattened to one line. When
    there is no note it is `—`, because WhatsApp refuses empty variables.
    Unlike the SMS, the note is **not** cut to 66 characters.
- The send (`MetaWhatsAppSender`) only says "accepted". Delivered, read or
  failed arrives later on the **webhook** `POST /api/v1/public/webhooks/whatsapp`.
  CarLink reads a webhook call only when it is signed with the app secret
  (`X-Hub-Signature-256`).
- Each channel is stored in `conversation_deliveries`. The owner's conversation
  page shows one badge per channel, e.g. "SMS · Delivered" and "WhatsApp · Read".
- Failure codes are `WA_<Meta code>`. The common ones:

  | Code | Meaning |
  |---|---|
  | `WA_131026` | The number is not on WhatsApp, or the message can't be delivered. |
  | `WA_131030` | Test number: the recipient isn't in the allowed list. |
  | `WA_132001` | The template doesn't exist or isn't approved in this language. |
  | `WA_190` | The token has expired. |
  | `WA_130429` / `WA_131056` | Rate limits. |

## Setup (one time)

1. **Meta Business account.** Go to business.facebook.com and create the
   business ("CarLink").
2. **Developer app.** At developers.facebook.com → My Apps → Create app, choose
   type **Business**, then add the **WhatsApp** product. Meta gives you a free
   **test number**, which can message up to 5 numbers you add as recipients.
   That's enough to try everything.
3. **Real sender number** (for production). In WhatsApp Manager, go to Phone
   numbers → Add. Use a number that isn't already registered on the WhatsApp
   app; a new SIM works. The display name is "CarLink", and Meta reviews it.
4. **Permanent token.** In Business settings → Users → **System users**, add an
   admin system user. Assign it the app and the WhatsApp account (full control),
   then **Generate token** with `whatsapp_business_messaging` and
   `whatsapp_business_management`, and no expiry.
5. **IDs and secrets** for `.env`:
   - `WHATSAPP_PHONE_NUMBER_ID`: WhatsApp → API setup, "Phone number ID" (not the phone number).
   - `WHATSAPP_ACCESS_TOKEN`: the token from step 4.
   - `WHATSAPP_APP_SECRET`: App settings → Basic → App secret.
   - `WHATSAPP_VERIFY_TOKEN`: any long random string you choose, e.g. `openssl rand -hex 24`.
6. **Template.** In WhatsApp Manager → Message templates → Create:
   - **Category:** Utility. **Name:** `carlink_alert`. **Language:** Arabic (`ar`).
   - **Body:**
     ```
     🚗 تنبيه CarLink
     {{1}}
     💬 {{2}}

     التنبيه موجود زادة في حسابك على CarLink.
     ```
     Meta refuses a body that starts or ends with a variable, hence the last line.
     It can also be created by API: `POST /v25.0/<WABA_ID>/message_templates`.
   - **Variable samples:** `{{1}}` = `🚧 كرهبتك سادّة عليّ`, `{{2}}` = `نستناك قدام الكرهبة`.

   Submit it. Approval takes from a few minutes to 24 hours. If you rename the
   template or change its language, set `WHATSAPP_TEMPLATE_NAME` /
   `WHATSAPP_TEMPLATE_LANG` to match.
7. **Webhook** (needs the public HTTPS domain). In the app → WhatsApp →
   Configuration → Webhook:
   - **Callback URL:** `https://<your-domain>/api/v1/public/webhooks/whatsapp`
   - **Verify token:** the `WHATSAPP_VERIFY_TOKEN` value.
   - Click **Verify and save**. The backend must already run with
     `WHATSAPP_PROVIDER=meta`.
   - Then subscribe to the **messages** field.
8. **Turn it on:** set `WHATSAPP_PROVIDER=meta` in `.env`, then restart the backend.
   The backend refuses to start if one of the values above is missing.
9. **Business verification** (Business settings → Security center). Without it
   you can message about 250 different people per 24 hours, which is enough for
   the pilot. Verification raises the limit.

Without the webhook (local tests), alerts still go out. Their WhatsApp channel
just stays "Sent", because the delivered/read report can't reach a local PC.

## Cost

Meta charges each **utility** template message at the rate of the recipient's
country (Tunisia is billed at the "Rest of Africa" rate, a fraction of a US cent;
see Meta's pricing page). There is no monthly fee, and because CarLink connects
to Meta directly there is no reseller markup. The SMS is still sent and billed
by TunisieSMS.

## Security

- The owner's number goes to Meta only in the JSON body of the send (never in a
  URL), and it is never logged. Logs show the owner as a SHA-256 hash and
  error codes only. The `wamid` id embeds the number, so it is never logged and
  only its SHA-256 is stored.
- Meta's replies and webhook calls carry the number (`wa_id`, `recipient_id`).
  CarLink never logs or stores them.
- The access token and the app secret live only in `.env`.
- Webhook calls without a valid `X-Hub-Signature-256` get a 401. A signed call
  is always answered 200, because Meta retries any other answer.
- Messages owners send to the CarLink number are ignored. Nothing forwards them
  to the visitor.
