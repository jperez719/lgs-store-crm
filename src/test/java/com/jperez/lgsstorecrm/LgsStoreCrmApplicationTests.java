package com.jperez.lgsstorecrm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class LgsStoreCrmApplicationTests {

	@Container
	static RabbitMQContainer rabbitMQ =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
		registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
		registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
		registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
	}

	@Test
	void contextLoads() {
	}
}
