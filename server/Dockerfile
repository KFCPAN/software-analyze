# 校园失物招领平台 · 后端一体化镜像
# 构建：docker build -t lnf-server .
# 运行：一般通过 ../deploy/docker-compose.yml（profile app）启动，见 docs/启动指南.md
#
# 多阶段构建：Maven 打包 → JRE 运行。宿主机无需安装 JDK/Maven，
# 也绕开了仓库中文路径导致 mvn spring-boot:run 启动失败的问题。

# 国内网络若拉不动 Docker Hub，构建时传参换镜像站，例如：
#   docker build --build-arg MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 \
#                --build-arg JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre -t lnf-server .
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre

# ---------- 构建阶段 ----------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build

# 先拷 pom 单独成层：依赖不变时构建缓存直接复用，不用重复下载
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- 运行阶段 ----------
FROM ${JRE_IMAGE}
WORKDIR /app

# 上传图片目录（卷持久化；可用环境变量 APP_UPLOAD_DIR 覆盖）
ENV APP_UPLOAD_DIR=/app/uploads \
    TZ=Asia/Shanghai

COPY --from=build /build/target/lnf-server-0.0.1-SNAPSHOT.jar app.jar

VOLUME /app/uploads
EXPOSE 8080

# 数据库/密钥等全部通过环境变量注入（compose 已配好），见 application.yml 的默认值
ENTRYPOINT ["java", "-jar", "app.jar"]
