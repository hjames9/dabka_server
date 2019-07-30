#!/bin/bash
#
# Usage: generate_tls_cert.sh -d <SAN DNS (optional)> -i <SAN IP (optional)> -t <CERT TYPE (rsa or ec)> -r <Root CA (optional)> -c <CSR only (optional, not self signed)>
#
# Example: ./generate_tls_cert.sh -d localhost -i 192.168.1.234 -t ec -r -c
# Example  ./generate_tls_cert.sh -d localhost -i 192.168.1.164 -t ec -r -c

OPENSSL=openssl
COUNTRY_NAME=US
STATE="New Jersey"
LOCALITY=Hoboken
ORGANIZATION="The Hayden Place"
ORGANIZATIONAL_UNIT=Engineering
COMMON_NAME=dabka.thehaydenplace.io
EMAIL_ADDRESS=dabka@thehaydenplace.io
EXPIRATION_DAYS=365
ROOT_CA=false
CSR=false

while getopts "d:i:t:rc" opt; do
    case $opt in
    d)
        SAN_DNS=$OPTARG
        ;;
    i)
        SAN_IP=$OPTARG
        ;;
    t)
        CERT_TYPE=$OPTARG
        ;;
    r)
        ROOT_CA=true
        ;;
    c)
        CSR=true
        ;;
    *)
        echo >&2 "Invalid argument: $1"
        exit 1
        ;;
    esac
done

if [ -z $CERT_TYPE ] || [[ "$CERT_TYPE" != "rsa" && "$CERT_TYPE" != "ec" ]]; then
    echo "Usage: ${0} -d <SAN DNS (optional)> -i <SAN IP (optional)> -t <CERT TYPE (rsa or ec)> -r <Root CA (optional)> -c <CSR only (optional, not self signed)>"
    exit 1
fi

if [ "$CERT_TYPE" == "rsa" ]; then
    CERT_TYPE_DETAIL="rsa:2048"
    KEY_FILENAME=key_rsa_2048.pem
    CERT_FILENAME=cert_rsa_2048.pem
elif [ "$CERT_TYPE" == "ec" ]; then
    CERT_TYPE_DETAIL="ec:<(openssl ecparam -name secp384r1)"
    KEY_FILENAME=key_secp384r1.pem
    CERT_FILENAME=cert_secp384r1.pem
fi

EXTENSIONS=
OPENSSL_CFG_PARAMS=
if [ "$ROOT_CA" == "true" ]; then
    EXTENSIONS="-extensions v3_req "
    OPENSSL_CFG_PARAMS="\n[v3_req]\nbasicConstraints=CA:TRUE"
fi

if [ ! -z $SAN_DNS ] && [ ! -z $SAN_IP ]; then
    OPENSSL_CFG_PARAMS+="\n[SAN]\nsubjectAltName=DNS:${SAN_DNS},IP:${SAN_IP}"
elif [ ! -z $SAN_DNS ]; then
    OPENSSL_CFG_PARAMS+="\n[SAN]\nsubjectAltName=DNS:${SAN_DNS}"
elif [ ! -z $SAN_IP ]; then
    OPENSSL_CFG_PARAMS+="\n[SAN]\nsubjectAltName=IP:${SAN_IP}"
fi

if [ ! -z $SAN_DNS ] || [ ! -z $SAN_IP ]; then
    EXTENSIONS+="-reqexts SAN -extensions SAN"
fi

OPENSSL_CFG=
if [ ! -z $OPENSSL_CFG_PARAMS ]; then
    OPENSSL_CFG=$(printf "\-config <(cat /etc/ssl/openssl.cnf <(printf \"%s\"))" $OPENSSL_CFG_PARAMS)
fi

X509=
if [ "$CSR" == "false" ]; then
    X509="-x509"
fi

echo "Generating certificate and key..."

eval ${OPENSSL} req ${X509} -nodes -newkey ${CERT_TYPE_DETAIL} -keyout ${KEY_FILENAME} -out ${CERT_FILENAME} -days ${EXPIRATION_DAYS} -subj \"/C="${COUNTRY_NAME}"/ST="${STATE}"/L="${LOCALITY}"/O="${ORGANIZATION}"/OU="${ORGANIZATIONAL_UNIT}"/CN="${COMMON_NAME}"/emailAddress="${EMAIL_ADDRESS}"\" "$EXTENSIONS" "${OPENSSL_CFG}"
