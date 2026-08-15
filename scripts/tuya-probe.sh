#!/usr/bin/env bash
# One-off probe against the Tuya Cloud API — is the account reachable, does the signing scheme
# work, and what are the recuperators called? Nothing in the app calls Tuya yet; this exists to
# answer docs/tuya.md's open questions before any code is written against them.
#
#   scripts/tuya-probe.sh
#
# Credentials come from local.properties (gitignored) and are never echoed. Two API calls per run,
# against a monthly allowance of 26,000 — cheap, but not free. Delete this script once
# docs/tuya.md records what it found.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/local.properties"

[[ -f "$PROPS" ]] || { echo "no local.properties at $PROPS" >&2; exit 1; }

prop() {
    local value
    value="$(sed -n "s/^$1=//p" "$PROPS" | head -1 | tr -d '\r')"
    [[ -n "$value" ]] || { echo "local.properties is missing $1" >&2; exit 1; }
    printf '%s' "$value"
}

CLIENT_ID="$(prop 'tuya\.client\.id')"
CLIENT_SECRET="$(prop 'tuya\.client\.secret')"
UID_VALUE="$(prop 'tuya\.uid')"
HOST="$(prop 'tuya\.region\.host')"

# Signature per https://developer.tuya.com/en/docs/iot/new-singnature — the fiddly part is that
# stringToSign carries a body digest and the *sorted* query string, and that the HMAC is uppercase.
# The empty-body digest is the well-known SHA256 of "".
EMPTY_BODY_SHA256='e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'

# sign METHOD URL ACCESS_TOKEN TIMESTAMP -> uppercase hex HMAC. access_token is "" for /v1.0/token
# itself; nonce is unused, so it contributes an empty string to both str and stringToSign.
sign() {
    local method="$1" url="$2" access_token="$3" t="$4"
    local string_to_sign="$method
$EMPTY_BODY_SHA256

$url"
    printf '%s' "${CLIENT_ID}${access_token}${t}${string_to_sign}" |
        openssl dgst -sha256 -hmac "$CLIENT_SECRET" |
        awk '{print $NF}' |
        tr '[:lower:]' '[:upper:]'
}

# Tuya wants a 13-digit millisecond timestamp; BSD date has no %N, and second precision is inside
# the tolerated skew.
now_ms() { echo "$(( $(date +%s) * 1000 ))"; }

call() {
    local method="$1" url="$2" access_token="${3:-}"
    local t signature
    t="$(now_ms)"
    signature="$(sign "$method" "$url" "$access_token" "$t")"

    local headers=(
        -H "client_id: $CLIENT_ID"
        -H "sign: $signature"
        -H "t: $t"
        -H "sign_method: HMAC-SHA256"
    )
    [[ -n "$access_token" ]] && headers+=(-H "access_token: $access_token")

    curl --silent --show-error --max-time 20 --request "$method" "${headers[@]}" "$HOST$url"
}

# The response is small and flat; sed is enough and keeps the probe dependency-free.
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" <<<"$2"; }

echo "host: $HOST"
echo

# GET, not POST — POST returns 1108 "uri path invalid" for every path on the host, including
# nonsense ones, which is what the route not existing for that method looks like.
echo "== GET /v1.0/token?grant_type=1 =="
token_response="$(call GET '/v1.0/token?grant_type=1')"
# Never print the response verbatim — it carries the access and refresh tokens.
if [[ "$token_response" != *'"success":true'* ]]; then
    echo "failed: $token_response" >&2
    echo >&2
    echo "1004 sign invalid means the signature; 1106 permission denied usually means the data" >&2
    echo "centre in tuya.region.host is not the one the project was created in." >&2
    exit 1
fi
ACCESS_TOKEN="$(field access_token "$token_response")"
[[ -n "$ACCESS_TOKEN" ]] || { echo "success, but no access_token in the response" >&2; exit 1; }
echo "ok — access_token acquired, expires in $(sed -n 's/.*"expire_time":\([0-9]*\).*/\1/p' <<<"$token_response")s"
echo

# Best guess at the device-list endpoint for a linked Smart Life account. If this 404s or comes
# back empty while the console shows the recuperators, that is the finding — record it, do not
# paper over it.
echo "== GET /v1.0/users/$UID_VALUE/devices =="
call GET "/v1.0/users/$UID_VALUE/devices" "$ACCESS_TOKEN"
echo
calls=2

# Pass a device id to ask what it can actually do, or any number of paths starting with "/" to GET
# them verbatim — the datapoint story differs by endpoint, so poking at several is the point.
#   scripts/tuya-probe.sh <device_id>
#   scripts/tuya-probe.sh /v2.0/cloud/thing/<device_id>/model
for arg in "$@"; do
    if [[ "$arg" == /* ]]; then
        echo
        echo "== GET $arg =="
        call GET "$arg" "$ACCESS_TOKEN"
        echo
        calls=$((calls + 1))
    else
        for endpoint in specifications functions; do
            echo
            echo "== GET /v1.0/devices/$arg/$endpoint =="
            call GET "/v1.0/devices/$arg/$endpoint" "$ACCESS_TOKEN"
            echo
            calls=$((calls + 1))
        done
    fi
done

echo
echo "$calls API calls spent."
