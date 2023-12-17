package io.github.mortuusars.exposure.client.gui.screen;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Axis;
import io.github.mortuusars.exposure.Config;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.camera.capture.component.FileSaveComponent;
import io.github.mortuusars.exposure.client.render.ExposureRenderer;
import io.github.mortuusars.exposure.item.PhotographItem;
import io.github.mortuusars.exposure.util.ItemAndStack;
import io.github.mortuusars.exposure.util.Navigation;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PhotographScreen extends ZoomableScreen {
    public static final ResourceLocation WIDGETS_TEXTURE = Exposure.resource("textures/gui/widgets.png");
    public static final int BUTTON_SIZE = 16;

    private final List<ItemAndStack<PhotographItem>> photographs;
    private final List<String> savedExposures = new ArrayList<>();
    private int currentIndex = 0;
    private long lastCycledAt;

    private ImageButton previousButton;
    private ImageButton nextButton;

    public PhotographScreen(List<ItemAndStack<PhotographItem>> photographs) {
        super(Component.empty());
        Preconditions.checkState(photographs.size() > 0, "No photographs to display.");
        this.photographs = photographs;

        // Query all photographs:
        for (ItemAndStack<PhotographItem> photograph : photographs) {
            @Nullable Either<String, ResourceLocation> idOrTexture = photograph.getItem()
                    .getIdOrTexture(photograph.getStack());
            if (idOrTexture != null)
                idOrTexture.ifLeft(id -> ExposureClient.getExposureStorage().getOrQuery(id));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        zoomFactor = (float) height / ExposureRenderer.SIZE;

        if (photographs.size() > 1) {
            previousButton = new ImageButton(0, (int) (height / 2f - BUTTON_SIZE / 2f), BUTTON_SIZE, BUTTON_SIZE,
                    0, 0, BUTTON_SIZE, WIDGETS_TEXTURE, this::buttonPressed);
            nextButton = new ImageButton(width - BUTTON_SIZE, (int) (height / 2f - BUTTON_SIZE / 2f), BUTTON_SIZE, BUTTON_SIZE,
                    16, 0, BUTTON_SIZE, WIDGETS_TEXTURE, this::buttonPressed);

            addRenderableWidget(previousButton);
            addRenderableWidget(nextButton);
        }
    }

    private void buttonPressed(Button button) {
        if (button == previousButton)
            cyclePhotograph(Navigation.PREVIOUS);
        else if (button == nextButton)
            cyclePhotograph(Navigation.NEXT);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500); // Otherwise exposure will overlap buttons
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();

        guiGraphics.pose().pushPose();

        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().translate(width / 2f, height / 2f, 0);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.pose().translate(ExposureRenderer.SIZE / -2f, ExposureRenderer.SIZE / -2f, 0);

        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        // Rendering paper bottom to top:
        for (int i = Math.min(2, photographs.size() - 1); i > 0; i--) {
            float posOffset = 4 * i;
            int brightness = Mth.clamp(255 - 50 * i, 0, 255);

            float rotateOffset = ExposureRenderer.SIZE / 2f;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(posOffset, posOffset, 0);

            guiGraphics.pose().translate(rotateOffset, rotateOffset, 0);
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(i * 90 + 90));
            guiGraphics.pose().translate(-rotateOffset, -rotateOffset, 0);

            ExposureClient.getExposureRenderer().renderPaperTexture(guiGraphics.pose(),
                    bufferSource, 0, 0, ExposureRenderer.SIZE, ExposureRenderer.SIZE, 0, 0, 1, 1,
                    LightTexture.FULL_BRIGHT, brightness, brightness, brightness, 255);

            guiGraphics.pose().popPose();
        }

        ItemAndStack<PhotographItem> photograph = photographs.get(currentIndex);
        @Nullable Either<String, ResourceLocation> idOrTexture = photograph.getItem().getIdOrTexture(photograph.getStack());
        if (idOrTexture != null) {
            ExposureClient.getExposureRenderer().renderOnPaper(idOrTexture, guiGraphics.pose(), bufferSource,
                    0, 0, ExposureRenderer.SIZE, ExposureRenderer.SIZE, 0, 0, 1, 1,
                    LightTexture.FULL_BRIGHT, 255, 255, 255, 255, false);
        }
        else {
            ExposureClient.getExposureRenderer().renderPaperTexture(guiGraphics.pose(), bufferSource,
                    0, 0, ExposureRenderer.SIZE, ExposureRenderer.SIZE, 0, 0, 1, 1,
                    LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
        }
        bufferSource.endBatch();

        guiGraphics.pose().popPose();

        trySaveToFile(photograph, idOrTexture);
    }

    private void trySaveToFile(ItemAndStack<PhotographItem> photograph, @Nullable Either<String, ResourceLocation> idOrTexture) {
        if (!Config.Client.EXPOSURE_SAVING.get() || idOrTexture == null || Minecraft.getInstance().player == null)
            return;

        CompoundTag tag = photograph.getStack().getTag();
        if (tag == null
                || !tag.contains("PhotographerId", Tag.TAG_INT_ARRAY)
                || !tag.getUUID("PhotographerId").equals(Minecraft.getInstance().player.getUUID())) {
            return;
        }

        idOrTexture.ifLeft(id -> {
            if (savedExposures.contains(id))
                return;

            ExposureClient.getExposureStorage().getOrQuery(id).ifPresent(exposure -> {
                savedExposures.add(id);
                new Thread(() -> FileSaveComponent.withDefaultFolders(id)
                        .save(exposure.getPixels(), exposure.getWidth(), exposure.getHeight(), exposure.getProperties()), "ExposureSaving").start();
            });
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled)
            return true;

        if (minecraft.options.keyLeft.matches(keyCode, scanCode) || keyCode == InputConstants.KEY_LEFT)
            cyclePhotograph(Navigation.PREVIOUS);
        else if (minecraft.options.keyRight.matches(keyCode, scanCode) || keyCode == InputConstants.KEY_RIGHT)
            cyclePhotograph(Navigation.NEXT);
        else
            return false;

        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyRight.matches(keyCode, scanCode) || keyCode == InputConstants.KEY_RIGHT
                || minecraft.options.keyLeft.matches(keyCode, scanCode) || keyCode == InputConstants.KEY_LEFT) {
            lastCycledAt = 0;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void cyclePhotograph(Navigation navigation) {
        if (Util.getMillis() - lastCycledAt < 50)
            return;

        int prevIndex = currentIndex;

        currentIndex += navigation == Navigation.NEXT ? 1 : -1;
        if (currentIndex >= photographs.size())
            currentIndex = 0;
        else if (currentIndex < 0)
            currentIndex = photographs.size() - 1;

        if (prevIndex != currentIndex && minecraft.player != null) {
            minecraft.player.playSound(Exposure.SoundEvents.CAMERA_LENS_RING_CLICK.get(), 0.8f,
                    minecraft.player.level().getRandom()
                            .nextFloat() * 0.2f + (navigation == Navigation.NEXT ? 1.1f : 0.9f));
            lastCycledAt = Util.getMillis();
        }
    }
}
