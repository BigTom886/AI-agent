# 注意：微信云托管会在容器启动时注入 /app/cert/initenv.sh 生命周期钩子，
# 该机制依赖 glibc，不能使用 alpine（musl）镜像，否则钩子退出码 137。
FROM nginx:1.27

# 删除默认配置；预置平台证书脚本所需的 /app/cert 目录
RUN rm -f /etc/nginx/conf.d/default.conf && mkdir -p /app/cert

# nginx 配置模板（启动时由官方 entrypoint 做 envsubst）
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
# 自定义入口：自动获取集群 DNS、补全后端服务 FQDN，再启动 nginx
COPY nginx/docker-entrypoint-wrapper.sh /docker-entrypoint-wrapper.sh
RUN chmod +x /docker-entrypoint-wrapper.sh

# 拷贝构建好的前端静态文件
COPY dist/ /usr/share/nginx/html/

# 后端服务地址，必须在微信云托管「服务设置 - 环境变量」中配置为真实后端服务名:端口
# 同一环境内服务间通过服务名互访，例如 wc-ai-agent-backend:80
ENV BACKEND_UPSTREAM=backend:80

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint-wrapper.sh"]
