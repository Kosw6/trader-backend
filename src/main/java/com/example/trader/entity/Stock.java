package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Objects;

@Entity
@Table(name = "stock")
@IdClass(StockId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Stock {

    @Id
    @Column(length = 12, nullable = false)
    @EqualsAndHashCode.Include
    private String symb;

    @Id
    @Column(nullable = false)
    @EqualsAndHashCode.Include
    private OffsetDateTime timestamp;

    @Column(nullable = false)
    private Double open;

    @Column(nullable = false)
    private Double high;

    @Column(nullable = false)
    private Double low;

    @Column(nullable = false)
    private Double close;

    @Column(nullable = false)
    private Long volume;
}

