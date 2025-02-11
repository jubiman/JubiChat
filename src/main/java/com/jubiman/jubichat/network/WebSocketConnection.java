package com.jubiman.jubichat.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jubiman.jubichat.Config;
import com.jubiman.jubichat.Jubichat;
import com.jubiman.jubichat.network.events.*;
import net.minecraft.network.chat.Component;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Handles the WebSocket connection to the Jubi Socket.
 */
public class WebSocketConnection implements WebSocket.Listener {    
    private static final Gson gson = new Gson();
    private WebSocket webSocket;
    private static final HashMap<String, Event> events = new HashMap<>();
    private final TimerTask TIMER_TASK;
    private final BlockingQueue<JsonObject> QUEUE = new LinkedBlockingQueue<>();
    private boolean open = true;
    private final RestartableThread sendThread = new RestartableThread(this::sendLoop, "JubiChat Socket Send Loop");
    private String buffer = "";

    static {
        events.put("identify", new IdentifyEvent());
        events.put("message", new MessageEvent());
        // TODO:
//        events.put(PacketType.PRIVATE_MESSAGE, new PrivateMessageEvent());
//        events.put("playerCount", new PlayerCountEvent());
    }

    public WebSocketConnection() {
        Jubichat.LOGGER.debug("Connecting to Jubi Socket on {}:{}", Config.getHostname(), Config.getPort());

        CompletableFuture<WebSocket> sock = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(
                        java.net.URI.create("ws://" + Config.getHostname() + ":" + Config.getPort()),
                        this);

        try {
            webSocket = sock.join();
        } catch (Exception e) {
            Jubichat.LOGGER.error("Failed to connect to Jubi Socket.", e);
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Jubichat.LOGGER.info("Jubi Socket connection closed, reconnecting...");
                        reconnect();
                        // Stop the initial timer task
                        cancel();
                    } catch (CompletionException e) {
                        Jubichat.LOGGER.error("Failed to reconnect to Jubi Socket. Probably offline, check debug logs for more info.");
                        Jubichat.LOGGER.debug("Failed to reconnect to Jubi Socket.", e);
                    }
                }
            }, Config.getReconnectInterval() * 1000L, Config.getReconnectInterval() * 1000L);
            TIMER_TASK = null;
            return;
        }

        // Schedule a reconnect on a set interval, defined in the config (in seconds, so * 1000L ms)
        TIMER_TASK = new WebsocketTimerTask(webSocket, this);
        //new Timer().scheduleAtFixedRate(TIMER_TASK, Config.getReconnectInterval() * 1000L, Config.getReconnectInterval() * 1000L);

        Jubichat.LOGGER.info("Successfully connected to Jubi Socket and set up reconnect timer.");
    }

    /**
     * Sends an event to the Jubi Socket.
     *
     * @param json The event to send.
     */
    public void sendJson(JsonObject json) {
        Jubichat.LOGGER.debug("Adding message to queue: {}", json);
        try {
            QUEUE.put(json);
        } catch (Exception e) {
            Jubichat.LOGGER.error("Failed to add message to queue. See debug logs for more info.");
            Jubichat.LOGGER.debug("Failed to add message to queue.", e);
        }
        Jubichat.LOGGER.debug("Added message to queue: {}", json);
    }

    /**
     * Sends a message to the Jubi Socket.
     *
     * @param username The username of the player who sent the message.
     * @param message  The message that was sent.
     */
    public void sendMessage(String username, UUID uuid, Component message) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "message");
        json.addProperty("server", Config.getIdentifier());
        json.addProperty("player", username);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("message", message.getString());

        sendJson(json);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
    }

        private void sendLoop() {
        while (open) {
            try {
                // TODO: convert to packet
                JsonObject json = QUEUE.take();

                Jubichat.LOGGER.debug("Sending event to Jubi Socket: {}", json);
                try {
                    webSocket.sendText(json.toString(), true).join();
                } catch (Exception e) {
                    Jubichat.LOGGER.error("Failed to send event to Jubi Socket. See debug logs for more info.");
                    Jubichat.LOGGER.debug("Failed to send event to Jubi Socket.", e);
                }
                Jubichat.LOGGER.info("Sent event to Jubi Socket: {}", json);
            } catch (InterruptedException ignored) {
            }
        }
        Jubichat.LOGGER.debug("Send loop closed.");
    }
    
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        WebSocket.Listener.super.onText(webSocket, data, last);
        buffer += data;

        if (!last) {
            Jubichat.LOGGER.debug("Received partial message from Jubi Socket: {}", data);
            return null;
        }
        Jubichat.LOGGER.info("Received message from Jubi Socket: {}", buffer);

        try {
            JsonObject json = gson.fromJson(buffer, JsonObject.class);
            events.getOrDefault(json.get("event").getAsString().toLowerCase(),
                    (ignored, json1) -> Jubichat.LOGGER
                            .warn("Received unknown event from Jubi Socket: {}", json1.get("event").getAsString())
            ).handle(this, json);
        } catch (Exception e) {
            Jubichat.LOGGER.error("Failed to send message to Jubi Socket. See debug logs for more info.");
            Jubichat.LOGGER.debug("Failed to send message to Jubi Socket.", e);
        }
        // Reset buffer
        buffer = "";
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        WebSocket.Listener.super.onBinary(webSocket, data, last);
        Jubichat.LOGGER.debug("Received binary message from Jubi Socket: {}", data);
        
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        Jubichat.LOGGER.info("Jubi Socket closed with status code {} and reason: {}", statusCode, reason);
        this.open = false;
        sendThread.stop();
        this.webSocket = null;
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        WebSocket.Listener.super.onError(webSocket, error);
        Jubichat.LOGGER.error("Jubi Socket errored", error);
    }

    /**
     * Closes the WebSocket connection.
     *
     * @param reason The reason for closing the connection.
     */
    public void close(String reason) {
        if (webSocket == null) return;
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason).join();
        } catch (Exception e) {
            Jubichat.LOGGER.error("Failed to close Jubi Socket.", e);
        }
        sendThread.stop();
    }

    /**
     * Reconnects to the Jubi Socket.
     *
     * @throws CompletionException If the connection fails.
     */
    public void reconnect() {
        if (TIMER_TASK != null)
            TIMER_TASK.cancel();
        CompletableFuture<WebSocket> sock = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(
                        java.net.URI.create("ws://" + Config.getHostname() + ":" + Config.getPort() + "/WSSMessaging"),
                        this);

        webSocket = sock.join();
    }

    /**
     * Sends a player join/leave event to the Jubi Socket.
     *
     * @param username The username of the player.
     * @param joined   Whether the player joined or left. True for joined, false for left.
     */
    public void sendPlayerEvent(String username, UUID uuid, boolean joined) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "playerState");
        json.addProperty("server", Config.getIdentifier());
        json.addProperty("player", username);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("joined", joined ? "joined" : "left");

        sendJson(json);
    }

    public void sendServerStateEvent(String state) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "serverState");
        json.addProperty("server", Config.getIdentifier());
        json.addProperty("state", state);

        sendJson(json);
    }

    public void identify() {
        Jubichat.LOGGER.info("Identifying to Jubi Socket as {}", Config.getIdentifier());
        JsonObject out = new JsonObject();
        out.addProperty("event", "identify");
        out.addProperty("identifier", Config.getIdentifier());
        
        webSocket.sendText(out.toString(), true).join();
        
        open = true;
        sendThread.restart();
    }

    private static class WebsocketTimerTask extends TimerTask {
        private final WebSocket webSocket;
        private final WebSocketConnection connection;

        public WebsocketTimerTask(WebSocket webSocket, WebSocketConnection connection) {
            this.webSocket = webSocket;
            this.connection = connection;
        }

        @Override
        public void run() {
            try {
                Jubichat.LOGGER.debug("Checking if Jubi Socket connection is closed...");
                if (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed()) {
                    Jubichat.LOGGER.info("Jubi Socket connection closed, reconnecting...");
                    connection.reconnect();
                }
            } catch (CompletionException e) {
                Jubichat.LOGGER.error("Failed to reconnect to Jubi Socket. Probably offline, check debug logs for more info.");
                Jubichat.LOGGER.debug("Failed to reconnect to Jubi Socket.", e);
            }
        }
    }
}
