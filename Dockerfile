FROM xiyangai/alpine-jre17:latest

LABEL authors="caomingyang"

WORKDIR /opt

EXPOSE 8080

ENV LANG C.UTF-8
ENV java_opts="-Xms64m -Xmx128m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -Dfile.encoding=utf-8"

COPY target/wechatgpt.jar /opt/wechatgpt.jar

ENTRYPOINT exec java -jar $java_opts /opt/wechatgpt.jar
