package com.rexcantor64.triton.velocity.player;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.language.Language;
import com.rexcantor64.triton.language.ExecutableCommand;
import com.rexcantor64.triton.player.LanguagePlayer;
import com.rexcantor64.triton.utils.SocketUtils;
import com.rexcantor64.triton.velocity.VelocityTriton;
import com.rexcantor64.triton.velocity.packetinterceptor.VelocityNettyDecoder;
import com.rexcantor64.triton.velocity.packetinterceptor.VelocityNettyEncoder;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityLanguagePlayer implements LanguagePlayer {
    @Getter
    private final Player parent;

    private Language language;

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PUBLIC)
    private Component lastTabHeader;
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PUBLIC)
    private Component lastTabFooter;
    private final Map<UUID, Component> bossBars = new HashMap<>();
    private final Map<UUID, Component> playerListItemCache = new ConcurrentHashMap<>();
    private boolean waitingForClientLocale = false;
    private String clientLocale;
    private final RefreshFeatures refresher;

    public VelocityLanguagePlayer(@NotNull Player parent) {
        this.parent = parent;
        this.refresher = new RefreshFeatures(this);
        Triton.get().runAsync(this::load);
    }

    public static VelocityLanguagePlayer fromUUID(UUID uuid) {
        val player = VelocityTriton.asVelocity().getLoader().getServer().getPlayer(uuid);
        return player.map(VelocityLanguagePlayer::new).orElse(null);
    }

    public void setBossbar(UUID uuid, Component lastBossBar) {
        bossBars.put(uuid, lastBossBar);
    }

    public void removeBossbar(UUID uuid) {
        bossBars.remove(uuid);
    }

    Map<UUID, Component> getCachedBossBars() {
        return Collections.unmodifiableMap(bossBars);
    }

    public void cachePlayerListItem(UUID uuid, Component lastDisplayName) {
        playerListItemCache.put(uuid, lastDisplayName);
    }

    public void deleteCachedPlayerListItem(UUID uuid) {
        playerListItemCache.remove(uuid);
    }

    Map<UUID, Component> getCachedPlayerListItems() {
        return Collections.unmodifiableMap(playerListItemCache);
    }

    @Override
    public boolean isWaitingForClientLocale() {
        return waitingForClientLocale;
    }

    @Override
    public void waitForClientLocale() {
        this.waitingForClientLocale = true;
    }

    public void setClientLocale(String locale) {
        if (this.isWaitingForClientLocale()) {
            this.setLang(Triton.get().getLanguageManager().getLanguageByLocaleOrDefault(locale));
        }
        this.clientLocale = locale;
    }

    public Language getLang() {
        if (language == null)
            language = Triton.get().getLanguageManager().getMainLanguage();
        return language;
    }

    public void setLang(Language language) {
        setLang(language, true);
    }

    public void setLang(Language language, boolean sendToSpigot) {
        // TODO fire Triton's API change language event
        if (this.waitingForClientLocale && getParent() != null)
            parent.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(Triton.get().getMessagesConfig()
                    .getMessage("success.detected-language", language.getDisplayName())));
        this.language = language;
        this.waitingForClientLocale = false;

        if (sendToSpigot && getParent() != null) {
            VelocityTriton.asVelocity().getBridgeManager().sendPlayerLanguage(this);
        }

        save();
        refreshAll();
        executeCommands(null);
    }

    public void refreshAll() {
        this.refresher.refreshAll();
    }

    public void injectNettyPipeline() {
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) this.parent;
        connectedPlayer.getConnection().getChannel().pipeline()
                .addAfter(Connections.MINECRAFT_DECODER, "triton-custom-decoder", new VelocityNettyDecoder(this));
        connectedPlayer.getConnection().getChannel().pipeline()
                .addAfter(Connections.MINECRAFT_ENCODER, "triton-custom-encoder", new VelocityNettyEncoder(this));
    }

    @Override
    public UUID getUUID() {
        return this.parent.getUniqueId();
    }

    public @NotNull ProtocolVersion getProtocolVersion() {
        return this.getParent().getProtocolVersion();
    }

    private void load() {
        this.language = Triton.get().getStorage().getLanguage(this);
        if (this.clientLocale != null && this.isWaitingForClientLocale()) {
            this.waitingForClientLocale = false;
            this.language = Triton.get().getLanguageManager().getLanguageByLocaleOrDefault(this.clientLocale);
            if (getParent() != null) {
                getParent().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(Triton.get().getMessagesConfig()
                        .getMessage("success.detected-language", language.getDisplayName())));
            }
        }
        if (getParent() != null) {
            Triton.get().getStorage()
                    .setLanguage(null, SocketUtils.getIpAddress(getParent().getRemoteAddress()), language);
        }
    }

    private void save() {
        Triton.get().runAsync(() -> {
            val ip = SocketUtils.getIpAddress(getParent().getRemoteAddress());
            Triton.get().getStorage().setLanguage(getParent().getUniqueId(), ip, language);
        });
    }

    public void executeCommands(RegisteredServer overrideServer) {
        val currentServer = getParent().getCurrentServer();
        if (overrideServer == null && !currentServer.isPresent()) return;
        val server = overrideServer == null ? currentServer.get().getServer() : overrideServer;
        for (val cmd : ((com.rexcantor64.triton.language.Language) language).getCmds()) {
            val cmdText = cmd.getCmd().replace("%player%", getParent().getUsername())
                    .replace("%uuid%", getParent().getUniqueId().toString());

            if (!cmd.isUniversal() && !cmd.getServers().contains(server.getServerInfo().getName())) {
                continue;
            }

            val velocity = VelocityTriton.asVelocity().getLoader().getServer();

            if (cmd.getType() == ExecutableCommand.Type.SERVER) {
                VelocityTriton.asVelocity().getBridgeManager().sendExecutableCommand(cmdText, server);
            } else if (cmd.getType() == ExecutableCommand.Type.PLAYER) {
                getParent().spoofChatInput("/" + cmdText);
            } else if (cmd.getType() == ExecutableCommand.Type.BUNGEE) {
                velocity.getCommandManager().executeAsync(velocity.getConsoleCommandSource(), cmdText);
            } else if (cmd.getType() == ExecutableCommand.Type.BUNGEE_PLAYER) {
                velocity.getCommandManager().executeAsync(getParent(), cmdText);
            }
        }
    }

    @Override
    public String toString() {
        return "VelocityLanguagePlayer{" +
                "username=" + parent.getUsername() +
                ", uuid=" + parent.getUniqueId() +
                ", language=" + Optional.ofNullable(language).map(Language::getName).orElse("null") +
                '}';
    }
}
