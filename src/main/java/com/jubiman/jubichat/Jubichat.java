package com.jubiman.jubichat;

import com.jubiman.jubichat.network.WebSocketConnection;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jline.utils.Levenshtein;
import org.slf4j.Logger;

@Mod(Jubichat.MODID)
public class Jubichat {
    public static final String MODID = "jubichat";
    public static final Logger LOGGER = LogUtils.getLogger();
    private WebSocketConnection connection;

    public Jubichat() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        // Register command to reconnect to the websocket, only works if the user has the permission level of OP
        event.getDispatcher().register(Commands.literal("jubichat")
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reconnect")
                                .executes(context -> {
                                    try {
                                        this.connection.reconnect();
                                        context.getSource().sendSuccess(() -> Component.literal(Config.getIdentifier() + " reconnected to JubiCord Socket"), false);
                                    } catch (Exception e) {
                                        LOGGER.warn("Failed to reconnect to JubiCord Socket", e);
                                        context.getSource().sendFailure(Component.literal("Failed to reconnect to JubiCord Socket! (See server logs for more info)"));
                                        return -1;
                                    }
                                    return 1;
                                }))));
        // TODO: add more commands, and maybe modularize it in a commands folder?
    }


    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Trying to connect to JubiCord Socket");
        try {
            connection = new WebSocketConnection();
            connection.sendServerStateEvent("started");
        } catch (Exception e) {
            LOGGER.warn("Failed to connect to JubiCord Socket", e);
        }
    }

    @SubscribeEvent
    public void onChatMessage(ServerChatEvent event) {
        connection.sendMessage(event.getUsername(), event.getPlayer().getUUID(), event.getMessage());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        connection.sendServerStateEvent("stopping");
        LOGGER.info("Closing JubiCord Socket connection");
        connection.cancelReconnect();
        connection.close("Server is closing connection");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        connection.sendPlayerEvent(event.getEntity().getName().getString(), event.getEntity().getUUID(), true);
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        connection.sendPlayerEvent(event.getEntity().getName().getString(), event.getEntity().getUUID(), false);
    }
}
