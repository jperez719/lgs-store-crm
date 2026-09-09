package com.jperez.apitests.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class SelfContainedEnvironment {

    private static final String APP_IMAGE =
            System.getProperty("APP_IMAGE", "lgs-store-crm:latest");

    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static RabbitMQContainer rabbitMQ;
    private static GenericContainer<?> app;

    private static boolean started = false;

    public static synchronized String start() {
        if (started) {
            return currentBaseUrl();
        }

        network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("store_crm_db")
                .withUsername("store_admin")
                .withPassword("changeme");
        postgres.start();

        rabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
                .withNetwork(network)
                .withNetworkAliases("rabbitmq");
        rabbitMQ.start();

        app = new GenericContainer<>(DockerImageName.parse(APP_IMAGE))
                .withNetwork(network)
                .withExposedPorts(8080)
                .withEnv("SPRING_PROFILES_ACTIVE", "docker")
                .withEnv("DB_USERNAME", "store_admin")
                .withEnv("DB_PASSWORD", "changeme")
                .withEnv("RABBITMQ_USERNAME", rabbitMQ.getAdminUsername())
                .withEnv("RABBITMQ_PASSWORD", rabbitMQ.getAdminPassword())
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
        app.start();

        started = true;
        return currentBaseUrl();
    }

    private static String currentBaseUrl() {
        return "http://" + app.getHost() + ":" + app.getMappedPort(8080);
    }
}
