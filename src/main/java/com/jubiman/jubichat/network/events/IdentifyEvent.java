package com.jubiman.jubichat.network.events;

import com.google.gson.JsonObject;
import com.jubiman.jubichat.network.WebSocketConnection;

public class IdentifyEvent implements Event {
    @Override
    public void handle(WebSocketConnection connection, JsonObject json) {
        connection.identify();
    }
}