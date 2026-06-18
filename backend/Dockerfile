FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/scala-3.7.2/loan-backend-assembly.jar /app/app.jar
COPY target/otel-agent.jar /app/otel-agent.jar
ENV OTEL_SERVICE_NAME=loan-backend
ENV DUMP_DIR=/var/dumps
EXPOSE 8080
ENTRYPOINT ["sh","-c","java -javaagent:/app/otel-agent.jar -XX:StartFlightRecording=disk=true,maxsize=256m,name=continuous,settings=default,dumponexit=false -jar /app/app.jar"]
