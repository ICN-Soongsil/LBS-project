package ICN.itrc_project.consumer.config;

import ICN.itrc_project.consumer.service.LocationEventConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;

@Configuration
public class RedisStreamConfig {
    @Bean
    public UnifiedJedis unifiedJedis() {
        return new UnifiedJedis("redis://localhost:6379");
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(
            RedisConnectionFactory factory, LocationEventConsumer consumer) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        container.receive(
                Consumer.from("lbs_group", "consumer_1"),
                StreamOffset.create("lbs_stream", ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        return container;
    }
}