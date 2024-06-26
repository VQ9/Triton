package com.rexcantor64.triton.velocity.packetinterceptor.packets;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.config.FeatureSyntax;
import com.rexcantor64.triton.api.language.MessageParser;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import lombok.val;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public class TabHandler {

    private MessageParser parser() {
        return Triton.get().getMessageParser();
    }

    private boolean shouldNotTranslateTab() {
        return !Triton.get().getConfig().isTab();
    }

    private FeatureSyntax getTabSyntax() {
        return Triton.get().getConfig().getTabSyntax();
    }

    public @NotNull Optional<MinecraftPacket> handlePlayerListHeaderFooter(@NotNull HeaderAndFooterPacket headerFooterPacket, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateTab()) {
            return Optional.of(headerFooterPacket);
        }

        player.setLastTabHeader(headerFooterPacket.getHeader().getComponent());
        player.setLastTabFooter(headerFooterPacket.getFooter().getComponent());

        Optional<ComponentHolder> newHeader = parser().translateComponent(
                        headerFooterPacket.getHeader().getComponent(),
                        player,
                        getTabSyntax()
                )
                .getResultOrToRemove(Component::empty)
                .map(result -> new ComponentHolder(player.getProtocolVersion(), result));
        Optional<ComponentHolder> newFooter = parser().translateComponent(
                        headerFooterPacket.getFooter().getComponent(),
                        player,
                        getTabSyntax()
                )
                .getResultOrToRemove(Component::empty)
                .map(result -> new ComponentHolder(player.getProtocolVersion(), result));

        if (newFooter.isPresent() || newHeader.isPresent()) {
            return Optional.of(
                    new HeaderAndFooterPacket(
                            newHeader.orElseGet(headerFooterPacket::getHeader),
                            newFooter.orElseGet(headerFooterPacket::getFooter)
                    )
            );
        }
        return Optional.of(headerFooterPacket);
    }

    public @NotNull Optional<MinecraftPacket> handlePlayerListItem(@NotNull LegacyPlayerListItemPacket playerListItemPacket, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateTab()) {
            return Optional.of(playerListItemPacket);
        }

        val items = playerListItemPacket.getItems();
        val action = playerListItemPacket.getAction();

        for (LegacyPlayerListItemPacket.Item item : items) {
            val uuid = item.getUuid();
            switch (action) {
                case LegacyPlayerListItemPacket.ADD_PLAYER:
                case LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME:
                    if (item.getDisplayName() == null) {
                        player.deleteCachedPlayerListItem(uuid);
                        break;
                    }
                    parser()
                            .translateComponent(
                                    item.getDisplayName(),
                                    player,
                                    getTabSyntax()
                            )
                            .ifChanged(result -> {
                                player.cachePlayerListItem(uuid, item.getDisplayName());
                                item.setDisplayName(result);
                            })
                            .ifUnchanged(() -> player.deleteCachedPlayerListItem(uuid))
                            .ifToRemove(() -> player.deleteCachedPlayerListItem(uuid));
                    break;
                case LegacyPlayerListItemPacket.REMOVE_PLAYER:
                    player.deleteCachedPlayerListItem(uuid);
                    break;
            }
        }

        return Optional.of(playerListItemPacket);
    }

    public @NotNull Optional<MinecraftPacket> handleUpsertPlayerInfo(@NotNull UpsertPlayerInfoPacket upsertPlayerInfoPacket, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateTab()) {
            return Optional.of(upsertPlayerInfoPacket);
        }
        if (!upsertPlayerInfoPacket.getActions().contains(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
            return Optional.of(upsertPlayerInfoPacket);
        }
        for (UpsertPlayerInfoPacket.Entry item : upsertPlayerInfoPacket.getEntries()) {
            val uuid = item.getProfileId();
            if (item.getDisplayName() == null) {
                player.deleteCachedPlayerListItem(uuid);
                break;
            }
            parser()
                    .translateComponent(
                            item.getDisplayName().getComponent(),
                            player,
                            getTabSyntax()
                    )
                    .ifChanged(result -> {
                        player.cachePlayerListItem(uuid, item.getDisplayName().getComponent());
                        item.setDisplayName(new ComponentHolder(player.getProtocolVersion(), result));
                    })
                    .ifUnchanged(() -> player.deleteCachedPlayerListItem(uuid))
                    .ifToRemove(() -> player.deleteCachedPlayerListItem(uuid));
            break;
        }

        return Optional.of(upsertPlayerInfoPacket);
    }

    public @NotNull Optional<MinecraftPacket> handleRemovePlayerInfo(@NotNull RemovePlayerInfoPacket removePlayerInfoPacket, @NotNull VelocityLanguagePlayer player) {
        if (shouldNotTranslateTab()) {
            return Optional.of(removePlayerInfoPacket);
        }
        for (UUID uuid : removePlayerInfoPacket.getProfilesToRemove()) {
            player.deleteCachedPlayerListItem(uuid);
        }

        return Optional.of(removePlayerInfoPacket);
    }
}
