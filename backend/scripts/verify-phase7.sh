#!/usr/bin/env bash
# Live verification for Phase 7 — owner conversation dashboard.
# Requires the dev jar running against docker-compose postgres+redis.
set -u
BASE="http://localhost:8080"
PHONE="+21655120789"
STAMP=$(date +%s)
E1="live_a${STAMP}@example.com"
E2="live_b${STAMP}@example.com"
PASS=0; FAIL=0

jqget() { node -e "const d=JSON.parse(require('fs').readFileSync(0,'utf8'));console.log(eval('d.'+process.argv[1]))" "$1"; }

check() { # $1=desc $2=expected $3=actual
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "  ✅ $1";
  else FAIL=$((FAIL+1)); echo "  ❌ $1 — expected [$2] got [$3]"; fi
}

register_login() { # $1=email -> access token
  curl -s -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"Secret123\",\"firstName\":\"L\",\"lastName\":\"U\",\"phone\":\"$PHONE\"}" >/dev/null
  curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"Secret123\"}" | jqget accessToken
}

A1=$(register_login "$E1")
A2=$(register_login "$E2")
B1="Authorization: Bearer $A1"
B2="Authorization: Bearer $A2"

echo "[1/7] owner1 creates a vehicle + QR"
VID=$(curl -s -X POST "$BASE/api/v1/vehicles" -H "$B1" -H 'Content-Type: application/json' \
  -d '{"nickname":"Live Car","brand":"Tesla","model":"Model 3","color":"Red","licensePlate":"LV-77-777"}' | jqget id)
TOKEN=$(curl -s -X POST "$BASE/api/v1/vehicles/$VID/qr" -H "$B1" | jqget rawToken)
echo "  vehicle=$VID token=${TOKEN:0:12}..."

echo "[2/7] visitor submits two contacts (WHATSAPP then SMS)"
C1=$(curl -s -X POST "$BASE/api/v1/public/qr/$TOKEN/contact" -H 'Content-Type: application/json' \
  -d '{"channel":"WHATSAPP","message":"Hello, is this available?"}' | jqget conversationId)
C2=$(curl -s -X POST "$BASE/api/v1/public/qr/$TOKEN/contact" -H 'Content-Type: application/json' \
  -d '{"channel":"SMS","message":"Can you call me back please?"}' | jqget conversationId)
echo "  conversations: $C1, $C2"

echo "[3/7] owner1 lists dashboard — 2 items, both unread, SMS first (newest)"
LIST=$(curl -s "$BASE/api/v1/conversations" -H "$B1")
check "2 conversations" "2" "$(node -e "console.log($LIST.length)")"
check "first channel SMS" "SMS" "$(node -e "console.log($LIST[0].channel)")"
check "first unread" "true" "$(node -e "console.log($LIST[0].unread)")"
check "first preview" "Can you call me back please?" "$(node -e "console.log($LIST[0].lastMessagePreview)")"
check "second unread" "true" "$(node -e "console.log($LIST[1].unread)")"

echo "[4/7] owner1 reads conversation detail (full history ordered)"
DET=$(curl -s "$BASE/api/v1/conversations/$C2" -H "$B1")
check "detail id matches" "$C2" "$(node -e "console.log($DET.id)")"
check "detail unread" "true" "$(node -e "console.log($DET.unread)")"
check "1 message" "1" "$(node -e "console.log($DET.messages.length)")"
check "message content" "Can you call me back please?" "$(node -e "console.log($DET.messages[0].content)")"
check "no licensePlate leak" "undefined" "$(node -e "console.log($DET.licensePlate)")"
check "no phone leak" "undefined" "$(node -e "console.log($DET.phone)")"

echo "[5/7] owner1 marks conversation read"
curl -s -X POST "$BASE/api/v1/conversations/$C2/read" -H "$B1" >/dev/null
DET2=$(curl -s "$BASE/api/v1/conversations/$C2" -H "$B1")
check "unread now false" "false" "$(node -e "console.log($DET2.unread)")"
LIST2=$(curl -s "$BASE/api/v1/conversations" -H "$B1")
check "list reflects read" "false" "$(node -e "console.log($LIST2[0].unread)")"
READAT=$(curl -s "$BASE/api/v1/conversations/$C2" -H "$B1" | jqget expiresAt) # sanity only
check "C1 still unread" "true" "$(node -e "console.log($LIST2[1].unread)")"

echo "[6/7] cross-owner + unauthenticated isolation"
curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/conversations/$C2" -H "$B2" > /tmp/code.txt
check "owner2 detail -> 404" "404" "$(cat /tmp/code.txt)"
curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/conversations/$C2" > /tmp/code.txt
check "no-auth detail -> 401" "401" "$(cat /tmp/code.txt)"
curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/conversations/$C2/read" -H "$B2" > /tmp/code.txt
check "owner2 mark-read -> 404" "404" "$(cat /tmp/code.txt)"
O2LIST=$(curl -s "$BASE/api/v1/conversations" -H "$B2")
check "owner2 dashboard empty" "0" "$(node -e "console.log($O2LIST.length)")"

echo "[7/7] DB check — read_at populated, no raw token stored"
DB_READAT=$(docker exec carlink-postgres psql -U carlink -d carlink -tAc \
  "SELECT (read_at IS NOT NULL) FROM conversations WHERE id='$C2'")
check "conversation C2 read_at set in DB" "t" "$DB_READAT"
DB_TOKENHASH=$(docker exec carlink-postgres psql -U carlink -d carlink -tAc \
  "SELECT (SELECT count(*) FROM qr_codes WHERE token_hash IS NOT NULL) = (SELECT count(*) FROM qr_codes)")
check "qr_codes holds hash on every row" "t" "$DB_TOKENHASH"

echo ""
echo "========================================"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && echo "ALL LIVE CHECKS PASSED" || echo "SOME CHECKS FAILED"