package fi.dy.masa.placementpreview.fake;

import java.io.File;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.SpawnListEntry;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.placementpreview.PlacementPreview;

public class FakeWorld extends WorldServer
{
    protected final World parent;
    protected final FakeChunk chunk;
    protected final ChunkProviderServer chunkProvider;
    protected final Set<BlockPos> setPositions = new HashSet<BlockPos>();
    protected boolean storePositions;

    public FakeWorld(MinecraftServer server, World parent)
    {
        //super(null, parent.getWorldInfo(), new WorldProviderSurface(), null, false);
        //super(new FakeServer(Minecraft.getMinecraft(), "fake", "fake", new WorldSettings(parent.getWorldInfo()), null, null, null, null),
        //        null, parent.getWorldInfo(), 1, parent.theProfiler);
        super(server, server.getActiveAnvilConverter().getSaveLoader("pp_fake", false), parent.getWorldInfo(), PlacementPreview.dimId, parent.theProfiler);

        this.parent = parent;
        this.chunk = new FakeChunk(this);
        this.chunkProvider = this.createChunkProvider();
        this.provider.registerWorld(this);
        this.provider.setDimension(PlacementPreview.dimId);
        this.mapStorage = new MapStorage(this.saveHandler);
        this.perWorldStorage = new MapStorage((ISaveHandler) null);
    }

    @Override
    public ChunkProviderServer getChunkProvider()
    {
        return this.chunkProvider;
    }

    @Override
    protected ChunkProviderServer createChunkProvider()
    {
        return new FakeChunkProvider(this);
    }

    @Override
    public Biome getBiome(BlockPos pos)
    {
        return this.parent.getBiome(pos);
    }

    @Override
    public Biome getBiomeForCoordsBody(final BlockPos pos)
    {
        return this.parent.getBiomeForCoordsBody(pos);
    }

    @Override
    public BiomeProvider getBiomeProvider()
    {
        return this.parent.getBiomeProvider();
    }

    @Override
    public Chunk getChunkFromBlockCoords(BlockPos pos)
    {
        return this.chunk;
    }

    @Override
    public Chunk getChunkFromChunkCoords(int chunkX, int chunkZ)
    {
        return this.chunk;
    }

    private boolean isOutsideBuildHeight(BlockPos pos)
    {
        return pos.getY() < 0 || pos.getY() >= 256;
    }

    public void setStorePositions(boolean store)
    {
        this.storePositions = store;
    }

    public void clearPositions()
    {
        this.setPositions.clear();
    }

    public Collection<BlockPos> getChangedPositions()
    {
        return this.setPositions;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags)
    {
        return this.setBlockState(pos, newState, flags, true);
    }

    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags, boolean callHooks)
    {
        if (this.isOutsideBuildHeight(pos))
        {
            return false;
        }
        else if (this.isRemote == false && this.worldInfo.getTerrainType() == WorldType.DEBUG_WORLD)
        {
            return false;
        }
        else
        {
            net.minecraftforge.common.util.BlockSnapshot blockSnapshot = null;
            if (this.captureBlockSnapshots && this.isRemote == false)
            {
                blockSnapshot = net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(this, pos, flags);
                this.capturedBlockSnapshots.add(blockSnapshot);
            }

            IBlockState state = this.chunk.setBlockState(pos, newState, callHooks);

            if (this.storePositions)
            {
                this.setPositions.add(pos);
            }

            if (state == null)
            {
                if (blockSnapshot != null)
                {
                    this.capturedBlockSnapshots.remove(blockSnapshot);
                }

                return false;
            }
            else
            {
                if (blockSnapshot == null)
                {
                    this.markAndNotifyBlock(pos, this.chunk, state, newState, flags);
                }

                return true;
            }
        }
    }

    @Override
    public void markAndNotifyBlock(BlockPos pos, Chunk chunk, IBlockState iblockstate, IBlockState newState, int flags)
    {
        if (this.isRemote == false && (flags & 1) != 0)
        {
            this.notifyNeighborsRespectDebug(pos, iblockstate.getBlock());

            if (newState.hasComparatorInputOverride())
            {
                this.updateComparatorOutputLevel(pos, newState.getBlock());
            }
        }
    }

    @Override
    public void notifyBlockUpdate(BlockPos pos, IBlockState oldState, IBlockState newState, int flags)
    {
        // NO-OP
    }

    /*@Override
    public void notifyNeighborsOfStateChange(BlockPos pos, Block blockType)
    {
        // NO-OP
    }

    @Override
    public void notifyNeighborsOfStateExcept(BlockPos pos, Block blockType, EnumFacing skipSide)
    {
        // NO-OP
    }

    @Override
    public void notifyBlockOfStateChange(BlockPos pos, final Block blockIn)
    {
        // NO-OP
    }*/

    @Override
    public void markBlocksDirtyVertical(int x1, int z1, int x2, int z2)
    {
        // NO-OP
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2)
    {
        // NO-OP
    }

    @Override
    public boolean canSeeSky(BlockPos pos)
    {
        return this.parent.canSeeSky(pos);
    }

    @Override
    public boolean canBlockSeeSky(BlockPos pos)
    {
        return this.parent.canBlockSeeSky(pos);
    }

    @Override
    public int getLight(BlockPos pos)
    {
        return this.parent.getLight(pos);
    }

    @Override
    public int getLight(BlockPos pos, boolean checkNeighbors)
    {
        return this.parent.getLight(pos, checkNeighbors);
    }

    @Override
    public BlockPos getHeight(BlockPos pos)
    {
        return this.parent.getHeight(pos);
    }

    @Override
    public int getLightFor(EnumSkyBlock type, BlockPos pos)
    {
        return this.parent.getLightFor(type, pos);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos)
    {
        return this.parent.getLightFromNeighborsFor(type, pos);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getCombinedLight(BlockPos pos, int lightValue)
    {
        return this.parent.getCombinedLight(pos, lightValue);
    }

    @Override
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int lightValue)
    {
        // NO-OP
    }

    @Override
    public void notifyLightSet(BlockPos pos)
    {
        // NO-OP
    }

    @Override
    public float getLightBrightness(BlockPos pos)
    {
        return this.parent.getLightBrightness(pos);
    }

    @Override
    public boolean isDaytime()
    {
        return this.parent.isDaytime();
    }

    @Override
    public RayTraceResult rayTraceBlocks(Vec3d vec31, Vec3d vec32, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock)
    {
        return this.parent.rayTraceBlocks(vec31, vec32, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
    }

    @Override
    public void playSound(@Nullable EntityPlayer player, BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playSound(@Nullable EntityPlayer player, double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume, float pitch)
    {
        // NO-OP
    }

    @Override
    public void playRecord(BlockPos blockPositionIn, @Nullable SoundEvent soundEventIn)
    {
        // NO-OP
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters)
    {
        this.spawnParticle(particleType.getParticleID(), particleType.getShouldIgnoreRange(), xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void spawnParticle(EnumParticleTypes particleType, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters)
    {
        this.spawnParticle(particleType.getParticleID(), particleType.getShouldIgnoreRange() | ignoreRange, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);
    }

    private void spawnParticle(int particleID, boolean ignoreRange, double xCood, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters)
    {
        // NO-OP
    }

    @Override
    public boolean addWeatherEffect(Entity entityIn)
    {
        // NO-OP
        return true;
    }

    @Override
    public boolean spawnEntityInWorld(Entity entityIn)
    {
        // NO-OP
        return false;
    }

    @Override
    public void onEntityAdded(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void onEntityRemoved(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void removeEntity(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void removeEntityDangerously(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public void addEventListener(IWorldEventListener listener)
    {
        // NO-OP
    }

    @Override
    public void removeEventListener(IWorldEventListener listener)
    {
        // NO-OP
    }

    @Override
    public boolean isInsideBorder(WorldBorder worldBorderIn, Entity entityIn)
    {
        return this.parent.isInsideBorder(worldBorderIn, entityIn);
    }

    @Override
    public List<AxisAlignedBB> getCollisionBoxes(@Nullable Entity entityIn, AxisAlignedBB aabb)
    {
        return this.parent.getCollisionBoxes(entityIn, aabb);
    }

    @Override
    public List<AxisAlignedBB> getCollisionBoxes(AxisAlignedBB bb)
    {
        return this.parent.getCollisionBoxes(bb);
    }

    @Override
    public boolean collidesWithAnyBlock(AxisAlignedBB bbox)
    {
        return this.parent.collidesWithAnyBlock(bbox);
    }

    @Override
    public int calculateSkylightSubtracted(float partialTicks)
    {
        return this.parent.calculateSkylightSubtracted(partialTicks);
    }

    @Override
    public float getSunBrightnessFactor(float partialTicks)
    {
        return this.parent.getSunBrightnessFactor(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getSunBrightness(float p_72971_1_)
    {
        return this.parent.getSunBrightness(p_72971_1_);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getSunBrightnessBody(float p_72971_1_)
    {
        return this.parent.getSunBrightnessBody(p_72971_1_);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getSkyColor(Entity entityIn, float partialTicks)
    {
        return this.parent.getSkyColor(entityIn, partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getSkyColorBody(Entity entityIn, float partialTicks)
    {
        return this.parent.getSkyColorBody(entityIn, partialTicks);
    }

    @Override
    public float getCelestialAngle(float partialTicks)
    {
        return this.parent.getCelestialAngle(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getMoonPhase()
    {
        return this.parent.getMoonPhase();
    }

    @Override
    public float getCurrentMoonPhaseFactor()
    {
        return this.parent.getCurrentMoonPhaseFactor();
    }

    @Override
    public float getCurrentMoonPhaseFactorBody()
    {
        return this.parent.getCurrentMoonPhaseFactorBody();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getCloudColour(float partialTicks)
    {
        return this.parent.getCloudColour(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getCloudColorBody(float partialTicks)
    {
        return this.parent.getCloudColorBody(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float partialTicks)
    {
        return this.parent.getFogColor(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getStarBrightness(float partialTicks)
    {
        return this.parent.getStarBrightness(partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getStarBrightnessBody(float partialTicks)
    {
        return this.parent.getStarBrightnessBody(partialTicks);
    }

    @Override
    public BlockPos getPrecipitationHeight(BlockPos pos)
    {
        return this.parent.getPrecipitationHeight(pos);
    }

    @Override
    public BlockPos getTopSolidOrLiquidBlock(BlockPos pos)
    {
        return this.parent.getTopSolidOrLiquidBlock(pos);
    }

    @Override
    public void updateEntities()
    {
        // NO-OP
    }

    @Override
    public void updateEntity(Entity ent)
    {
        // NO-OP
    }

    @Override
    public void updateEntityWithOptionalForce(Entity entityIn, boolean forceUpdate)
    {
        // NO-OP
    }

    @Override
    public boolean addTileEntity(TileEntity tile)
    {
        return false;
    }

    @Override
    public void addTileEntities(Collection<TileEntity> tileEntityCollection)
    {
        // NO-OP
    }

    @Override
    public void setTileEntity(BlockPos pos, @Nullable TileEntity tileEntityIn)
    {
        if (this.isOutsideBuildHeight(pos))
        {
            return;
        }

        pos = pos.toImmutable();

        if (tileEntityIn != null && tileEntityIn.isInvalid() == false)
        {
            this.chunk.addTileEntity(pos, tileEntityIn);
            //this.updateComparatorOutputLevel(pos, getBlockState(pos).getBlock()); //Notify neighbors of changes
        }
    }

    @Override
    public void removeTileEntity(BlockPos pos)
    {
        this.chunk.removeTileEntity(pos);
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos)
    {
        if (this.isOutsideBuildHeight(pos))
        {
            return null;
        }
        else
        {
            return this.chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.IMMEDIATE);
        }
    }

    @Override
    public void markTileEntityForRemoval(TileEntity tileEntityIn)
    {
        // NO-OP
    }

    @Override
    public boolean checkNoEntityCollision(AxisAlignedBB bb, @Nullable Entity entityIn)
    {
        return this.parent.checkNoEntityCollision(bb, entityIn);
    }

    @Override
    public boolean checkBlockCollision(AxisAlignedBB bb)
    {
        return this.parent.checkBlockCollision(bb);
    }

    @Override
    public boolean containsAnyLiquid(AxisAlignedBB bb)
    {
        return this.parent.containsAnyLiquid(bb);
    }

    @Override
    public boolean isFlammableWithin(AxisAlignedBB bb)
    {
        return this.parent.isFlammableWithin(bb);
    }

    @Override
    public boolean handleMaterialAcceleration(AxisAlignedBB bb, Material materialIn, Entity entityIn)
    {
        return false;
    }

    @Override
    public boolean isMaterialInBB(AxisAlignedBB bb, Material materialIn)
    {
        return this.parent.isMaterialInBB(bb, materialIn);
    }

    @Override
    public boolean isAABBInMaterial(AxisAlignedBB bb, Material materialIn)
    {
        return this.parent.isAABBInMaterial(bb, materialIn);
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entityIn, double x, double y, double z, float strength, boolean isSmoking)
    {
        return null;
    }

    @Override
    public Explosion newExplosion(@Nullable Entity entityIn, double x, double y, double z, float strength, boolean isFlaming, boolean isSmoking)
    {
        return null;
    }

    @Override
    public float getBlockDensity(Vec3d vec, AxisAlignedBB bb)
    {
        return this.parent.getBlockDensity(vec, bb);
    }

    @Override
    public boolean extinguishFire(@Nullable EntityPlayer player, BlockPos pos, EnumFacing side)
    {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public String getProviderName()
    {
        return "FakeWorld";
    }

    @Override
    public boolean isBlockNormalCube(BlockPos pos, boolean _default)
    {
        return this.parent.isBlockNormalCube(pos, _default);
    }

    @Override
    public void calculateInitialSkylight()
    {
        // NO-OP
    }

    @Override
    public void setAllowedSpawnTypes(boolean hostile, boolean peaceful)
    {
        // NO-OP
    }

    @Override
    public void tick()
    {
        // NO-OP
    }

    @Override
    protected void calculateInitialWeather()
    {
        // NO-OP
    }

    @Override
    public void calculateInitialWeatherBody()
    {
        // NO-OP
    }

    @Override
    protected void updateWeather()
    {
        // NO-OP
    }

    @Override
    public void updateWeatherBody()
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void playMoodSoundAndCheckLight(int p_147467_1_, int p_147467_2_, Chunk chunkIn)
    {
        // NO-OP
    }

    @Override
    public void immediateBlockTick(BlockPos pos, IBlockState state, Random random)
    {
        // NO-OP
    }

    @Override
    public boolean canBlockFreeze(BlockPos pos, boolean noWaterAdj)
    {
        return false;
    }

    @Override
    public boolean canBlockFreezeBody(BlockPos pos, boolean noWaterAdj)
    {
        return false;
    }

    @Override
    public boolean canSnowAt(BlockPos pos, boolean checkLight)
    {
        return false;
    }

    @Override
    public boolean canSnowAtBody(BlockPos pos, boolean checkLight)
    {
        return false;
    }

    @Override
    public boolean checkLight(BlockPos pos)
    {
        return false;
    }

    @Override
    public boolean checkLightFor(EnumSkyBlock lightType, BlockPos pos)
    {
        return false;
    }

    @Override
    public List<Entity> getEntitiesWithinAABBExcludingEntity(@Nullable Entity entityIn, AxisAlignedBB bb)
    {
        return Lists.<Entity>newArrayList();
    }

    @Override
    public List<Entity> getEntitiesInAABBexcluding(@Nullable Entity entityIn, AxisAlignedBB boundingBox, @Nullable Predicate <? super Entity > predicate)
    {
        return Lists.<Entity>newArrayList();
    }

    @Override
    public <T extends Entity> List<T> getEntities(Class <? extends T > entityType, Predicate <? super T > filter)
    {
        return Lists.<T>newArrayList();
    }

    @Override
    public <T extends Entity> List<T> getPlayers(Class <? extends T > playerType, Predicate <? super T > filter)
    {
        return Lists.<T>newArrayList();
    }

    @Override
    public <T extends Entity> List<T> getEntitiesWithinAABB(Class <? extends T > classEntity, AxisAlignedBB bb)
    {
        return Lists.<T>newArrayList();
    }

    @Override
    public <T extends Entity> List<T> getEntitiesWithinAABB(Class <? extends T > clazz, AxisAlignedBB aabb, @Nullable Predicate <? super T > filter)
    {
        return Lists.<T>newArrayList();
    }

    @Override
    @Nullable
    public <T extends Entity> T findNearestEntityWithinAABB(Class <? extends T > entityType, AxisAlignedBB aabb, T closestTo)
    {
        return null;
    }

    @Override
    @Nullable
    public Entity getEntityByID(int id)
    {
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public List<Entity> getLoadedEntityList()
    {
        return Lists.<Entity>newArrayList();
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity unusedTileEntity)
    {
        // NO-OP
    }

    @Override
    public int countEntities(Class<?> entityType)
    {
        return 0;
    }

    @Override
    public void loadEntities(Collection<Entity> entityCollection)
    {
        // NO-OP
    }

    @Override
    public void unloadEntities(Collection<Entity> entityCollection)
    {
        // NO-OP
    }

    @Override
    public boolean canBlockBePlaced(Block blockIn, BlockPos pos, boolean p_175716_3_, EnumFacing side, @Nullable Entity entityIn, @Nullable ItemStack itemStackIn)
    {
        return this.parent.canBlockBePlaced(blockIn, pos, p_175716_3_, side, entityIn, itemStackIn);
    }

    @Override
    public WorldType getWorldType()
    {
        return this.parent.getWorldType();
    }

    @Override
    @Nullable
    public EntityPlayer getClosestPlayerToEntity(Entity entityIn, double distance)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getNearestPlayerNotCreative(Entity entityIn, double distance)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getClosestPlayer(double posX, double posY, double posZ, double distance, boolean spectator)
    {
        return null;
    }

    @Override
    public boolean isAnyPlayerWithinRangeAt(double x, double y, double z, double range)
    {
        return false;
    }

    @Override
    @Nullable
    public EntityPlayer getNearestAttackablePlayer(Entity entityIn, double maxXZDistance, double maxYDistance)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getNearestAttackablePlayer(BlockPos pos, double maxXZDistance, double maxYDistance)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getNearestAttackablePlayer(double posX, double posY, double posZ, double maxXZDistance, double maxYDistance, @Nullable Function<EntityPlayer, Double> playerToDouble, @Nullable Predicate<EntityPlayer> p_184150_12_)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getPlayerEntityByName(String name)
    {
        return null;
    }

    @Override
    @Nullable
    public EntityPlayer getPlayerEntityByUUID(UUID uuid)
    {
        return null;
    }

    @Override
    public void checkSessionLock() throws MinecraftException
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void setTotalWorldTime(long worldTime)
    {
        // NO-OP
    }

    @Override
    public long getSeed()
    {
        return 0;
    }

    @Override
    public long getTotalWorldTime()
    {
        return this.parent.getTotalWorldTime();
    }

    @Override
    public long getWorldTime()
    {
        return this.parent.getWorldTime();
    }

    @Override
    public void setWorldTime(long time)
    {
        // NO-OP
    }

    @Override
    public BlockPos getSpawnPoint()
    {
        return this.parent.getSpawnPoint();
    }

    @Override
    public void setSpawnPoint(BlockPos pos)
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void joinEntityInSurroundings(Entity entityIn)
    {
        // NO-OP
    }

    @Override
    public boolean isBlockModifiable(EntityPlayer player, BlockPos pos)
    {
        return true;
    }

    @Override
    public void addBlockEvent(BlockPos pos, Block blockIn, int eventID, int eventParam)
    {
        // NO-OP
    }

    /*@Override
    public ISaveHandler getSaveHandler()
    {
        return null;
    }*/

    @Override
    public WorldInfo getWorldInfo()
    {
        // ;_; ffs the hacks keep piling up...
        // Without this the game crashes here with an NPE, via WorldServer constructor
        if (this.parent == null)
        {
            return new WorldInfo(new NBTTagCompound());
        }

        return this.parent.getWorldInfo();
    }

    @Override
    public GameRules getGameRules()
    {
        return this.parent.getGameRules();
    }

    @Override
    public float getThunderStrength(float delta)
    {
        return this.parent.getThunderStrength(delta);
    }

    @Override
    public float getRainStrength(float delta)
    {
        return this.parent.getRainStrength(delta);
    }

    @Override
    public boolean isRainingAt(BlockPos strikePosition)
    {
        return this.parent.isRainingAt(strikePosition);
    }

    @SideOnly(Side.CLIENT)
    public void setThunderStrength(float strength)
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void setRainStrength(float strength)
    {
        // NO-OP
    }

    @Override
    public boolean isThundering()
    {
        return this.parent.isThundering();
    }

    @Override
    public boolean isRaining()
    {
        return this.parent.isRaining();
    }

    @Override
    public boolean isBlockinHighHumidity(BlockPos pos)
    {
        return this.parent.isBlockinHighHumidity(pos);
    }

    /*@Override
    @Nullable
    public MapStorage getMapStorage()
    {
        return null;
    }*/

    @Override
    public void setItemData(String dataID, WorldSavedData worldSavedDataIn)
    {
        // NO-OP
    }

    @Override
    @Nullable
    public WorldSavedData loadItemData(Class <? extends WorldSavedData > clazz, String dataID)
    {
        return null;
    }

    @Override
    public int getUniqueDataId(String key)
    {
        return 0;
    }

    @Override
    public void playBroadcastSound(int id, BlockPos pos, int data)
    {
        // NO-OP
    }

    @Override
    public void playEvent(int type, BlockPos pos, int data)
    {
        // NO-OP
    }

    @Override
    public void playEvent(@Nullable EntityPlayer player, int type, BlockPos pos, int data)
    {
        // NO-OP
    }

    @Override
    public int getHeight()
    {
        return this.parent.getHeight();
    }

    @Override
    public int getActualHeight()
    {
        return this.parent.getActualHeight();
    }

    @Override
    public Random setRandomSeed(int p_72843_1_, int p_72843_2_, int p_72843_3_)
    {
        // NO-OP
        return this.rand;
    }

    @Override
    public CrashReportCategory addWorldInfoToCrashReport(CrashReport report)
    {
        CrashReportCategory crashreportcategory = report.makeCategoryDepth("Affected level", 1);
        crashreportcategory.addCrashSection("Level name", this.worldInfo == null ? "????" : this.worldInfo.getWorldName());

        try
        {
            this.worldInfo.addToCrashReport(crashreportcategory);
        }
        catch (Throwable throwable)
        {
            crashreportcategory.addCrashSectionThrowable("Level Data Unobtainable", throwable);
        }

        return crashreportcategory;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public double getHorizon()
    {
        return this.parent.getHorizon();
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress)
    {
        // NO-OP
    }

    @Override
    public Calendar getCurrentDate()
    {
        return this.parent.getCurrentDate();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void makeFireworks(double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable NBTTagCompound compund)
    {
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return this.parent.getScoreboard();
    }

    @Override
    public void updateComparatorOutputLevel(BlockPos pos, Block blockIn)
    {
        // NO-OP
    }

    @Override
    public DifficultyInstance getDifficultyForLocation(BlockPos pos)
    {
        return this.parent.getDifficultyForLocation(pos);
    }

    @Override
    public EnumDifficulty getDifficulty()
    {
        return this.parent.getDifficulty();
    }

    @Override
    public int getSkylightSubtracted()
    {
        return this.parent.getSkylightSubtracted();
    }

    @Override
    public void setSkylightSubtracted(int newSkylightSubtracted)
    {
        // NO-OP
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getLastLightningBolt()
    {
        return this.parent.getLastLightningBolt();
    }

    @Override
    public void setLastLightningBolt(int lastLightningBoltIn)
    {
        // NO-OP;
    }

    @Override
    public VillageCollection getVillageCollection()
    {
        return this.parent.getVillageCollection();
    }

    @Override
    public WorldBorder getWorldBorder()
    {
        return new WorldBorder();
    }

    @Override
    public boolean isSpawnChunk(int x, int z)
    {
        return this.parent.isSpawnChunk(x, z);
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default)
    {
        return this.parent.isSideSolid(pos, side, _default);
    }

    @Override
    public ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> getPersistentChunks()
    {
        return ImmutableSetMultimap.<ChunkPos, ForgeChunkManager.Ticket>of();
    }

    /*@Override
    public Iterator<Chunk> getPersistentChunkIterable(Iterator<Chunk> chunkIterator)
    {
        return ForgeChunkManager.getPersistentChunksIterableFor(this, chunkIterator);
    }*/

    @Override
    public int getBlockLightOpacity(BlockPos pos)
    {
        return this.parent.getBlockLightOpacity(pos);
    }

    @Override
    public int countEntities(net.minecraft.entity.EnumCreatureType type, boolean forSpawnCount)
    {
        return 0;
    }

    /*@Override
    public MapStorage getPerWorldStorage()
    {
        return this.perWorldStorage;
    }*/

    @Override
    public void sendPacketToServer(Packet<?> packetIn)
    {
        // NO-OP;
    }

    @Override
    public LootTableManager getLootTableManager()
    {
        return this.parent.getLootTableManager();
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty)
    {
        return true;
    }

    /***************** WorldServer *********************/

    @Override
    public World init()
    {
        return this;
    }

    @Override
    protected void tickPlayers()
    {
        // NO-OP
    }

    @Override
    @Nullable
    public SpawnListEntry getSpawnListEntryForTypeAt(EnumCreatureType creatureType, BlockPos pos)
    {
        return null;
    }

    @Override
    public boolean canCreatureTypeSpawnHere(EnumCreatureType creatureType, SpawnListEntry spawnListEntry, BlockPos pos)
    {
        return false;
    }

    @Override
    public void updateAllPlayersSleepingFlag()
    {
        // NO-OP
    }

    @Override
    protected void wakeAllPlayers()
    {
        // NO-OP
    }

    @Override
    public boolean areAllPlayersAsleep()
    {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void setInitialSpawnLocation()
    {
        // NO-OP
    }

    @Override
    protected void playerCheckLight()
    {
        // NO-OP
    }

    @Override
    protected void updateBlocks()
    {
        // NO-OP
    }

    @Override
    protected BlockPos adjustPosToNearbyEntity(BlockPos pos)
    {
        return BlockPos.ORIGIN;
    }

    @Override
    public boolean isBlockTickPending(BlockPos pos, Block blockType)
    {
        return false;
    }

    @Override
    public boolean isUpdateScheduled(BlockPos pos, Block blk)
    {
        return false;
    }

    @Override
    public void scheduleUpdate(BlockPos pos, Block blockIn, int delay)
    {
        // NO-OP
    }

    @Override
    public void updateBlockTick(BlockPos pos, Block blockIn, int delay, int priority)
    {
        // NO-OP
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos, Block blockIn, int delay, int priority)
    {
        // NO-OP
    }

    @Override
    public void resetUpdateEntityTick()
    {
        // NO-OP
    }

    @Override
    public boolean tickUpdates(boolean p_72955_1_)
    {
        return false;
    }

    @Override
    @Nullable
    public List<NextTickListEntry> getPendingBlockUpdates(Chunk chunkIn, boolean p_72920_2_)
    {
        return null;
    }

    @Override
    @Nullable
    public List<NextTickListEntry> getPendingBlockUpdates(StructureBoundingBox structureBB, boolean p_175712_2_)
    {
        return null;
    }

    @Override
    public boolean canMineBlockBody(EntityPlayer player, BlockPos pos)
    {
        return true;
    }

    @Override
    public void initialize(WorldSettings settings)
    {
        // NO-OP
    }

    @Override
    protected void createBonusChest()
    {
        // NO-OP
    }

    @Override
    public BlockPos getSpawnCoordinate()
    {
        return BlockPos.ORIGIN;
    }

    @Override
    public void saveAllChunks(boolean p_73044_1_, IProgressUpdate progressCallback) throws MinecraftException
    {
        // NO-OP
    }

    @Override
    public void saveChunkData()
    {
        // NO-OP
    }

    @Override
    protected void saveLevel() throws MinecraftException
    {
        // NO-OP
    }

    @Override
    public void setEntityState(Entity entityIn, byte state)
    {
        // NO-OP
    }

    @Override
    public void flush()
    {
        // NO-OP
    }

    @Override
    @Nullable
    public MinecraftServer getMinecraftServer()
    {
        return PlacementPreview.fakeServer;
    }

    /*@Override
    public EntityTracker getEntityTracker()
    {
        return super.getEntityTracker();
    }

    @Override
    public PlayerChunkMap getPlayerChunkMap()
    {
        return super.getPlayerChunkMap();
    }*/

    /*@Override
    public Teleporter getDefaultTeleporter()
    {
        return super.getDefaultTeleporter();
    }*/

    @Override
    public TemplateManager getStructureTemplateManager()
    {
        return null;
    }

    @Override
    public void spawnParticle(EntityPlayerMP player, EnumParticleTypes particle, boolean longDistance, double x,
            double y, double z, int count, double xOffset, double yOffset, double zOffset, double speed, int... arguments)
    {
        // NO-OP
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, boolean longDistance, double xCoord, double yCoord,
            double zCoord, int numberOfParticles, double xOffset, double yOffset, double zOffset, double particleSpeed, int... particleArguments)
    {
        // NO-OP
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double xCoord, double yCoord, double zCoord,
            int numberOfParticles, double xOffset, double yOffset, double zOffset, double particleSpeed, int... particleArguments)
    {
        // NO-OP
    }

    @Override
    @Nullable
    public Entity getEntityFromUuid(UUID uuid)
    {
        return null;
    }

    @Override
    public ListenableFuture<Object> addScheduledTask(Runnable runnableToSchedule)
    {
        return null; // TODO
    }

    @Override
    public boolean isCallingFromMinecraftThread()
    {
        return true;
    }

    @Override
    public File getChunkSaveLocation()
    {
        return new File(Minecraft.getMinecraft().mcDataDir, "saves/pp_fake/");
    }
}
