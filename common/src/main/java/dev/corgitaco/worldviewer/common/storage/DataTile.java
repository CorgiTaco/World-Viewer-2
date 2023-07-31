package dev.corgitaco.worldviewer.common.storage;


import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataTile {

    private static final int SIZE = 16;

    private final ConcurrentHashMap<Heightmap.Types, int[]> heights = Util.make(new ConcurrentHashMap<>(), map -> {
        for (Heightmap.Types value : Heightmap.Types.values()) {
            int[] heights = new int[SIZE * SIZE];
            Arrays.fill(heights, Integer.MIN_VALUE);
            map.put(value, heights);
        }
    });

    private final DataTileBiomeStorage biomes;

    private Set<Holder<Structure>> structures = null;

    private final long pos;
    private final DataTileManager manager;

    private final boolean isSlimeChunk;

    private boolean needsSaving = false;

    public DataTile(long pos, DataTileManager tileManager, CompoundTag tag) {
        this.pos = pos;
        this.manager = tileManager;
        {
            ListTag list = tag.getList("heights", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < list.size(); i++) {
                IntArrayTag intTags = (IntArrayTag) list.get(i);
                heights.put(Heightmap.Types.values()[i], intTags.getAsIntArray());
            }
        }
        {
            Registry<Biome> biomeRegistry = tileManager.serverLevel().registryAccess().registryOrThrow(Registries.BIOME);
            this.biomes = new DataTileBiomeStorage(tag.getCompound("biomes"), biomeRegistry);
        }
        {
            if (tag.contains("structures")) {
                this.structures = new ObjectOpenHashSet<>();
                ListTag structures = tag.getList("structures", Tag.TAG_STRING);
                for (Tag value : structures) {
                    StringTag structure = (StringTag) value;
                    Optional<Holder.Reference<Structure>> holder = tileManager.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(structure.getAsString())));
                    holder.ifPresent(configuredStructureFeatureHolder -> this.structures.add(configuredStructureFeatureHolder));
                }
            }
        }

        this.isSlimeChunk = tag.getBoolean("slimes");

    }


    public DataTile(long pos, DataTileManager manager) {
        this.pos = pos;
        this.manager = manager;
        this.biomes = new DataTileBiomeStorage();
        this.isSlimeChunk = manager.isSlimeChunkRaw(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        x = x & (SIZE - 1);
        z = z & (SIZE - 1);

        int[] heights = this.heights.get(type);
        int index = getIndex(x, z);
        if (heights != null || heights.length == 0) {
            heights = new int[SIZE * SIZE];
            Arrays.fill(heights, Integer.MIN_VALUE);
            this.heights.put(type, heights);
        }


        int height = heights[index];

        if (height == Integer.MIN_VALUE) {
            height = this.manager.getHeightRaw(type, toWorldX(x), toWorldZ(z));
            heights[index] = height;
            needsSaving = true;
        }

        return height;
    }

    public Set<Holder<Structure>> structures() {
        if (this.structures == null) {
            needsSaving = true;
            this.structures = this.manager.getStructuresRaw(this.pos);
        }
        return this.structures;
    }

    public Holder<Biome> getBiome(int x, int z) {
        int storageQuartX = x & (SIZE - 1);
        int storageQuartZ = z & (SIZE - 1);
        storageQuartX = QuartPos.fromBlock(storageQuartX);
        storageQuartZ = QuartPos.fromBlock(storageQuartZ);

        return this.biomes.getBiome(storageQuartX, storageQuartZ, x, z, (worldX, worldZ) -> {
            this.needsSaving = true;
            return this.manager.getBiomeRaw(worldX, worldZ);
        });
    }

    private static int getIndex(int x, int z) {
        return x + z * SIZE;
    }

    private static int getBiomeIndex(int x, int z) {
        return QuartPos.fromBlock(getIndex(x, z));
    }

    public long getPos() {
        return pos;
    }

    private int toWorldX(int x) {
        return SectionPos.sectionToBlockCoord(ChunkPos.getX(pos), x);
    }

    private int toWorldZ(int x) {
        return SectionPos.sectionToBlockCoord(ChunkPos.getZ(pos), x);
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();

        compoundTag.put("heights", saveHeights());
        compoundTag.put("biomes", saveBiomes());
        compoundTag.put("structures", saveStructures());

        compoundTag.putBoolean("slimes", this.isSlimeChunk);
        return compoundTag;
    }

    @NotNull
    private ListTag saveStructures() {
        ListTag tags = new ListTag();

        if (structures != null) {
            for (Holder<Structure> structure : this.structures) {
                tags.add(StringTag.valueOf(structure.unwrapKey().orElseThrow().location().toString()));
            }
        }
        return tags;
    }

    @NotNull
    private CompoundTag saveBiomes() {
        return biomes.save();
    }

    @NotNull
    private ListTag saveHeights() {
        ListTag heights = new ListTag();
        for (Heightmap.Types value : Heightmap.Types.values()) {
            heights.add(this.heights.containsKey(value) ? new IntArrayTag(this.heights.get(value)) : new IntArrayTag(new int[]{}));
        }
        return heights;
    }

    public boolean isSlimeChunk() {
        return isSlimeChunk;
    }

    public Set<Holder<Structure>> getStructures() {
        return structures;
    }

    public boolean isNeedsSaving() {
        return needsSaving;
    }

    public void setNeedsSaving(boolean needsSaving) {
        this.needsSaving = needsSaving;
    }
}
