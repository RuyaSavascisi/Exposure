package io.github.mortuusars.exposure.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import io.github.mortuusars.exposure.Config;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.command.ExposureCommands;
import io.github.mortuusars.exposure.command.ShaderCommand;
import io.github.mortuusars.exposure.command.TestCommand;
import io.github.mortuusars.exposure.command.argument.ShaderLocationArgument;
import io.github.mortuusars.exposure.network.fabric.PacketsImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.fml.config.ModConfig;

public class ExposureFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Exposure.init();

        ForgeConfigRegistry.INSTANCE.register(Exposure.ID, ModConfig.Type.COMMON, Config.Common.SPEC);
        ForgeConfigRegistry.INSTANCE.register(Exposure.ID, ModConfig.Type.CLIENT, Config.Client.SPEC);

        ArgumentTypeRegistry.registerArgumentType(Exposure.resource("shader_location"),
                ShaderLocationArgument.class, SingletonArgumentInfo.contextFree(ShaderLocationArgument::new));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            ExposureCommands.register(dispatcher);
            ShaderCommand.register(dispatcher);
            TestCommand.register(dispatcher);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(content -> {
            content.prepend(Exposure.Items.CAMERA.get());
            content.prepend(Exposure.Items.BLACK_AND_WHITE_FILM.get());
            content.prepend(Exposure.Items.COLOR_FILM.get());
            content.prepend(Exposure.Items.DEVELOPED_BLACK_AND_WHITE_FILM.get());
            content.prepend(Exposure.Items.DEVELOPED_COLOR_FILM.get());
            content.prepend(Exposure.Items.PHOTOGRAPH.get());
            content.prepend(Exposure.Items.STACKED_PHOTOGRAPHS.get());
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(content -> {
            content.prepend(Exposure.Items.LIGHTROOM.get());
        });

        Exposure.Advancements.register();
        Exposure.Stats.register();

        ServerLifecycleEvents.SERVER_STARTING.register(Exposure::initServer);

        PacketsImpl.registerC2SPackets();
    }
}
