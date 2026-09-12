# 多模块 Maven 工程：在根目录构建 app 与 mcp-search 两个 fat jar
FROM maven:3.9-amazoncorretto-21
WORKDIR /app

# 拷贝父 POM
COPY pom.xml ./
# 拷贝主应用模块
COPY wc-ai-agent-app/pom.xml ./wc-ai-agent-app/
COPY wc-ai-agent-app/src ./wc-ai-agent-app/src
# 拷贝 MCP 搜图服务模块（容器内以 stdio 子进程方式被主应用拉起）
COPY wc-ai-agent-mcp-search/pom.xml ./wc-ai-agent-mcp-search/
COPY wc-ai-agent-mcp-search/src ./wc-ai-agent-mcp-search/src

# 只构建主应用模块（prod 不启用 MCP，mcp-search 不进入镜像）
# -pl 指定模块，-am 顺带构建其依赖的模块
RUN mvn -pl wc-ai-agent-app -am clean package -DskipTests

# 微信云托管服务端口需与应用监听端口一致，统一为 80
EXPOSE 80

# 以 prod profile 启动主应用
CMD ["java", "-jar", "/app/wc-ai-agent-app/target/wc-ai-agent-app-1.0-SNAPSHOT.jar", "--spring.profiles.active=prod"]
