package fi.dy.masa.placementpreview.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.fake.FakeNetHandler;
import fi.dy.masa.placementpreview.fake.FakePlayerSP;
import fi.dy.masa.placementpreview.fake.FakeWorld;

public class TickHandler
{
    private static TickHandler instance;
    private final Minecraft mc;
    private BlockRendererDispatcher dispatcher;
    private FakeWorld fakeWorld;
    private FakePlayerSP fakePlayer;
    private BlockPos lastBlockPos;
    private Vec3d lastHitPos;
    private float lastYaw;
    private float lastPitch;
    private EnumFacing lastSide;
    private final List<ModelHolder> models;
    private boolean hoveringBlocks;
    private long hoverStartTime;
    private boolean modelsChanged;

    public TickHandler()
    {
        this.mc = Minecraft.getMinecraft();
        this.models = new ArrayList<ModelHolder>();
        instance = this;
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

    public boolean isTargetingBlocks()
    {
        return this.hoveringBlocks;
    }

    public long getHoverStartTime()
    {
        return this.hoverStartTime;
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

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        this.fakeWorld = new FakeWorld(event.getWorld());
        this.fakePlayer = new FakePlayerSP(this.mc, this.fakeWorld,
                new FakeNetHandler(null, null, null, new GameProfile(UUID.randomUUID(), "[PlacementPreview]")), null);
        this.dispatcher = this.mc.getBlockRendererDispatcher();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event)
    {
        this.fakeWorld = null;
        this.fakePlayer = null;
        this.dispatcher = null;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event)
    {
        if (event.phase != Phase.END || event.side != Side.CLIENT || this.fakeWorld == null)
        {
            return;
        }

        synchronized (this.fakeWorld)
        {
            this.checkAndUpdateBlocks(this.mc.theWorld, this.fakeWorld, this.mc.thePlayer, this.fakePlayer);
        }
    }

    public static boolean shouldRenderGhostBlocks(EntityPlayer player)
    {
        boolean sneaking = player.isSneaking();
        boolean renderGhost = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderGhost) : Configs.renderGhost;

        return renderGhost && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyGhost);
    }

    public static boolean shouldRenderWireFrame(EntityPlayer player)
    {
        boolean sneaking = player.isSneaking();
        boolean renderWire  = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderWire) : Configs.renderWire;

        return renderWire && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyWire);
    }

    private void checkAndUpdateBlocks(World realWorld, FakeWorld fakeWorld, EntityPlayer realPlayer, EntityPlayer fakePlayer)
    {
        RayTraceResult trace = this.mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK ||
            (shouldRenderGhostBlocks(realPlayer) == false && shouldRenderWireFrame(realPlayer) == false))
        {
            this.hoveringBlocks = false;
            return;
        }

        BlockPos pos = trace.getBlockPos();
        Vec3d hitPos = trace.hitVec;
        long currentTime = System.currentTimeMillis();
        boolean mainPosChanged = pos.equals(this.lastBlockPos) == false || trace.sideHit != this.lastSide ||
                fakeWorld.getBlockState(pos) != realWorld.getBlockState(pos);
        float yaw = realPlayer.rotationYaw;
        float pitch = realPlayer.rotationPitch;

        if (mainPosChanged || yaw != this.lastYaw || pitch != this.lastPitch || hitPos.equals(this.lastHitPos) == false ||
            ItemStack.areItemsEqual(realPlayer.getHeldItemMainhand(), fakePlayer.getHeldItemMainhand()) == false ||
            ItemStack.areItemsEqual(realPlayer.getHeldItemOffhand(), fakePlayer.getHeldItemOffhand()) == false)
        {
            // Clean up old TileEntities
            fakeWorld.getChunkFromChunkCoords(0, 0).getTileEntityMap().clear();
            this.copyCurrentBlocksToFakeWorld(realWorld, fakeWorld, pos, Configs.fakeWorldCopyRadius);
            this.tryPlaceFakeBlocks(fakeWorld, realPlayer, fakePlayer, pos, hitPos, trace.sideHit);
            this.getChangedBlocks(fakeWorld);
        }

        this.lastBlockPos = pos;
        this.lastHitPos = hitPos;
        this.lastSide = trace.sideHit;
        this.lastYaw = yaw;
        this.lastPitch = pitch;

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
        float hitX = (float)hitPos.xCoord - posCenter.getX();
        float hitY = (float)hitPos.yCoord - posCenter.getY();
        float hitZ = (float)hitPos.zCoord - posCenter.getZ();

        fakeWorld.clearPositions();
        fakeWorld.setStorePositions(true);

        EnumActionResult result = this.doUseAction(fakeWorld, realPlayer, fakePlayer, posCenter, side, hitPos, EnumHand.MAIN_HAND, hitX, hitY, hitZ);
        if (result == EnumActionResult.PASS)
        {
            this.doUseAction(fakeWorld, realPlayer, fakePlayer, posCenter, side, hitPos, EnumHand.OFF_HAND, hitX, hitY, hitZ);
        }

        fakeWorld.setStorePositions(false);
    }

    private EnumActionResult doUseAction(final World fakeWorld, final EntityPlayer realPlayer, final EntityPlayer fakePlayer,
            final BlockPos posCenter, final EnumFacing side, final Vec3d hitPos, final EnumHand hand,
            final float hitX, final float hitY, final float hitZ)
    {
        ItemStack stack = realPlayer.getHeldItem(hand);

        if (stack != null)
        {
            fakePlayer.setLocationAndAngles(realPlayer.posX, realPlayer.posY, realPlayer.posZ, realPlayer.rotationYaw, realPlayer.rotationPitch);

            ItemStack stackCopy = stack.copy();
            fakePlayer.setHeldItem(hand, stackCopy);

            EnumActionResult result = stackCopy.getItem().onItemUseFirst(stackCopy, fakePlayer, fakeWorld, posCenter, side, hitX, hitY, hitZ, hand);
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }

            result = stackCopy.onItemUse(fakePlayer, fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }

            result = stackCopy.useItemRightClick(fakeWorld, fakePlayer, hand).getType();
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }
        }

        return EnumActionResult.PASS;
    }

    private void copyCurrentBlocksToFakeWorld(final World realWorld, final World fakeWorld, final BlockPos posCenter, int radius)
    {
        for (int y = posCenter.getY() - radius; y <= posCenter.getY() + radius; y++)
        {
            for (int z = posCenter.getZ() - radius; z <= posCenter.getZ() + radius; z++)
            {
                for (int x = posCenter.getX() - radius; x <= posCenter.getX() + radius; x++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = realWorld.getBlockState(pos);

                    if (fakeWorld.setBlockState(pos, state, 0) && state.getBlock().hasTileEntity(state))
                    {
                        TileEntity teSrc = realWorld.getTileEntity(pos);
                        TileEntity teDst = fakeWorld.getTileEntity(pos);

                        if (teSrc != null && teDst != null)
                        {
                            teDst.handleUpdateTag(teSrc.getUpdateTag());
                            //teDst.readFromNBT(teSrc.writeToNBT(new NBTTagCompound()));
                        }
                    }
                }
            }
        }
    }

    private void getChangedBlocks(final FakeWorld fakeWorld)
    {
        this.models.clear();
        this.modelsChanged = true;

        for (BlockPos pos : fakeWorld.getChangedPositions())
        {
            IBlockState actualState = fakeWorld.getBlockState(pos).getActualState(fakeWorld, pos);

            IBlockState extendedState = actualState.getBlock().getExtendedState(actualState, fakeWorld, pos);
            IBakedModel model = this.dispatcher.getModelForState(actualState);

            this.models.add(new ModelHolder(pos, actualState, extendedState, fakeWorld.getTileEntity(pos), model));
        }
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
