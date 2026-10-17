FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY src ./src
COPY lib ./lib
COPY db ./db
RUN javac -encoding UTF-8 -d out src/*.java
EXPOSE 8090
CMD ["java", "-cp", "out:lib/*", "InicioServidor"]
