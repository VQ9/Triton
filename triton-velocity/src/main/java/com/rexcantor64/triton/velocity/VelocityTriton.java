package com.rexcantor64.triton.velocity;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.player.PlayerManager;
import com.rexcantor64.triton.plugin.PluginLoader;
import com.rexcantor64.triton.storage.LocalStorage;
import com.rexcantor64.triton.velocity.bridge.VelocityBridgeManager;
import com.rexcantor64.triton.velocity.commands.handler.VelocityCommandHandler;
import com.rexcantor64.triton.velocity.listeners.VelocityListener;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.rexcantor64.triton.velocity.plugin.VelocityPlugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import lombok.val;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VelocityTriton extends Triton<VelocityLanguagePlayer, VelocityBridgeManager> {

    @Getter
    private ChannelIdentifier bridgeChannelIdentifier;
    private ScheduledTask configRefreshTask;

    public VelocityTriton(PluginLoader loader) {
        super(new PlayerManager<>(VelocityLanguagePlayer::fromUUID), new VelocityBridgeManager());
        super.loader = loader;
    }

    public static VelocityTriton asVelocity() {
        return (VelocityTriton) instance;
    }

    public VelocityPlugin getLoader() {
        return (VelocityPlugin) this.loader;
    }

    @Override
    public void onEnable() {
        instance = this;
        super.onEnable();

        // bStats
        Metrics metrics = getLoader().getMetricsFactory().make(getLoader(), 16222);
        metrics.addCustomChart(new SingleLineChart("active_placeholders",
                () -> this.getTranslationManager().getTranslationCount()));

        val eventManager = getLoader().getServer().getEventManager();
        eventManager.register(getLoader(), new VelocityListener());
        eventManager.register(getLoader(), bridgeManager);

        this.bridgeChannelIdentifier = MinecraftChannelIdentifier.create("triton", "main");
        getLoader().getServer().getChannelRegistrar().register(this.bridgeChannelIdentifier);

        if (getStorage() instanceof LocalStorage)
            bridgeManager.sendConfigToEveryone();

        val commandHandler = new VelocityCommandHandler();
        val commandManager = getLoader().getServer().getCommandManager();
        commandManager.register(commandManager.metaBuilder("triton")
                .aliases(getConfig().getCommandAliases().toArray(new String[0])).build(), commandHandler);
        commandManager.register(commandManager.metaBuilder("twin").build(), commandHandler);
    }

    @Override
    public void reload() {
        super.reload();
        if (bridgeManager != null)
            bridgeManager.sendConfigToEveryone();
    }

    @Override
    protected void startConfigRefreshTask() {
        if (configRefreshTask != null) configRefreshTask.cancel();
        if (getConfig().getConfigAutoRefresh() <= 0) return;
        configRefreshTask = getLoader().getServer().getScheduler().buildTask(getLoader(), this::reload)
                .delay(getConfig().getConfigAutoRefresh(), TimeUnit.SECONDS).schedule();
    }


    public File getDataFolder() {
        return getLoader().getDataDirectory().toFile();
    }

    @Override
    public String getVersion() {
        return "@version@";
    }

    @Override
    public void runAsync(Runnable runnable) {
        getLoader().getServer().getScheduler().buildTask(getLoader(), runnable).schedule();
    }

    public ProxyServer getVelocity() {
        return getLoader().getServer();
    }

    @Override
    public UUID getPlayerUUIDFromString(String input) {
        val player = getVelocity().getPlayer(input);
        if (player.isPresent()) return player.get().getUniqueId();

        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected String getConfigFileName() {
        return "config_velocity";
    }
}
