package io.github.mortuusars.exposure.item;

import io.github.mortuusars.exposure.camera.ExposureFrame;
import io.github.mortuusars.exposure.camera.film.FilmType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface IFilmItem {
    FilmType getType();
    List<ExposureFrame> getExposedFrames(ItemStack filmStack);
}