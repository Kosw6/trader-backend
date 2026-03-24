package com.example.trader.config;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    // =========================
    // Producer
    // =========================
    @Bean
    public ProducerFactory<String, CanvasEventEnvelope> producerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties());

        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, CanvasEventEnvelope> kafkaTemplate(
            ProducerFactory<String, CanvasEventEnvelope> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    // =========================
    // Consumer
    // =========================
    @Bean
    public ConsumerFactory<String, CanvasEventEnvelope> consumerFactory(KafkaProperties properties) {
        JsonDeserializer<CanvasEventEnvelope> deserializer =
                new JsonDeserializer<>(CanvasEventEnvelope.class);

        deserializer.addTrustedPackages("*");

        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties());

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CanvasEventEnvelope>
    kafkaListenerContainerFactory(ConsumerFactory<String, CanvasEventEnvelope> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, CanvasEventEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}
