package io.taskmesh.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfig {
    @Bean
    public ServerClient serverClient(
            @Value("${taskmesh.server-url:http://localhost:8080}") String baseUrl) {
        return new ServerClient(baseUrl);
    }
}
