package com.jubiman.jubichat.network.events;

import com.google.gson.JsonObject;
import com.jubiman.jubichat.network.WebSocketConnection;

public interface Event {
	void handle(WebSocketConnection connection, JsonObject packet);
}
