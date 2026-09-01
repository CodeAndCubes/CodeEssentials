package com.mrleonardos.codeessentials.platform;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeessentials.api.teleport.BlockSample;
import com.mrleonardos.codeessentials.api.teleport.BlockView;

final class SafeSpotValidator implements BlockView {

    private final WorldServer world;
    private final boolean generate;
    private final Logger log;

    private int generated;

    SafeSpotValidator(WorldServer world, boolean generate, Logger log) {
        this.world = world;
        this.generate = generate;
        this.log = log;
    }

    @Override
    public int dimension() {
        return world.provider.dimensionId;
    }

    @Override
    public boolean chunkLoaded(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        IChunkProvider provider = world.getChunkProvider();
        if (provider.chunkExists(chunkX, chunkZ)) {
            return true;
        }
        if (!(provider instanceof ChunkProviderServer)) {
            return false;
        }
        ChunkProviderServer server = (ChunkProviderServer) provider;
        boolean saved = saved(server, chunkX, chunkZ);
        if (!saved && !generate) {
            return false;
        }
        server.loadChunk(chunkX, chunkZ);
        if (!provider.chunkExists(chunkX, chunkZ)) {
            return false;
        }
        if (!saved) {
            generated++;
            if (generated > 1) {
                log.warn(
                    "Chunk {},{} of dimension {} was generated on the spot, {} chunk(s) since the search started",
                    Integer.valueOf(chunkX),
                    Integer.valueOf(chunkZ),
                    Integer.valueOf(dimension()),
                    Integer.valueOf(generated));
            }
        }
        return true;
    }

    @Override
    public BlockSample sample(int blockX, int blockY, int blockZ) {
        Block block = world.getBlock(blockX, blockY, blockZ);
        Material material = block.getMaterial();
        boolean passable = !material.blocksMovement();
        boolean solid = block.isSideSolid(world, blockX, blockY, blockZ, ForgeDirection.UP);
        boolean harmful = material == Material.lava || material == Material.fire || block == Blocks.cactus;
        return BlockSample.of(passable, solid, harmful, material.isLiquid());
    }

    @Override
    public int height() {
        return world.getHeight();
    }

    private static boolean saved(ChunkProviderServer provider, int chunkX, int chunkZ) {
        if (!(provider.currentChunkLoader instanceof AnvilChunkLoader)) {
            return false;
        }
        return ((AnvilChunkLoader) provider.currentChunkLoader).chunkExists(provider.worldObj, chunkX, chunkZ);
    }
}
