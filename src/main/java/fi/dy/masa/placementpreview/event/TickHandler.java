package fi.dy.masa.placementpreview.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.placementpreview.PlacementPreview;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.fake.FakeNetHandler;
import fi.dy.masa.placementpreview.fake.FakePlayerSP;
import fi.dy.masa.placementpreview.fake.FakeWorld;

public class TickHandler
{
    private static TickHandler instance;

    private final Minecraft mc;
    private FakeWorld fakeWorld;
    private FakePlayerSP fakePlayer;
    private BlockPos lastBlockPos;
    private Vec3d lastHitPos;
    private float lastYaw;
    private float lastPitch;
    private EnumFacing lastSide;
    private final List<ModelHolder> models;
    private boolean fakeUseInProgress;
    private boolean hoveringBlocks;
    private long hoverStartTime;
    private boolean modelsChanged;
    private final Set<IBlockState> blacklistedBlockstatesFromCopy = new HashSet<>();
    private final HashSet<ResourceLocation> blacklistedItems = new HashSet<ResourceLocation>();
    private final HashSet<ResourceLocation> whitelistedItems = new HashSet<ResourceLocation>();

    public TickHandler()
    {
        instance = this;
        this.mc = Minecraft.getMinecraft();
        this.models = new ArrayList<ModelHolder>();
        this.setBlacklistedItems(Configs.blacklistedItems);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        if (event.getWorld() != null && event.getWorld().isRemote)
        {
            this.fakeWorld = new FakeWorld(PlacementPreview.fakeServer, event.getWorld());
            this.fakePlayer = new FakePlayerSP(this.mc, this.fakeWorld,
                    new FakeNetHandler(null, null, null, new GameProfile(new UUID(0xDEADBEEF, 0xBABECAFE), "[PlacementPreview]")), null, null);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event)
    {
        if (event.phase == Phase.END && event.side == Side.CLIENT &&
            this.fakeWorld != null && this.mc.world != null && this.mc.player != null)
        {
            synchronized (this.fakeWorld)
            {
                this.checkAndUpdateBlocks(this.mc.world, this.fakeWorld, this.mc.player, this.fakePlayer);
            }
        }
    }

    public static TickHandler getInstance()
    {
        return instance;
    }

    public World getFakeWorld()
    {
        return this.fakeWorld;
    }

    public EntityPlayer getFakePlayer()
    {
        return this.fakePlayer;
    }

    public void setBlacklistedItems(String[] items)
    {
        this.blacklistedItems.clear();

        for (String name : items)
        {
            this.blacklistedItems.add(new ResourceLocation(name));
        }
    }

    public void setWhitelistedItems(String[] items)
    {
        this.whitelistedItems.clear();

        for (String name : items)
        {
            this.whitelistedItems.add(new ResourceLocation(name));
        }
    }

    public void setBlacklistedBlocks(String[] blocks)
    {
        this.blacklistedBlockstatesFromCopy.clear();

        for (String name : blocks)
        {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name));

            if (block != Blocks.AIR)
            {
                this.blacklistedBlockstatesFromCopy.addAll(block.getBlockState().getValidStates());
            }
        }
    }

    public boolean isTargetingBlocks()
    {
        return this.hoveringBlocks;
    }

    public long getHoverStartTime()
    {
        return this.hoverStartTime;
    }

    public boolean fakeUseInProgress()
    {
        return this.fakeUseInProgress;
    }

    public List<ModelHolder> getModels()
    {
        return this.models;
    }

    public boolean modelsChanged()
    {
        return this.modelsChanged;
    }

    public void clearModelsChanged()
    {
        this.modelsChanged = false;
    }

    public static boolean shouldRenderGhostBlocks(EntityPlayer player)
    {
        boolean mainCondition = Configs.enableRenderGhost && (Configs.requireSneakForGhost == false || player.isSneaking());
        boolean renderState = Configs.defaultStateGhost ^ (Configs.toggleGhostWhileHoldingKey && InputEventHandler.isRequiredKeyActive(Configs.toggleKeyGhost));

        return mainCondition && renderState;
    }

    public static boolean shouldRenderWireFrame(EntityPlayer player)
    {
        boolean mainCondition = Configs.enableRenderWire && (Configs.requireSneakForWire == false || player.isSneaking());
        boolean renderState = Configs.defaultStateWire ^ (Configs.toggleWireWhileHoldingKey && InputEventHandler.isRequiredKeyActive(Configs.toggleKeyWire));

        return mainCondition && renderState;
    }

    private boolean isHoldinPreviewableItem(EntityPlayer player)
    {
        return this.isItemAllowedForPreviewing(player.getHeldItemMainhand()) ||
               this.isItemAllowedForPreviewing(player.getHeldItemOffhand());
    }

    private boolean isItemAllowedForPreviewing(ItemStack stack)
    {
        return stack.isEmpty() == false && this.isItemAllowedForPreviewing(stack.getItem().getRegistryName());
    }

    private boolean isItemAllowedForPreviewing(ResourceLocation registryName)
    {
        return (Configs.itemListIsWhitelist && this.whitelistedItems.contains(registryName)) ||
               (Configs.itemListIsWhitelist == false && this.blacklistedItems.contains(registryName) == false);
    }

    private void checkAndUpdateBlocks(World realWorld, FakeWorld fakeWorld, EntityPlayer realPlayer, EntityPlayer fakePlayer)
    {
        RayTraceResult trace = this.mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK ||
            (shouldRenderGhostBlocks(realPlayer) == false && shouldRenderWireFrame(realPlayer) == false) ||
            this.isHoldinPreviewableItem(realPlayer) == false)
        {
            this.hoveringBlocks = false;
            return;
        }

        BlockPos pos = trace.getBlockPos();
        boolean mainPosChanged = pos.equals(this.lastBlockPos) == false || trace.sideHit != this.lastSide ||
                fakeWorld.getBlockState(pos) != realWorld.getBlockState(pos);

        if (mainPosChanged || realPlayer.rotationYaw != this.lastYaw || realPlayer.rotationPitch != this.lastPitch ||
            trace.hitVec.equals(this.lastHitPos) == false ||
            ItemStack.areItemsEqual(realPlayer.getHeldItemMainhand(), fakePlayer.getHeldItemMainhand()) == false ||
            ItemStack.areItemsEqual(realPlayer.getHeldItemOffhand(), fakePlayer.getHeldItemOffhand()) == false)
        {
            this.copyCurrentBlocksToFakeWorld(realWorld, fakeWorld, pos, Configs.fakeWorldCopyRadius);
            this.tryPlaceFakeBlocks(fakeWorld, realPlayer, fakePlayer, pos, trace.hitVec, trace.sideHit);
            this.getChangedBlocks(realWorld, fakeWorld, pos, Configs.fakeWorldCopyRadius - 1 < 0 ? 0 : Configs.fakeWorldCopyRadius - 1);
        }

        this.lastBlockPos = pos;
        this.lastHitPos = trace.hitVec;
        this.lastSide = trace.sideHit;
        this.lastYaw = realPlayer.rotationYaw;
        this.lastPitch = realPlayer.rotationPitch;

        long currentTime = System.currentTimeMillis();
        // Reset the start time when the hover position changes, but only when enabled in the config for each position change,
        // or when the timer hasn't yet hit the activation delay for the first time
        if (mainPosChanged && (Configs.resetHoverTimerOnPosChange || currentTime - this.hoverStartTime < Configs.renderDelay))
        {
            this.hoverStartTime = currentTime;
        }

        if (this.hoveringBlocks == false)
        {
            this.hoverStartTime = currentTime;
            this.hoveringBlocks = true;
        }
    }

    private void tryPlaceFakeBlocks(final FakeWorld fakeWorld, final EntityPlayer realPlayer, final EntityPlayer fakePlayer,
            final BlockPos posCenter, final Vec3d hitPos, final EnumFacing side)
    {
        float hitX = (float) hitPos.x - posCenter.getX();
        float hitY = (float) hitPos.y - posCenter.getY();
        float hitZ = (float) hitPos.z - posCenter.getZ();

        fakeWorld.clearPositions();
        fakeWorld.setStorePositions(true);
        this.fakeUseInProgress = true;

        EnumActionResult result = this.doUseAction(fakeWorld, realPlayer, fakePlayer, posCenter, side, hitPos, EnumHand.MAIN_HAND, hitX, hitY, hitZ);
        if (result == EnumActionResult.PASS)
        {
            result = this.doUseAction(fakeWorld, realPlayer, fakePlayer, posCenter, side, hitPos, EnumHand.OFF_HAND, hitX, hitY, hitZ);
        }

        this.fakeUseInProgress = false;
        fakeWorld.setStorePositions(false);
    }

    private EnumActionResult doUseAction(final World fakeWorld, final EntityPlayer realPlayer, final EntityPlayer fakePlayer,
            final BlockPos posCenter, final EnumFacing side, final Vec3d hitPos, final EnumHand hand,
            final float hitX, final float hitY, final float hitZ)
    {
        ItemStack stack = realPlayer.getHeldItem(hand);

        if (stack.isEmpty() == false)
        {
            ResourceLocation regName = stack.getItem().getRegistryName();

            if (this.isItemAllowedForPreviewing(regName) == false)
            {
                return EnumActionResult.PASS;
            }

            fakePlayer.setLocationAndAngles(realPlayer.posX, realPlayer.posY, realPlayer.posZ, realPlayer.rotationYaw, realPlayer.rotationPitch);

            ItemStack stackCopy = stack.copy();
            // A second copy, to which to reset after the fake use action has been completed.
            // This prevents the fake use from happening again every tick if the only item from the fake player's hand was consumed.
            ItemStack stackCopy2 = stack.copy();
            fakePlayer.setHeldItem(hand, stackCopy);

            try
            {
                PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(
                        fakePlayer, hand, posCenter, side, ForgeHooks.rayTraceEyeHitVec(fakePlayer, 6));

                if (event.isCanceled())
                {
                    return EnumActionResult.PASS;
                }

                EnumActionResult result = stackCopy.onItemUseFirst(fakePlayer, fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
                if (result == EnumActionResult.SUCCESS)
                {
                    fakePlayer.setHeldItem(hand, stackCopy2);
                    return result;
                }

                result = stackCopy.onItemUse(fakePlayer, fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
                if (result == EnumActionResult.SUCCESS)
                {
                    fakePlayer.setHeldItem(hand, stackCopy2);
                    return result;
                }

                result = stackCopy.useItemRightClick(fakeWorld, fakePlayer, hand).getType();
                if (result == EnumActionResult.SUCCESS)
                {
                    fakePlayer.setHeldItem(hand, stackCopy2);
                    return result;
                }
            }
            catch (Throwable t)
            {
                if (Configs.enableVerboseLogging)
                {
                    PlacementPreview.logger.warn("Item '{}' threw an exception while trying to fake use it," +
                        " blacklisting it for this session\n", regName, t);
                }
                else
                {
                    PlacementPreview.logger.warn("Item '{}' threw an exception while trying to fake use it," +
                        " blacklisting it for this session\n", regName);
                }

                fakePlayer.setHeldItem(hand, stackCopy2);
                this.blacklistedItems.add(regName);
                this.whitelistedItems.remove(regName);
            }
        }

        return EnumActionResult.PASS;
    }

    public void blackListBlockBecauseOfException(IBlockState state, BlockPos pos, Throwable t, String strWhen)
    {
        if (state.getBlock() != Blocks.AIR)
        {
            this.blacklistedBlockstatesFromCopy.add(state);

            if (Configs.enableVerboseLogging)
            {
                PlacementPreview.logger.warn("Block '{}' at {} threw an exception {}, blacklisting it for this session\n", state, pos, strWhen, t);
            }
            else
            {
                PlacementPreview.logger.trace("Block '{}' at {} threw an exception {}, blacklisting it for this session\n", state, pos, strWhen, t);
            }
        }
    }

    private void copyCurrentBlocksToFakeWorld(final World realWorld, final FakeWorld fakeWorld, final BlockPos posCenter, int radius)
    {
        for (int y = posCenter.getY() - radius; y <= posCenter.getY() + radius; y++)
        {
            for (int z = posCenter.getZ() - radius; z <= posCenter.getZ() + radius; z++)
            {
                for (int x = posCenter.getX() - radius; x <= posCenter.getX() + radius; x++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = realWorld.getBlockState(pos);

                    // Check that this block hasn't caused problems during this game launch and hasn't been blacklisted via the configs
                    if (this.blacklistedBlockstatesFromCopy.contains(state))
                    {
                        continue;
                    }

                    try
                    {
                        boolean success = fakeWorld.setBlockState(pos, state, 0, false);

                        if (success && Configs.enableTileEntityDataCopying && state.getBlock().hasTileEntity(state))
                        {
                            TileEntity teSrc = realWorld.getTileEntity(pos);
                            TileEntity teDst = fakeWorld.getTileEntity(pos);

                            if (teSrc != null && teDst != null)
                            {
                                try
                                {
                                    teDst.handleUpdateTag(teSrc.getUpdateTag());
                                    //teDst.readFromNBT(teSrc.writeToNBT(new NBTTagCompound())); // This is more crashy with mod stuff
                                }
                                catch (Throwable t)
                                {
                                    // (Almost) silently ignore these... it seems that some/many mod TileEntities crash
                                    // if writeToNBT() and/or readFromNBT() is called on the client side
                                    if (Configs.enableVerboseLogging)
                                    {
                                        PlacementPreview.logger.warn("Block '{}' at {} threw an exception while trying to copy" +
                                            " TE data to the fake world\n", state, pos, t);
                                    }
                                    else
                                    {
                                        PlacementPreview.logger.debug("Block '{}' at {} threw an exception while trying to copy" +
                                            " TE data to the fake world\n", state, pos, t);
                                    }
                                }
                            }
                        }
                    }
                    catch (Throwable t)
                    {
                        fakeWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), 0, false);
                        this.blackListBlockBecauseOfException(state, pos, t, "while trying to copy it to the fake world");
                    }
                }
            }
        }
    }

    private void getChangedBlocks(final World realWorld, final FakeWorld fakeWorld, final BlockPos posCenter, int radius)
    {
        this.models.clear();
        this.modelsChanged = true;

        // Render overlapping: Get all changed actual block states within the radius
        if (Configs.renderOverlapping)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);

            for (int y = posCenter.getY() - radius; y <= posCenter.getY() + radius; y++)
            {
                for (int z = posCenter.getZ() - radius; z <= posCenter.getZ() + radius; z++)
                {
                    for (int x = posCenter.getX() - radius; x <= posCenter.getX() + radius; x++)
                    {
                        pos.setPos(x, y, z);

                        if (realWorld.getBlockState(pos).getActualState(realWorld, pos) !=
                            fakeWorld.getBlockState(pos).getActualState(fakeWorld, pos))
                        {
                            this.addModelState(fakeWorld, pos.toImmutable());
                        }
                    }
                }
            }
        }
        // Don't render overlapping: only get the positions that have been setBlockState()'d in the fake world
        // Note: this does also include overlapping blocks sometimes, if the use action changes neighbor blocks
        // to actually different states via setBlockState()
        else
        {
            for (BlockPos pos : fakeWorld.getChangedPositions())
            {
                this.addModelState(fakeWorld, pos);
            }
        }
    }

    private void addModelState(World fakeWorld, BlockPos pos)
    {
        IBlockState actualState = fakeWorld.getBlockState(pos).getActualState(fakeWorld, pos);

        IBlockState extendedState = actualState.getBlock().getExtendedState(actualState, fakeWorld, pos);
        IBakedModel model = this.mc.getBlockRendererDispatcher().getModelForState(actualState);

        this.models.add(new ModelHolder(pos, actualState, extendedState, fakeWorld.getTileEntity(pos), model));
    }

    public static class ModelHolder
    {
        public final BlockPos pos;
        public final IBlockState actualState;
        public final IBlockState extendedState;
        public final TileEntity te;
        public final IBakedModel model;
        public final List<BakedQuad> quads;

        public ModelHolder(BlockPos pos, IBlockState actualState, IBlockState extendedState, @Nullable TileEntity te, IBakedModel model)
        {
            this.pos = pos;
            this.actualState = actualState;
            this.extendedState = extendedState;
            this.te = te;
            this.model = model;
            this.quads = new ArrayList<BakedQuad>();
        }
    }
}
