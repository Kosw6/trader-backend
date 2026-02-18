package com.example.trader.repository.projection;

import java.time.LocalDateTime;

public interface NotifycationView {

    Long getId();

    String getMessage();

    Boolean getIsRead();

    String getType();

    LocalDateTime getCreatedAt();

    String getDeeplink();
}
