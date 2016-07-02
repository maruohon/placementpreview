package fi.dy.masa.placementpreview.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL11;
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
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.world.FakeWorld;

public class RenderEventHandler
{
    public static boolean renderingEnabled;

    private final Minecraft mc;
    private BlockRendererDispatcher dispatcher;
    private float partialTickLast;
    private final List<BlockPos> positions;
    private final Map<BlockPos, List<BakedQuad>> quadsForWires;
    private World fakeWorld;
    private boolean doRender;
    private boolean renderWire;
    //private BlockPos lastBlockPos;
    //private Vec3d lastHitPos;

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
        this.dispatcher = this.mc.getBlockRendererDispatcher();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event)
    {
        this.fakeWorld = null;
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

        if (partialTicks < this.partialTickLast)
        {
            this.checkAndUpdateBlocks();
        }

        if (this.doRender)
        {
            EntityPlayer player = this.mc.thePlayer;
            boolean sneaking = player.isSneaking();
            boolean renderGhost = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderGhost) : Configs.renderGhost;
            this.renderWire     = (Configs.toggleOnSneak && sneaking) ? (! Configs.renderWire)  : Configs.renderWire;

            for (BlockPos pos : this.positions)
            {
                if (renderGhost && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyGhost))
                {
                    IBlockState state = this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos);
                    state = state.getBlock().getExtendedState(state, this.fakeWorld, pos);
                    this.renderGhostBlock(pos, state, player, this.mc.theWorld.getLightBrightness(pos), partialTicks);
                }

                if (this.renderWire && (sneaking || Configs.requireSneak == false) && InputEventHandler.isRequiredKeyActive(Configs.keyWire))
                {
                    this.renderWireFrames(pos, player, partialTicks);
                }
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

            //if (pos.equals(this.lastBlockPos) == false || hitPos.equals(this.lastHitPos) == false)
            {
                this.copyCurrentBlocksToFakeWorld(pos);
                this.tryPlaceFakeBlocks(pos, hitPos, trace.sideHit);
                this.detectChangedBlocks(pos);
            }

            //this.lastBlockPos = pos;
            //this.lastHitPos = hitPos;
            this.doRender = true;

        }
        else
        {
            this.doRender = false;
        }
    }

    private void tryPlaceFakeBlocks(BlockPos posCenter, Vec3d hitPos, EnumFacing side)
    {
        float hitX = (float)hitPos.xCoord - posCenter.getX();
        float hitY = (float)hitPos.yCoord - posCenter.getY();
        float hitZ = (float)hitPos.zCoord - posCenter.getZ();

        EnumActionResult result = this.doUseAction(posCenter, side, hitPos, EnumHand.MAIN_HAND, hitX, hitY, hitZ);
        if (result == EnumActionResult.PASS)
        {
            this.doUseAction(posCenter, side, hitPos, EnumHand.OFF_HAND, hitX, hitY, hitZ);
        }
    }

    private EnumActionResult doUseAction(BlockPos posCenter, EnumFacing side, Vec3d hitPos, EnumHand hand, float hitX, float hitY, float hitZ)
    {
        ItemStack stack = this.mc.thePlayer.getHeldItem(hand);
        if (stack != null)
        {
            // FIXME somehow use a FakePlayer for this?
            return stack.copy().onItemUse(this.mc.thePlayer, this.fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
        }

        return EnumActionResult.PASS;
    }

    private void copyCurrentBlocksToFakeWorld(BlockPos posCenter)
    {
        int r = 2;

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

    private void detectChangedBlocks(BlockPos posCenter)
    {
        this.positions.clear();
        this.quadsForWires.clear();

        int r = 2;

        for (int y = posCenter.getY() - r; y <= posCenter.getY() + r; y++)
        {
            for (int z = posCenter.getZ() - r; z <= posCenter.getZ() + r; z++)
            {
                for (int x = posCenter.getX() - r; x <= posCenter.getX() + r; x++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState stateFake = this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos);
                    IBlockState stateReal = this.mc.theWorld.getBlockState(pos).getActualState(this.mc.theWorld, pos);

                    if (stateFake != stateReal)
                    {
                        this.positions.add(pos);

                        if (this.renderWire)
                        {
                            this.addModelQuads(stateFake, pos);
                        }
                    }
                }
            }
        }
    }

    private void addModelQuads(IBlockState state, BlockPos pos)
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

    private void renderGhostBlock(BlockPos pos, IBlockState state, EntityPlayer player, float brightness, float partialTicks)
    {
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (state != null)
        {
            EnumBlockRenderType renderType = state.getRenderType();

            if (renderType == EnumBlockRenderType.MODEL || renderType == EnumBlockRenderType.LIQUID)
            {
                GlStateManager.pushMatrix();
                GlStateManager.enableCull();
                GlStateManager.enableDepth();
                GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz + 1.0d);
                GlStateManager.translate(0, 0, -1);
                GlStateManager.rotate(-90, 0, 1, 0);
                RenderHelper.disableStandardItemLighting();
                BlockRenderLayer layer = state.getBlock().getBlockLayer();

                if (layer == BlockRenderLayer.SOLID)
                {
                    GlStateManager.disableAlpha();
                }
                else if (layer == BlockRenderLayer.CUTOUT_MIPPED)
                {
                    GlStateManager.enableAlpha();
                }
                else if (layer == BlockRenderLayer.CUTOUT)
                {
                    this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
                }
                else if (layer == BlockRenderLayer.TRANSLUCENT)
                {
                    GlStateManager.disableBlend();
                    GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                    GlStateManager.alphaFunc(516, 0.1F);
                    GlStateManager.enableBlend();
                    GlStateManager.depthMask(false);
                    this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    GlStateManager.shadeModel(7425);
                }

                this.dispatcher.renderBlockBrightness(state, 0.9f);

                if (layer == BlockRenderLayer.CUTOUT)
                {
                    this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
                }
                else if (layer == BlockRenderLayer.TRANSLUCENT)
                {
                    GlStateManager.shadeModel(7424);
                    GlStateManager.depthMask(true);
                    GlStateManager.enableCull();
                    GlStateManager.disableBlend();
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
        }

        GlStateManager.popMatrix();
    }

    private void renderWireFrames(BlockPos pos, EntityPlayer player, float partialTicks)
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
        GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz);
        GlStateManager.disableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.glLineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer buffer = tessellator.getBuffer();

        for (BakedQuad quad : quads)
        {
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.BLOCK);
            buffer.addVertexData(quad.getVertexData());
            /*buffer.putColorMultiplier(1f, 1f, 1f, 4);
            buffer.putColorMultiplier(1f, 1f, 1f, 3);
            buffer.putColorMultiplier(1f, 1f, 1f, 2);
            buffer.putColorMultiplier(1f, 1f, 1f, 1);*/
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
