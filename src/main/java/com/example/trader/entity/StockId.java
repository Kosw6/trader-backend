package com.example.trader.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockId implements Serializable {
    private String symb;
    private OffsetDateTime timestamp;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockId)) return false;
        StockId that = (StockId) o;
        return Objects.equals(symb, that.symb) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symb, timestamp);
    }
}
