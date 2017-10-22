package fi.dy.masa.placementpreview.fake;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class FakeChunk extends Chunk
{
    protected final World world;
    protected final IBlockState[] blockStorage = new IBlockState[4096];

    public FakeChunk(World world)
    {
        super(world, 0, 0);
        this.world = world;
        Arrays.fill(this.blockStorage, 0, this.blockStorage.length, Blocks.AIR.getDefaultState());
        this.markLoaded(true);
    }

    @Override
    public IBlockState getBlockState(int x, int y, int z)
    {
        return this.blockStorage[((y & 0xF) << 8) + ((z & 0xF) << 4) + (x & 0xF)];
    }

    @Override
    @Nullable
    public IBlockState setBlockState(BlockPos pos, IBlockState stateNew)
    {
        return this.setBlockState(pos, stateNew, true);
    }

    @Nullable
    public IBlockState setBlockState(BlockPos pos, IBlockState stateNew, boolean callHooks)
    {
        int x = pos.getX() & 0xF;
        int y = pos.getY() & 0xF;
        int z = pos.getZ() & 0xF;

        IBlockState stateOld = this.getBlockState(pos);
        Block blockNew = stateNew.getBlock();
        Block blockOld = stateOld.getBlock();

        this.blockStorage[(y << 8) + (z << 4) + x] = stateNew;

        if (callHooks && this.world.isRemote == false && blockNew != blockOld)
        {
            blockOld.breakBlock(this.world, pos, stateOld);
        }

        if (this.getTileEntity(pos, EnumCreateEntityType.CHECK) != null)
        {
            this.removeTileEntity(pos);
        }

        // If capturing blocks, only run block physics for TE's. Non-TE's are handled in ForgeHooks.onPlaceItemIntoWorld
        if (callHooks && this.world.isRemote == false && blockOld != blockNew &&
            (this.world.captureBlockSnapshots == false || blockNew.hasTileEntity(stateNew)))
        {
            blockNew.onBlockAdded(this.world, pos, stateNew);
        }

        if (blockNew.hasTileEntity(stateNew))
        {
            TileEntity te = blockNew.createTileEntity(this.world, stateNew);
            this.world.setTileEntity(pos, te);

            if (te != null)
            {
                te.updateContainingBlockInfo();
            }
        }

        return stateOld;
    }

    @Override
    public int getHeight(BlockPos pos)
    {
        return 16;
    }

    @Override
    public int getHeightValue(int x, int z)
    {
        return 16;
    }

    @Override
    public int getTopFilledSegment()
    {
        return 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void generateHeightMap()
    {
        // NO-OP
    }

    @Override
    public void generateSkylightMap()
    {
        // NO-OP
    }

    @Override
    public int getLightFor(EnumSkyBlock p_177413_1_, BlockPos pos)
    {
        return 15;
    }

    @Override
    public void setLightFor(EnumSkyBlock p_177431_1_, BlockPos pos, int value)
    {
        // NO-OP
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int amount)
    {
        return 15;
    }

    @Override
    public void addEntity(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void removeEntity(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void removeEntityAtIndex(Entity entityIn, int index)
    {
        // NO-OP
    }

    @Override
    public boolean canSeeSky(BlockPos pos)
    {
        return false;
    }

    @Override
    public void onUnload()
    {
        // NO-OP
    }

    @Override
    public void getEntitiesWithinAABBForEntity(Entity entityIn, AxisAlignedBB aabb, List<Entity> listToFill, Predicate<? super Entity> p_177414_4_)
    {
        // NO-OP
    }

    @Override
    public <T extends Entity> void getEntitiesOfTypeWithinAABB(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> p_177430_4_)
    {
        // NO-OP
    }

    @Override
    public boolean needsSaving(boolean p_76601_1_)
    {
        return false;
    }

    @Override
    public void populate(IChunkProvider chunkProvider, IChunkGenerator chunkGenrator)
    {
        // NO-OP
    }

    @Override
    protected void populate(IChunkGenerator generator)
    {
        // NO-OP
    }

    @Override
    public BlockPos getPrecipitationHeight(BlockPos pos)
    {
        return BlockPos.ORIGIN;
    }

    @Override
    public void onTick(boolean p_150804_1_)
    {
        // NO-OP
    }

    @Override
    public boolean isPopulated()
    {
        return true;
    }

    @Override
    public boolean wasTicked()
    {
        return true;
    }

    @Override
    public boolean isEmptyBetween(int startY, int endY)
    {
        return false;
    }

    @Override
    public void setStorageArrays(ExtendedBlockStorage[] newStorageArrays)
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void read(PacketBuffer buf, int p_186033_2_, boolean p_186033_3_)
    {
        // NO-OP
    }

    @Override
    public Biome getBiome(BlockPos pos, BiomeProvider provider)
    {
        return Biomes.PLAINS;
    }

    @Override
    public void enqueueRelightChecks()
    {
        // NO-OP
    }

    @Override
    public void checkLight()
    {
        // NO-OP
    }

    @Override
    public boolean isLoaded()
    {
        return true;
    }

    @Override
    public void setHeightMap(int[] newHeightMap)
    {
        // NO-OP
    }

    @Override
    public boolean isTerrainPopulated()
    {
        return true;
    }

    @Override
    public boolean isLightPopulated()
    {
        return true;
    }

    @Override
    public int getLowestHeight()
    {
        return 16;
    }
}
