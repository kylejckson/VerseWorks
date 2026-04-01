package com.kyden.verseworks.dimension;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kyden.verseworks.VerseWorks;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public final class VerseDimensionParameterSync {
    private static final StreamCodec<RegistryFriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION_STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(ResourceLocation::parse, ResourceLocation::toString)
            .mapStream(buffer -> (ByteBuf) buffer);

    private VerseDimensionParameterSync() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            SyncPayload.TYPE,
            SyncPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(VerseDimensionParameterSync::handleOnClient, VerseDimensionParameterSync::handleOnServer)
        );
    }

    public static void syncKnownDimensions(ServerPlayer player) {
        for (ResourceLocation dimensionId : VerseDimensionCatalog.knownDimensionIds(player.serverLevel().getServer())) {
            VerseDimensionCatalog.get(player.serverLevel().getServer(), dimensionId)
                .ifPresent(parameters -> syncToPlayer(player, dimensionId, parameters));
        }
    }

    public static void syncToPlayers(ServerLevel level, VerseDimensionParameters parameters) {
        SyncPayload payload = new SyncPayload(level.dimension().location(), encodeParameters(parameters));
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void syncToPlayer(ServerPlayer player, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
        PacketDistributor.sendToPlayer(player, new SyncPayload(dimensionId, encodeParameters(parameters)));
    }

    private static String encodeParameters(VerseDimensionParameters parameters) {
        return VerseDimensionParameters.CODEC.encodeStart(JsonOps.INSTANCE, parameters)
            .resultOrPartial(error -> VerseWorks.LOGGER.error("Failed to encode VerseWorks client dimension sync payload: {}", error))
            .map(JsonElement::toString)
            .orElse("{}");
    }

    private static Optional<VerseDimensionParameters> decodeParameters(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return VerseDimensionParameters.CODEC.parse(JsonOps.INSTANCE, element)
                .resultOrPartial(error -> VerseWorks.LOGGER.error("Failed to decode VerseWorks client dimension sync payload: {}", error));
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Failed to parse VerseWorks client dimension sync payload JSON", exception);
            return Optional.empty();
        }
    }

    private static void handleOnClient(SyncPayload payload, IPayloadContext context) {
        decodeParameters(payload.parametersJson())
            .ifPresent(parameters -> VerseDimensionCatalog.remember(payload.dimensionId(), parameters));
    }

    private static void handleOnServer(SyncPayload payload, IPayloadContext context) {
    }

    public record SyncPayload(ResourceLocation dimensionId, String parametersJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "sync_dimension_parameters")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
            RESOURCE_LOCATION_STREAM_CODEC,
            SyncPayload::dimensionId,
            ByteBufCodecs.STRING_UTF8,
            SyncPayload::parametersJson,
            SyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}