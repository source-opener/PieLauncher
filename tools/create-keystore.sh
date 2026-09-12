#!/bin/sh
# Creates the signing keystore for .github/workflows/release.yml and
# prints the repository secrets to set for it.

set -eu

KEYSTORE=${KEYSTORE:-release.jks}
ALIAS=${ALIAS:-pielauncher}
VALIDITY=${VALIDITY:-10950}

if [ -e "$KEYSTORE" ]; then
	echo "$KEYSTORE exists already, refusing to overwrite it." >&2
	exit 1
fi

command -v keytool >/dev/null || {
	echo "keytool not found, please install a JDK." >&2
	exit 1
}

printf 'Password for the new keystore: '
stty -echo 2>/dev/null || true
read -r PASSWORD
stty echo 2>/dev/null || true
printf '\n'

[ -n "$PASSWORD" ] || {
	echo "The password must not be empty." >&2
	exit 1
}

keytool -genkeypair \
	-keystore "$KEYSTORE" \
	-storetype PKCS12 \
	-storepass "$PASSWORD" \
	-keypass "$PASSWORD" \
	-alias "$ALIAS" \
	-keyalg RSA \
	-keysize 4096 \
	-validity "$VALIDITY" \
	-dname "CN=Pie Launcher, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"

cat <<INSTRUCTIONS

Created $KEYSTORE.

Add these repository secrets under
Settings > Secrets and variables > Actions > New repository secret:

  KEYSTORE_BASE64    the single line printed below
  KEYSTORE_PASSWORD  the password you just entered
  KEY_ALIAS          $ALIAS
  KEY_PASSWORD       the password you just entered

KEYSTORE_BASE64:

INSTRUCTIONS

base64 -w 0 "$KEYSTORE" 2>/dev/null || base64 "$KEYSTORE" | tr -d '\n'
printf '\n\nBack up %s. Losing it means no more updates for installed apps.\n' "$KEYSTORE"
