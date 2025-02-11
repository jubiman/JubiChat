package com.jubiman.jubichat;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<String> hostname;
    private static final ForgeConfigSpec.IntValue port;
    private static final ForgeConfigSpec.ConfigValue<String> identifier;
    private static final ForgeConfigSpec.ConfigValue<String> messageFormat;
    private static final ForgeConfigSpec.IntValue reconnectInterval;
  
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        {
            builder.push("server");
            {
                builder.comment("The hostname of the server to connect to.");
                hostname = builder.define("hostname", "127.0.0.1");
                builder.comment("The port of the server to connect to.");
                port = builder.defineInRange("port", 3542, 0, 65535);
                builder.comment("The identifier (aka server name) to use when connecting to the server.");
                identifier = builder.define("identifier", "UNKNOWN");
                builder.comment("The format of the message sent to the Jubi Socket.");
                builder.comment(
                        "Placeholders: %s = server name, %u = username, %m = message. Use \\\\ to escape the placeholders.");
                messageFormat = builder.define("server_message_format", "§1§l[%s] §4§u<%u>§r %m");
                builder.comment("The interval in seconds to wait before reconnecting to the Jubi Socket.");
                // 5 minutes default, 1 second minimum, 1 hour maximum
                reconnectInterval = builder.defineInRange("reconnect_interval", 5 * 60, 1, 60 * 60);
                builder.pop();
            }
        }
        SPEC = builder.build();
    }

    public static String getHostname() {
        return hostname.get();
    }

    public static int getPort() {
        return port.get();
    }
    
    public static String getIdentifier() {
        return identifier.get();
    }

    public static String getMessageFormat() {
        return messageFormat.get();
    }

    public static int getReconnectInterval() {
        return reconnectInterval.get();
    }
}
