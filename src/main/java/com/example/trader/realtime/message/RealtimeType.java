package com.example.trader.realtime.message;

public enum RealtimeType {
    //kafka->redis pub/sub -> broadcast
    RELIABLE,
    //redis pub/sub -> broadcast
    VOLATILE
}