#!/bin/sh
set -e

# BACKEND_UPSTREAM 支持两种写法：
#   1) 内网服务名：wc-ai-agent-backend:80            （推荐，同环境内网互访）
#   2) 公网域名：  https://xxx.sh.run.tcloudbase.com （走公网 HTTPS）

RAW=${BACKEND_UPSTREAM:-backend:80}
case "$RAW" in
  https://*) PROTO=https; HOSTPORT=${RAW#https://} ;;
  http://*)  PROTO=http;  HOSTPORT=${RAW#http://} ;;
  *)         PROTO=http;  HOSTPORT=$RAW ;;
esac

# 去掉可能填写的路径，只保留 host[:port]
HOSTPORT=${HOSTPORT%%/*}
HOST=${HOSTPORT%%:*}

# 未显式指定端口时：https 默认 443，http 默认 80
if [ "$HOST" = "$HOSTPORT" ]; then
  if [ "$PROTO" = "https" ]; then
    PORT=443
  else
    PORT=80
  fi
else
  PORT=${HOSTPORT##*:}
fi

# 内网短服务名补全为集群 FQDN（nginx 的 resolver 不读 /etc/resolv.conf 的 search 域）
BACKEND_HOST_HEADER=$HOST
if [ "$PROTO" = "http" ]; then
  case "$HOST" in
    *.*) ;;
    *)
      SEARCH_DOMAIN=$(awk '/^search[[:space:]]/ {print $2; exit}' /etc/resolv.conf)
      if [ -n "$SEARCH_DOMAIN" ]; then
        HOST="$HOST.$SEARCH_DOMAIN"
      fi
    ;;
  esac
fi
BACKEND_FULL="$HOST:$PORT"

# 集群 DNS（用于服务名解析），再补一个腾讯云公共 DNS（用于公网域名解析）
KUBE_DNS=$(awk '/^nameserver[[:space:]]/ {print $2; exit}' /etc/resolv.conf)
DNS_RESOLVER="$KUBE_DNS 119.29.29.29"

export BACKEND_PROTO=$PROTO
export BACKEND_FULL
export BACKEND_HOST_HEADER
export DNS_RESOLVER

exec /docker-entrypoint.sh nginx -g 'daemon off;'
