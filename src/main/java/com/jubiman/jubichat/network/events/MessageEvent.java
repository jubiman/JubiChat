package com.jubiman.jubichat.network.events;

import com.google.gson.JsonObject;
import com.jubiman.jubichat.Config;
import com.jubiman.jubichat.network.WebSocketConnection;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

public class MessageEvent implements Event {
	@Override
	public void handle(WebSocketConnection connection, JsonObject json) {
        // Iteratoe over all online players and display the message to them
        String message = Config.getMessageFormat()
                .replaceAll("(?<!\\\\)%s", Config.getIdentifier())
                .replaceAll("(?<!\\\\)%u", json.get("username").getAsString())
                .replaceAll("(?<!\\\\)%m", json.get("message").getAsString());
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.displayClientMessage(ComponentUtils.fromMessage(() -> message), false);
        }
	}
}
