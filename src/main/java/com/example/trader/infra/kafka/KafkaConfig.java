package com.example.trader.infra.kafka;

import com.example.trader.realtime.message.RealtimeEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@EnableKafka
@Configuration
@ConditionalOnProperty(
        name = "realtime.kafka.enabled",
        havingValue = "true"
)
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, RealtimeEnvelope> producerFactory(KafkaProperties properties) {
        Map<String, Object> config = properties.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, RealtimeEnvelope> kafkaTemplate(
            ProducerFactory<String, RealtimeEnvelope> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, RealtimeEnvelope> consumerFactory(KafkaProperties properties) {
        Map<String, Object> config = properties.buildConsumerProperties(null);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.trader.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RealtimeEnvelope.class.getName());

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(RealtimeEnvelope.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RealtimeEnvelope> kafkaListenerContainerFactory(
            ConsumerFactory<String, RealtimeEnvelope> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, RealtimeEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}