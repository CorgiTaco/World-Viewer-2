package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class BiomeLayer extends TileLayer {

    private int[][] colorData;

    private NativeImage image;



    public BiomeLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
        this.colorData = buildImage(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks);
        this.image = makeNativeImageFromColorData(colorData);
    }

    private static int[][] buildImage(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks) {
        int sampledSize = size / sampleResolution;

        int[][] colorData = new int[sampledSize][sampledSize];
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                int worldX = tileWorldX + (sampleX * sampleResolution);
                int worldZ = tileWorldZ + (sampleZ * sampleResolution);
                worldPos.set(worldX, y, worldZ);

                sampledChunks.add(ChunkPos.asLong(worldPos));
                Holder<Biome> biomeHolder = tileManager.getBiome(worldX, worldZ);

                int dataX = sampleX;
                int dataZ = sampleZ;
                ResourceKey<Biome> biome = biomeHolder.unwrapKey().orElseThrow();
                colorData[dataX][dataZ] = _ARGBToABGR(FAST_COLORS.computeIfAbsent(biome, biomeResourceKey -> {
                    Biome value = biomeHolder.value();
                    float baseTemperature = value.getBaseTemperature();
                    float lerp = Mth.inverseLerp(baseTemperature, -2, 2);
                    int r = (int) Mth.clampedLerp(137, 139, lerp);
                    int g = (int) Mth.clampedLerp(207, 0, lerp);
                    int b = (int) Mth.clampedLerp(240, 0, lerp);

                    return FastColor.ARGB32.color(255, r, g, b);
                }));

            }
        }
        return colorData;
    }

    public static final Object2IntOpenHashMap<ResourceKey<Biome>> FAST_COLORS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put(Biomes.BADLANDS, tryParseColor("0xD94515"));
        map.put(Biomes.BAMBOO_JUNGLE, tryParseColor("0x2C4205"));
        map.put(Biomes.BEACH, tryParseColor("0xFADE55"));
        map.put(Biomes.DESERT, tryParseColor("0xFA9418"));
        map.put(Biomes.BIRCH_FOREST, tryParseColor("0x307444"));
        map.put(Biomes.PLAINS, tryParseColor("0x8DB360"));
        map.put(Biomes.WINDSWEPT_HILLS, tryParseColor("0x606060"));
        map.put(Biomes.FOREST, tryParseColor("0x056621"));
        map.put(Biomes.TAIGA, tryParseColor("0x0B6659"));
        map.put(Biomes.SWAMP, tryParseColor("0x07F9B2"));
        map.put(Biomes.RIVER, tryParseColor("0x0000FF"));
        map.put(Biomes.NETHER_WASTES, tryParseColor("0xFF0000"));
        map.put(Biomes.THE_VOID, tryParseColor("0x8080FF"));
        map.put(Biomes.FROZEN_RIVER, tryParseColor("0xA0A0A0"));
        map.put(Biomes.SNOWY_PLAINS, tryParseColor("0xFFFFFF"));
        map.put(Biomes.MUSHROOM_FIELDS, tryParseColor("0xFF00FF"));
        map.put(Biomes.JUNGLE, tryParseColor("0x537B09"));
        map.put(Biomes.SPARSE_JUNGLE, tryParseColor("0x628B17"));
        map.put(Biomes.STONY_SHORE, tryParseColor("0xA2A284"));
        map.put(Biomes.SNOWY_BEACH, tryParseColor("0xFAF0C0"));
        map.put(Biomes.DARK_FOREST, tryParseColor("0x40511A"));
        map.put(Biomes.SNOWY_TAIGA, tryParseColor("0x31554A"));
        map.put(Biomes.OLD_GROWTH_PINE_TAIGA, tryParseColor("0x596651"));
        map.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, tryParseColor("0x818E79"));
        map.put(Biomes.SAVANNA, tryParseColor("0xBDB25F"));
        map.put(Biomes.SAVANNA_PLATEAU, tryParseColor("0xA79D24"));
        map.put(Biomes.WOODED_BADLANDS, tryParseColor("0xCA8C65"));
        map.put(Biomes.ERODED_BADLANDS, tryParseColor("0xFF6D3D"));
        map.put(Biomes.SUNFLOWER_PLAINS, tryParseColor("0xB5DB88"));
        map.put(Biomes.FLOWER_FOREST, tryParseColor("0x2D8E49"));
        map.put(Biomes.ICE_SPIKES, tryParseColor("0xB4DCDC"));
        map.put(Biomes.OCEAN, tryParseColor("0x000070"));
        map.put(Biomes.DEEP_OCEAN, tryParseColor("0x000030"));
        map.put(Biomes.COLD_OCEAN, tryParseColor("0x0056d6"));
        map.put(Biomes.DEEP_COLD_OCEAN, tryParseColor("0x004ecc"));
        map.put(Biomes.LUKEWARM_OCEAN, tryParseColor("0x45ADF2"));
        map.put(Biomes.DEEP_LUKEWARM_OCEAN, tryParseColor("0x3BA3E8"));
        map.put(Biomes.FROZEN_OCEAN, tryParseColor("0x9090A0"));
        map.put(Biomes.DEEP_FROZEN_OCEAN, tryParseColor("0x676791"));
        map.put(Biomes.FROZEN_PEAKS, tryParseColor("0x8BC0FC"));
        map.put(Biomes.WINDSWEPT_SAVANNA, tryParseColor("0xE5DA87"));
        map.put(Biomes.WINDSWEPT_FOREST, tryParseColor("0x589C6C"));
        map.put(Biomes.STONY_PEAKS, tryParseColor("0xC0C0C0"));
        map.put(Biomes.JAGGED_PEAKS, tryParseColor("0x969696"));
        map.put(Biomes.GROVE, tryParseColor("0x42FFBa"));
        map.put(Biomes.SOUL_SAND_VALLEY, tryParseColor("0x964B00"));
        map.put(Biomes.WARPED_FOREST, tryParseColor("0x89cff0"));
        map.put(Biomes.BASALT_DELTAS, tryParseColor("0x5A5A5A"));
        map.put(Biomes.CRIMSON_FOREST, tryParseColor("0xDC143C"));
    });


    public static int tryParseColor(String input) {
        int result = Integer.MAX_VALUE;

        if (input.isEmpty()) {
            return result;
        }

        try {
            String colorSubString = input.substring(2);
            result = (255 << 24) | (Integer.parseInt(colorSubString, 16));

            return result;
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static int _ARGBToABGR(int argb) {
        int r = FastColor.ARGB32.red(argb);
        int g = FastColor.ARGB32.green(argb);
        int b = FastColor.ARGB32.blue(argb);
        int a = FastColor.ARGB32.alpha(argb);

        return ClientUtil.combineNative(a, b, g, r);
    }

    public static int _ABGRToARGB(int abgr) {
        int a = ClientUtil.getNativeA(abgr);
        int b = ClientUtil.getNativeB(abgr);
        int g = ClientUtil.getNativeG(abgr);
        int r = ClientUtil.getNativeR(abgr);
        return FastColor.ARGB32.color(a, r, g, b);
    }
    @Override
    public NativeImage image() {
        return this.image;
    }
}
