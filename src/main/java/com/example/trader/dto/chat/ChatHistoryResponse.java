package com.example.trader.dto.chat;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, Long nextBeforeId) { }
