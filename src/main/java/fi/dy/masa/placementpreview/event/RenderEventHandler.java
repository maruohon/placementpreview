package fi.dy.masa.placementpreview.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.lwjgl.opengl.GL11;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.fake.FakeNetHandler;
import fi.dy.masa.placementpreview.fake.FakePlayerSP;
import fi.dy.masa.placementpreview.fake.FakeWorld;

public class RenderEventHandler
{
    public static boolean renderingDisabled;

    private final Minecraft mc;
    private BlockRendererDispatcher dispatcher;
    private float partialTickLast;
    private final List<BlockPos> positions;
    private final Map<BlockPos, List<BakedQuad>> quadsForWires;
    private FakeWorld fakeWorld;
    private FakePlayerSP fakePlayer;
    private boolean hoveringBlocks;
    private long hoverStartTime;
    private BlockPos lastBlockPos;
    private Vec3d lastHitPos;
    private float lastYaw;
    private float lastPitch;
    private EnumFacing lastSide;

    public RenderEventHandler()
    {
        this.mc = Minecraft.getMinecraft();
        this.positions = new ArrayList<BlockPos>();
        this.quadsForWires = new HashMap<BlockPos, List<BakedQuad>>();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        this.fakeWorld = new FakeWorld(event.getWorld());
        this.fakePlayer = new FakePlayerSP(this.mc, this.fakeWorld,
                new FakeNetHandler(null, null, null, new GameProfile(UUID.randomUUID(), "[Fake]")), null);
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
    public void onRenderWorldLast(RenderWorldLastEvent event)
    {
        if (this.fakeWorld == null || this.dispatcher == null)
        {
            return;
        }

        float partialTicks = event.getPartialTicks();

        // New game tick
        if (partialTicks < this.partialTickLast)
        {
            this.checkAndUpdateBlocks();
        }

        if (this.hoveringBlocks && renderingDisabled == false)
        {
            boolean render = true;

            if (Configs.renderAfterDelay)
            {
                if (System.currentTimeMillis() - this.hoverStartTime < Configs.renderDelay)
                {
                    render = false;
                }
            }

            if (render)
            {
                this.renderChangedBlocks(partialTicks);
            }
        }

        this.partialTickLast = partialTicks;
    }

    private void checkAndUpdateBlocks()
    {
        RayTraceResult trace = this.mc.objectMouseOver;
        if (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK)
        {
            BlockPos pos = trace.getBlockPos();
            Vec3d hitPos = trace.hitVec;
            long currentTime = System.currentTimeMillis();
            boolean mainPosChanged = pos.equals(this.lastBlockPos) == false || trace.sideHit != this.lastSide ||
                    this.fakeWorld.getBlockState(pos) != this.mc.theWorld.getBlockState(pos);
            float yaw = this.mc.thePlayer.rotationYaw;
            float pitch = this.mc.thePlayer.rotationPitch;

            if (mainPosChanged || yaw != this.lastYaw || pitch != this.lastPitch || hitPos.equals(this.lastHitPos) == false ||
                ItemStack.areItemsEqual(this.mc.thePlayer.getHeldItemMainhand(), this.fakePlayer.getHeldItemMainhand()) == false ||
                ItemStack.areItemsEqual(this.mc.thePlayer.getHeldItemOffhand(), this.fakePlayer.getHeldItemOffhand()) == false)
            {
                // Clean up old TileEntities
                this.fakeWorld.getChunkFromChunkCoords(0, 0).getTileEntityMap().clear();
                this.copyCurrentBlocksToFakeWorld(pos);
                this.tryPlaceFakeBlocks(pos, hitPos, trace.sideHit);
                this.getChangedBlocks();
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
        else
        {
            this.hoveringBlocks = false;
        }
    }

    private void renderChangedBlocks(final float partialTicks)
    {
        EntityPlayer player = this.mc.thePlayer;
        boolean sneaking = player.isSneaking();
        boolean renderGhost = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderGhost) : Configs.renderGhost;
        boolean renderWire  = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderWire)  : Configs.renderWire;

        if (renderGhost && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyGhost))
        {
            for (BlockPos pos : this.positions)
            {
                this.renderGhostBlock(pos, player, partialTicks);
            }
        }

        if (renderWire && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyWire))
        {
            for (BlockPos pos : this.positions)
            {
                this.renderWireFrames(pos, player, partialTicks);
            }
        }
    }

    private void tryPlaceFakeBlocks(final BlockPos posCenter, final Vec3d hitPos, final EnumFacing side)
    {
        float hitX = (float)hitPos.xCoord - posCenter.getX();
        float hitY = (float)hitPos.yCoord - posCenter.getY();
        float hitZ = (float)hitPos.zCoord - posCenter.getZ();

        this.fakeWorld.clearPositions();
        this.fakeWorld.setStorePositions(true);

        EnumActionResult result = this.doUseAction(posCenter, side, hitPos, EnumHand.MAIN_HAND, hitX, hitY, hitZ);
        if (result == EnumActionResult.PASS)
        {
            this.doUseAction(posCenter, side, hitPos, EnumHand.OFF_HAND, hitX, hitY, hitZ);
        }

        this.fakeWorld.setStorePositions(false);
    }

    private EnumActionResult doUseAction(final BlockPos posCenter, final EnumFacing side, final Vec3d hitPos, final EnumHand hand,
            final float hitX, final float hitY, final float hitZ)
    {
        ItemStack stack = this.mc.thePlayer.getHeldItem(hand);
        if (stack != null)
        {
            EntityPlayer realPlayer = this.mc.thePlayer;
            this.fakePlayer.setLocationAndAngles(realPlayer.posX, realPlayer.posY, realPlayer.posZ, realPlayer.rotationYaw, realPlayer.rotationPitch);
            ItemStack stackCopy = stack.copy();
            this.fakePlayer.setHeldItem(hand, stackCopy);

            EnumActionResult result = stackCopy.getItem().onItemUseFirst(stackCopy, this.fakePlayer, this.fakeWorld, posCenter, side, hitX, hitY, hitZ, hand);
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }

            result = stackCopy.onItemUse(this.fakePlayer, this.fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }

            result = stackCopy.useItemRightClick(this.fakeWorld, this.fakePlayer, hand).getType();
            if (result == EnumActionResult.SUCCESS || result == EnumActionResult.FAIL)
            {
                return result;
            }
        }

        return EnumActionResult.PASS;
    }

    private void copyCurrentBlocksToFakeWorld(final BlockPos posCenter)
    {
        int r = 3;

        for (int y = posCenter.getY() - r; y <= posCenter.getY() + r; y++)
        {
            for (int z = posCenter.getZ() - r; z <= posCenter.getZ() + r; z++)
            {
                for (int x = posCenter.getX() - r; x <= posCenter.getX() + r; x++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = this.mc.theWorld.getBlockState(pos);

                    if (this.fakeWorld.setBlockState(pos, state, 0) && state.getBlock().hasTileEntity(state))
                    {
                        TileEntity teSrc = this.mc.theWorld.getTileEntity(pos);
                        TileEntity teDst = this.fakeWorld.getTileEntity(pos);

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

    private void getChangedBlocks()
    {
        this.positions.clear();
        this.quadsForWires.clear();

        for (BlockPos pos : this.fakeWorld.getChangedPositions())
        {
            IBlockState stateFake = this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos);
            stateFake = stateFake.getBlock().getExtendedState(stateFake, this.fakeWorld, pos);

            this.positions.add(pos);
            this.addModelQuads(stateFake, pos);
        }
    }

    private void addModelQuads(final IBlockState state, final BlockPos pos)
    {
        if (state.getRenderType() != EnumBlockRenderType.MODEL)
        {
            return;
        }

        IBakedModel model = this.dispatcher.getModelForState(state);
        List<BakedQuad> quads = new ArrayList<BakedQuad>();

        for (EnumFacing side : EnumFacing.values())
        {
            quads.addAll(model.getQuads(state, side, 0));
        }

        quads.addAll(model.getQuads(state, null, 0));
        this.quadsForWires.put(pos, quads);
    }

    private void renderGhostBlock(final BlockPos pos, final EntityPlayer player, final float partialTicks)
    {
        boolean existingModel = this.mc.theWorld.isAirBlock(pos) == false;

        if (Configs.renderOverlapping == false && existingModel)
        {
            return;
        }

        IBlockState state = this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos);
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        EnumBlockRenderType renderType = state.getRenderType();

        if (renderType == EnumBlockRenderType.MODEL || renderType == EnumBlockRenderType.LIQUID)
        {
            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz);

            if (existingModel)
            {
                GlStateManager.scale(1.001, 1.001, 1.001);
            }

            RenderHelper.disableStandardItemLighting();
            BlockRenderLayer layer = state.getBlock().getBlockLayer();

            if (layer == BlockRenderLayer.CUTOUT)
            {
                this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            }

            if (layer == BlockRenderLayer.TRANSLUCENT)
            {
                GlStateManager.rotate(-90, 0, 1, 0);
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.enableBlend();
                GlStateManager.depthMask(false);
                this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.shadeModel(7425);

                this.dispatcher.renderBlockBrightness(state, 0.9f);

                GlStateManager.shadeModel(7424);
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
            }
            else
            {
                GlStateManager.color(1f, 1f, 1f, 1f);

                // This bit of rendering code has been taken from Chisels & Bits, thanks AlgorithmX2 !!
                if (Configs.useTransparency)
                {
                    int alpha = ((int)(Configs.transparencyAlpha * 0xFF)) << 24;
                    IBakedModel model = this.dispatcher.getModelForState(state);
                    state = state.getBlock().getExtendedState(state, this.fakeWorld, pos);

                    GlStateManager.enableBlend();
                    GlStateManager.enableTexture2D();

                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.colorMask(false, false, false, false);
                    this.renderModel(state, model, pos, alpha);

                    GlStateManager.colorMask(true, true, true, true);
                    GlStateManager.depthFunc(GL11.GL_LEQUAL);
                    this.renderModel(state, model, pos, alpha);

                    GlStateManager.disableBlend();
                }
                else
                {
                    GlStateManager.rotate(-90, 0, 1, 0);
                    IBakedModel model = this.dispatcher.getModelForState(state);
                    float brightness = 0.9f;
                    this.dispatcher.getBlockModelRenderer().renderModelBrightness(model, state, brightness, true);
                }

                if (layer == BlockRenderLayer.CUTOUT)
                {
                    this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
                }
            }

            GlStateManager.popMatrix();
        }

        if (state.getBlock().hasTileEntity(state))
        {
            TileEntity te = this.fakeWorld.getTileEntity(pos);
            int pass = 0;

            if (te != null && te.shouldRenderInPass(pass))
            {
                TileEntityRendererDispatcher.instance.preDrawBatch();
                TileEntityRendererDispatcher.instance.renderTileEntity(te, partialTicks, -1);
                TileEntityRendererDispatcher.instance.drawBatch(pass);
            }
        }

        GlStateManager.popMatrix();
    }

    // This code has been taken from Chisels & Bits, thanks AlgorithmX2 !!
    private void renderModel(final IBlockState state, final IBakedModel model, final BlockPos pos, final int alpha)
    {
        final Tessellator tessellator = Tessellator.getInstance();
        final VertexBuffer buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);

        for (final EnumFacing facing : EnumFacing.values())
        {
            this.renderQuads(state, pos, buffer, model.getQuads(state, facing, 0), alpha);
        }

        this.renderQuads(state, pos, buffer, model.getQuads(state, null, 0), alpha);
        tessellator.draw();
    }

    // This code has been taken from Chisels & Bits, thanks AlgorithmX2 !!
    private void renderQuads(final IBlockState state, final BlockPos pos, final VertexBuffer buffer, final List<BakedQuad> quads, final int alpha)
    {
        int i = 0;
        for (final int j = quads.size(); i < j; ++i)
        {
            final BakedQuad quad = quads.get(i);
            final int color = quad.getTintIndex() == -1 ? alpha | 0xffffff : this.getTint(state, pos, alpha, quad.getTintIndex());
            LightUtil.renderQuadColor(buffer, quad, color);
        }
    }

    // This code has been taken from Chisels & Bits, thanks AlgorithmX2 !!
    private int getTint(final IBlockState state, final BlockPos pos, final int alpha, final int tintIndex)
    {
        return alpha | this.mc.getBlockColors().colorMultiplier(state, this.fakeWorld, pos, tintIndex);
    }

    private void renderWireFrames(final BlockPos pos, final EntityPlayer player, final float partialTicks)
    {
        List<BakedQuad> quads = this.quadsForWires.get(pos);
        if (quads == null)
        {
            return;
        }

        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.glLineWidth(2.0f);

        GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz);

        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer buffer = tessellator.getBuffer();

        for (BakedQuad quad : quads)
        {
            buffer.begin(GL11.GL_LINE_LOOP, quad.getFormat());
            buffer.addVertexData(quad.getVertexData());
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }
}
