FROM xiyangai/alpine-jre17:latest

LABEL authors="caomingyang"

WORKDIR /opt

EXPOSE 8080

ENV LANG C.UTF-8
ENV java_opts="-Xms64m -Xmx128m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -Dfile.encoding=utf-8"

COPY target/wechat-chatgpt.jar /opt/wechat-chatgpt.jar

ENTRYPOINT exec java -jar $java_opts /opt/wechat-chatgpt.jar
