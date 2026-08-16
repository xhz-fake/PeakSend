## 第一阶段, 编译打包
FROM maven:3.9.9-eclipse-temurin-8 AS builder
#FROM 意思是：我接下来要基于哪个现成环境开始做
#先找一台已经装好 Java + Maven 的干净机器，专门负责打包代码

WORKDIR /build
#意思是：后面所有操作，默认都在 /build 这个目录里进行

RUN apt-get update \
    && apt-get install -y openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY pom.xml ./
COPY sky-common/pom.xml sky-common/pom.xml
COPY sky-pojo/pom.xml sky-pojo/pom.xml
COPY sky-server/pom.xml sky-server/pom.xml

COPY sky-common/src sky-common/src
COPY sky-pojo/src sky-pojo/src
COPY sky-server/src sky-server/src
#这部分的作用就是：把你本地项目里的代码和配置，复制到容器里的 /build 目录下，把整个后端工程需要的东西都复制进去
#Docker 打包不是直接读取你电脑里的代码，而是要先把代码“放进容器里”再打包。

RUN mvn -pl sky-server -am clean package -DskipTests
#在容器里执行 Maven 打包命令，这一行本质就是在容器里把 PeakSend 后端打成 jar 包

## 第二阶段，真正运行
FROM eclipse-temurin:8-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /build/sky-server/target/sky-server-1.0-SNAPSHOT.jar app.jar
#意思是：从前面那个叫 builder 的阶段里，把打好的 jar 包复制到当前运行环境里，并改名成 app.jar

EXPOSE 8080
#这个容器里的应用，主要监听 8080 端口

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
##容器一启动，就执行这条命令
