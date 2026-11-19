#!/usr/bin/env bash
# Makes a VAPID key pair for streak reminders (Web Push) and prints the two .env lines.
# Run once per site; keep the private key secret (it goes in the server's .env, never in git).
set -euo pipefail
pem=$(mktemp)
trap 'rm -f "$pem"' EXIT
openssl ecparam -name prime256v1 -genkey -noout -out "$pem" 2>/dev/null
b64url() { base64 | tr -d '\n=' | tr '/+' '_-'; }
# the 32-byte private scalar sits after a 7-byte header in the SEC1 DER; the public point is the last 65 bytes
private=$(openssl ec -in "$pem" -outform DER 2>/dev/null | tail -c +8 | head -c 32 | b64url)
public=$(openssl ec -in "$pem" -pubout -outform DER 2>/dev/null | tail -c 65 | b64url)
echo "QUIZCADIA_PUSH_PUBLIC_KEY=$public"
echo "QUIZCADIA_PUSH_PRIVATE_KEY=$private"
