FROM eclipse-temurin:25-jdk
EXPOSE 8080
ADD server/target/app.jar statusrobot-backend.jar
ENTRYPOINT ["java","-jar","statusrobot-backend.jar"]