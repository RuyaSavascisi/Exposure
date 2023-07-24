package io.github.mortuusars.exposure.camera.component;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.item.CameraItem;
import io.github.mortuusars.exposure.util.ItemAndStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class Shutter {
    private final Map<Player, OpenShutter> openShutters = new HashMap<>();

    public boolean isOpen(Player player) {
        return openShutters.containsKey(player);
    }

    public void open(Player player, ItemAndStack<CameraItem> camera, ShutterSpeed shutterSpeed, boolean exposingFrame) {
        OpenShutter openShutter = new OpenShutter(camera, shutterSpeed, System.currentTimeMillis(), exposingFrame);
        openShutters.put(player, openShutter);
        camera.getItem().onShutterOpen(player, openShutter);
    }

    public void close(Player player) {
        @Nullable OpenShutter shutter = openShutters.remove(player);
        if (shutter != null)
            shutter.camera().getItem().onShutterClosed(player, shutter);
    }

    public void tick(Player player) {
        if (!isOpen(player))
            return;

        List<Player> toClose = new ArrayList<>();

        for (Map.Entry<Player, OpenShutter> shutter : openShutters.entrySet()) {
            if (System.currentTimeMillis() - shutter.getValue().openedAt > shutter.getValue().shutterSpeed.getMilliseconds())
                toClose.add(shutter.getKey());
            else
                shutter.getValue().camera.getItem().onShutterTick(shutter.getKey(), shutter.getValue());
        }

        for (Player pl : toClose) {
            close(pl);
        }
    }

    public record OpenShutter(ItemAndStack<CameraItem> camera, ShutterSpeed shutterSpeed, long openedAt, boolean exposingFrame) {}
}
