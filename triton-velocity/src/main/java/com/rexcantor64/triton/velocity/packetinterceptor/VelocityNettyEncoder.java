package com.rexcantor64.triton.velocity.packetinterceptor;

import com.rexcantor64.triton.velocity.packetinterceptor.packets.BossBarHandler;
import com.rexcantor64.triton.velocity.packetinterceptor.packets.ChatHandler;
import com.rexcantor64.triton.velocity.packetinterceptor.packets.DisconnectHandler;
import com.rexcantor64.triton.velocity.packetinterceptor.packets.ResourcePackHandler;
import com.rexcantor64.triton.velocity.packetinterceptor.packets.TabHandler;
import com.rexcantor64.triton.velocity.packetinterceptor.packets.TitleHandler;
import com.rexcantor64.triton.velocity.player.VelocityLanguagePlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.BossBar;
import com.velocitypowered.proxy.protocol.packet.Disconnect;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooter;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItem;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfo;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequest;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfo;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChat;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChat;
import com.velocitypowered.proxy.protocol.packet.title.LegacyTitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleActionbarPacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleSubtitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleTextPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.ReferenceCounted;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@RequiredArgsConstructor
public class VelocityNettyEncoder extends MessageToMessageEncoder<MinecraftPacket> {

    private final VelocityLanguagePlayer player;
    private static final Map<Class<?>, BiFunction<?, VelocityLanguagePlayer, Optional<MinecraftPacket>>> handlerMap = new HashMap<>();

    static {
        val chatHandler = new ChatHandler();
        addHandler(SystemChat.class, chatHandler::handleSystemChat);
        addHandler(LegacyChat.class, chatHandler::handleLegacyChat);

        val titleHandler = new TitleHandler();
        addHandler(TitleTextPacket.class, titleHandler::handleGenericTitle);
        addHandler(TitleSubtitlePacket.class, titleHandler::handleGenericTitle);
        addHandler(TitleActionbarPacket.class, titleHandler::handleGenericTitle);
        addHandler(LegacyTitlePacket.class, titleHandler::handleLegacyTitle);

        val tabHandler = new TabHandler();
        addHandler(HeaderAndFooter.class, tabHandler::handlePlayerListHeaderFooter);
        addHandler(LegacyPlayerListItem.class, tabHandler::handlePlayerListItem);
        addHandler(UpsertPlayerInfo.class, tabHandler::handleUpsertPlayerInfo);
        addHandler(RemovePlayerInfo.class, tabHandler::handleRemovePlayerInfo);

        addHandler(Disconnect.class, new DisconnectHandler()::handleDisconnect);
        addHandler(ResourcePackRequest.class, new ResourcePackHandler()::handleResourcePackRequest);
        addHandler(BossBar.class, new BossBarHandler()::handleBossBar);
    }

    private static <T extends MinecraftPacket> void addHandler(final @NotNull Class<T> type, final @NotNull BiFunction<@NotNull T, @NotNull VelocityLanguagePlayer, @NotNull Optional<MinecraftPacket>> handler) {
        handlerMap.put(type, handler);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, MinecraftPacket packet, List<Object> out) {
        if (packet instanceof ReferenceCounted) {
            // We need to retain the packet since we're just passing them through, otherwise Netty will throw an error
            ((ReferenceCounted) packet).retain();
            out.add(packet);
            return;
        }
        @SuppressWarnings("unchecked")
        val handler = (BiFunction<MinecraftPacket, VelocityLanguagePlayer, Optional<MinecraftPacket>>) handlerMap.get(packet.getClass());
        if (handler != null) {
            Optional<MinecraftPacket> result = handler.apply(packet, this.player);
            if (result.isPresent()) {
                out.add(result.get());
            } else {
                // Discard the packet
                out.add(false);
            }
        } else {
            out.add(packet);
        }
    }

}
