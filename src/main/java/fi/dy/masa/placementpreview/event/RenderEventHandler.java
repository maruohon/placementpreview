package fi.dy.masa.placementpreview.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
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
import fi.dy.masa.placementpreview.world.FakeWorld;

public class RenderEventHandler
{
    private final Minecraft mc;
    private BlockRendererDispatcher dispatcher;
    private float partialTickLast;
    private final List<BlockPos> positions;
    private World fakeWorld;
    private boolean doRender;
    //private BlockPos lastBlockPos;
    //private Vec3d lastHitPos;

    public RenderEventHandler()
    {
        this.mc = Minecraft.getMinecraft();
        this.positions = new ArrayList<BlockPos>();
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
            this.checkIfModelNeedsUpdate();
        }

        if (this.doRender)
        {
            for (BlockPos pos : this.positions)
            {
                IBlockState state = this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos);
                this.renderGhostBlock(pos, state, this.mc.thePlayer, this.mc.theWorld.getLightBrightness(pos), partialTicks);
            }
        }

        this.partialTickLast = partialTicks;
    }

    private void checkIfModelNeedsUpdate()
    {
        RayTraceResult trace = this.mc.objectMouseOver;

        if (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK)
        {
            BlockPos pos = trace.getBlockPos();
            Vec3d hitPos = trace.hitVec;

            //if (pos.equals(this.lastBlockPos) == false || hitPos.equals(this.lastHitPos) == false)
            {
                this.updateFakeBlocks(pos, hitPos, this.mc.thePlayer, trace.sideHit);
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

    private void updateFakeBlocks(BlockPos posCenter, Vec3d hitPos, EntityPlayer player, EnumFacing side)
    {
        float hitX = (float)hitPos.xCoord - posCenter.getX();
        float hitY = (float)hitPos.yCoord - posCenter.getY();
        float hitZ = (float)hitPos.zCoord - posCenter.getZ();

        this.copyCurrentBlocksToFakeWorld(posCenter);

        EnumActionResult result = this.doUseAction(posCenter, side, hitPos, player, EnumHand.MAIN_HAND, hitX, hitY, hitZ);
        if (result != EnumActionResult.SUCCESS)
        {
            result = this.doUseAction(posCenter, side, hitPos, player, EnumHand.OFF_HAND, hitX, hitY, hitZ);
        }

        this.detectChangedBlocks(posCenter);
    }

    private EnumActionResult doUseAction(BlockPos posCenter, EnumFacing side, Vec3d hitPos, EntityPlayer player, 
            EnumHand hand, float hitX, float hitY, float hitZ)
    {
        ItemStack stack = player.getHeldItem(hand);
        if (stack != null)
        {
            return stack.copy().onItemUse(player, this.fakeWorld, posCenter, hand, side, hitX, hitY, hitZ);
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

        int r = 2;

        for (int y = posCenter.getY() - r; y <= posCenter.getY() + r; y++)
        {
            for (int z = posCenter.getZ() - r; z <= posCenter.getZ() + r; z++)
            {
                for (int x = posCenter.getX() - r; x <= posCenter.getX() + r; x++)
                {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (this.fakeWorld.getBlockState(pos).getActualState(this.fakeWorld, pos) !=
                          this.mc.theWorld.getBlockState(pos).getActualState(this.mc.theWorld, pos))
                    //if (this.fakeWorld.getBlockState(pos) != this.mc.theWorld.getBlockState(pos))
                    {
                        this.positions.add(pos);
                    }
                }
            }
        }
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
}
