package dev.corgitaco.worldviewer.client.tile;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.mixin.NativeImageAccessor;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SingleScreenTileLayer implements ScreenTileLayer {

    private static final ExecutorService FILE_SAVING_EXECUTOR_SERVICE = RenderTileManager.createExecutor(2, "Worker-TileSaver-IO");

    private final TileLayer tileLayer;

    @Nullable
    public DynamicTexture dynamicTexture;


    private final int minTileWorldX;
    private final int minTileWorldZ;

    private final int maxTileWorldX;
    private final int maxTileWorldZ;
    private final int size;
    private final int sampleRes;

    private boolean shouldRender = true;

    private final LongSet sampledChunks = new LongOpenHashSet();

    public SingleScreenTileLayer(DataTileManager tileManager, String name, TileLayer.GenerationFactory generationFactory, @Nullable TileLayer.DiskFactory diskFactory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, @Nullable SingleScreenTileLayer lastResolution) {
        this.minTileWorldX = minTileWorldX;
        this.minTileWorldZ = minTileWorldZ;

        this.maxTileWorldX = minTileWorldX + (size);
        this.maxTileWorldZ = minTileWorldZ + (size);
        String levelName = tileManager.serverLevel().getServer().getWorldData().getLevelName();
        this.size = size;

        Path imagePath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("image").resolve("p." + worldScreenv2.shiftingManager.blockToTile(minTileWorldX) + "-" + worldScreenv2.shiftingManager.blockToTile(minTileWorldZ) + "_s." + size + ".png");
        Path dataPath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("data").resolve("p." + worldScreenv2.shiftingManager.blockToTile(minTileWorldX) + "-" + worldScreenv2.shiftingManager.blockToTile(minTileWorldZ) + "_s." + size + ".dat");
        TileLayer tileLayer1 = null;

        if (lastResolution != null) {
            sampledChunks.addAll(lastResolution.sampledChunks);
        }

        if (lastResolution != null && !lastResolution.tileLayer.usesLod()) {
            tileLayer1 = lastResolution.tileLayer;
        } else {
            if (diskFactory != null) {
                try {
                    TileLayer fromDisk = diskFactory.fromDisk(size, imagePath, dataPath, sampleRes);
                    tileLayer1 = fromDisk;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            boolean nullTileLayer = tileLayer1 == null;


            if (nullTileLayer) {
                tileLayer1 = getTileLayer(tileManager, generationFactory, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, dataPath, imagePath);
            } else {
                boolean tileLayerComplete = tileLayer1.isComplete();
                boolean resolutionsDontMatch = tileLayer1.sampleRes() != worldScreenv2.sampleResolution;
                boolean usesLod = tileLayer1.usesLod();
                if (!tileLayerComplete || (usesLod && resolutionsDontMatch)) {
                    tileLayer1.close();
                    tileLayer1 = getTileLayer(tileManager, generationFactory, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, dataPath, imagePath);
                }
            }
        }
        this.tileLayer = tileLayer1;


        this.sampleRes = this.tileLayer.sampleRes();
    }

    private TileLayer getTileLayer(DataTileManager tileManager, TileLayer.GenerationFactory generationFactory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, Path dataPath, Path imagePath) {
        TileLayer tileLayer1 = generationFactory.make(tileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, sampledChunks);
        try {
            CompoundTag tag = tileLayer1.tag();
            if (tag != null) {
                ByteArrayDataOutput byteArrayDataOutput = ByteStreams.newDataOutput();
                NbtIo.write(tag, byteArrayDataOutput);
                FILE_SAVING_EXECUTOR_SERVICE.submit(() -> {
                    try {
                        File dataPathFile = dataPath.toFile();
                        if (dataPathFile.exists()) {
                            while (!dataPathFile.canWrite()) {
                                Thread.sleep(1);
                            }
                        } else {
                            Path parent = dataPath.getParent();
                            if (!parent.toFile().exists()) {
                                Files.createDirectories(parent);
                            }
                        }
                        Files.write(dataPath, byteArrayDataOutput.toByteArray());
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }

            NativeImage image = tileLayer1.image();
            if (image != null && ((NativeImageAccessor) (Object) image).wvGetPixels() != 0L) {
                byte[] imageByteArray = image.asByteArray();
                FILE_SAVING_EXECUTOR_SERVICE.submit(() -> {
                    try {
                        File imagePathFile = imagePath.toFile();
                        if (imagePathFile.exists()) {
                            while (!imagePathFile.canWrite()) {
                                Thread.sleep(1);
                            }
                        } else {
                            Path parent = imagePath.getParent();
                            if (!parent.toFile().exists()) {
                                Files.createDirectories(parent);
                            }
                        }
                        Files.write(imagePath, imageByteArray, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tileLayer1;
    }

    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY);
    }

    public void afterTilesRender(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2) {
        this.tileLayer.afterTilesRender(guiGraphics, opacity, getMinTileWorldX(), getMinTileWorldZ(), worldScreenv2);
    }

    public int getMinTileWorldX() {
        return minTileWorldX;
    }

    public int getMinTileWorldZ() {
        return minTileWorldZ;
    }

    @Override
    public int getMaxTileWorldX() {
        return this.maxTileWorldX;
    }

    @Override
    public int getMaxTileWorldZ() {
        return maxTileWorldZ;
    }

    @Override
    public void renderTile(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2) {
        if (shouldRender && this.tileLayer.image() != null) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.tileLayer.image());
            }
            renderer().render(guiGraphics, size, this.dynamicTexture.getId(), opacity, worldScreenv2);
        }
    }

    @Override
    public TileLayer.Renderer renderer() {
        return this.tileLayer.renderer();
    }

    @Override
    public NativeImage image() {
        return this.tileLayer.image();
    }

    @Override
    public int size() {
        return this.size;
    }

    public int getSampleRes() {
        return sampleRes;
    }

    public int getSize() {
        return size;
    }

    @Override
    public boolean sampleResCheck(int worldScreenSampleRes) {
        return this.sampleRes == worldScreenSampleRes;
    }

    @Override
    public boolean shouldRender() {
        return this.shouldRender;
    }

    @Override
    public void setShouldRender(boolean shouldRender) {
        this.shouldRender = shouldRender;
    }

    @Override
    public void closeDynamicTexture() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.close();
        }
    }

    @Override
    public void releaseDynamicTextureID() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.releaseId();
        }
    }

    @Override
    public void closeNativeImage() {
        this.tileLayer.close();
    }
}
