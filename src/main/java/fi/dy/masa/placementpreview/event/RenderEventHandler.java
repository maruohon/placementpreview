package fi.dy.masa.placementpreview.event;

import java.util.List;
import org.lwjgl.opengl.GL11;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.event.TickHandler.ModelHolder;

public class RenderEventHandler
{
    public static boolean renderingDisabled;

    private final Minecraft mc;

    public RenderEventHandler()
    {
        this.mc = Minecraft.getMinecraft();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event)
    {
        TickHandler tickHandler = TickHandler.getInstance();
        World fakeWorld = tickHandler.getFakeWorld();

        if (fakeWorld == null)
        {
            return;
        }

        synchronized (fakeWorld)
        {
            if (renderingDisabled == false && TickHandler.getInstance().isTargetingBlocks())
            {
                long hoverStartTime = tickHandler.getHoverStartTime();

                if (Configs.renderAfterDelay == false || System.currentTimeMillis() - hoverStartTime >= Configs.renderDelay)
                {
                    this.renderChangedBlocks(fakeWorld, this.mc.thePlayer, event.getPartialTicks());
                }
            }
        }
    }

    private void renderChangedBlocks(final World fakeWorld, final EntityPlayer player, final float partialTicks)
    {
        TickHandler tickHandler = TickHandler.getInstance();
        boolean renderGhost = TickHandler.shouldRenderGhostBlocks(player);
        boolean renderWire = TickHandler.shouldRenderWireFrame(player);

        if (renderGhost || renderWire)
        {
            List<ModelHolder> models = TickHandler.getInstance().getModels();

            if (tickHandler.modelsChanged())
            {
                for (ModelHolder holder : models)
                {
                    this.getQuads(holder, holder.quads);
                }

                tickHandler.clearModelsChanged();
            }

            if (renderGhost)
            {
                for (ModelHolder holder : models)
                {
                    this.renderGhostBlock(fakeWorld, holder, player, partialTicks);
                }
            }

            if (renderWire)
            {
                for (ModelHolder holder : models)
                {
                    this.renderWireFrames(holder.quads, holder.pos, player, partialTicks);
                }
            }
        }
    }

    private void getQuads(ModelHolder holder, List<BakedQuad> quads)
    {
        if (holder.actualState.getRenderType() == EnumBlockRenderType.MODEL ||
            holder.actualState.getRenderType() == EnumBlockRenderType.LIQUID)
        {
            BlockRenderLayer originalLayer = MinecraftForgeClient.getRenderLayer();

            for (BlockRenderLayer layer : BlockRenderLayer.values())
            {
                if (holder.actualState.getBlock().canRenderInLayer(holder.actualState, layer))
                {
                    ForgeHooksClient.setRenderLayer(layer);

                    for (final EnumFacing facing : EnumFacing.values())
                    {
                        quads.addAll(holder.model.getQuads(holder.extendedState, facing, 0));
                    }

                    quads.addAll(holder.model.getQuads(holder.extendedState, null, 0));
                }
            }

            ForgeHooksClient.setRenderLayer(originalLayer);
        }
    }

    private void renderGhostBlock(final World fakeWorld, ModelHolder holder, final EntityPlayer player, final float partialTicks)
    {
        boolean existingModel = this.mc.theWorld.isAirBlock(holder.pos) == false;

        if (Configs.renderOverlapping == false && existingModel)
        {
            return;
        }

        IBlockState actualState = holder.actualState;
        Block block = actualState.getBlock();

        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        if (actualState.getRenderType() == EnumBlockRenderType.MODEL || actualState.getRenderType() == EnumBlockRenderType.LIQUID)
        {
            BlockRenderLayer originalLayer = MinecraftForgeClient.getRenderLayer();

            for (BlockRenderLayer layer : BlockRenderLayer.values())
            {
                if (block.canRenderInLayer(actualState, layer))
                {
                    ForgeHooksClient.setRenderLayer(layer);
                    this.renderGhostBlock(fakeWorld, holder, player, layer, existingModel, partialTicks);
                }
            }

            ForgeHooksClient.setRenderLayer(originalLayer);
        }

        if (holder.te != null)
        {
            TileEntity te = holder.te;
            int pass = 0;

            if (te.shouldRenderInPass(pass))
            {
                TileEntityRendererDispatcher.instance.preDrawBatch();
                TileEntityRendererDispatcher.instance.renderTileEntity(te, partialTicks, -1);
                TileEntityRendererDispatcher.instance.drawBatch(pass);
            }
        }
    }

    private void renderGhostBlock(final World fakeWorld, ModelHolder holder, final EntityPlayer player, BlockRenderLayer layer, boolean existingModel, final float partialTicks)
    {
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        float brightness = 0.9f;
        BlockPos pos = holder.pos;
        IBlockState actualState = holder.actualState;

        GlStateManager.pushMatrix();
        GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz);

        if (existingModel)
        {
            GlStateManager.scale(1.001, 1.001, 1.001);
        }

        RenderHelper.disableStandardItemLighting();

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

            this.mc.getBlockRendererDispatcher().renderBlockBrightness(actualState, brightness);

            GlStateManager.shadeModel(7424);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        else
        {
            GlStateManager.color(1f, 1f, 1f, 1f);

            if (Configs.useTransparency)
            {
                int alpha = ((int)(Configs.transparencyAlpha * 0xFF)) << 24;

                GlStateManager.enableBlend();
                GlStateManager.enableTexture2D();

                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.colorMask(false, false, false, false);
                this.renderModel(fakeWorld, holder, pos, alpha);

                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                this.renderModel(fakeWorld, holder, pos, alpha);

                GlStateManager.disableBlend();
            }
            else
            {
                GlStateManager.rotate(-90, 0, 1, 0);
                this.mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightness(holder.model, holder.extendedState, brightness, true);
            }

            if (layer == BlockRenderLayer.CUTOUT)
            {
                this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
            }
        }

        GlStateManager.popMatrix();
    }

    private void renderModel(final World world, final ModelHolder holder, final BlockPos pos, final int alpha)
    {
        for (final EnumFacing facing : EnumFacing.values())
        {
            this.renderQuads(world, holder.actualState, pos, holder.model.getQuads(holder.extendedState, facing, 0), alpha);
        }

        this.renderQuads(world, holder.actualState, pos, holder.model.getQuads(holder.extendedState, null, 0), alpha);
    }

    private void renderQuads(final World world, final IBlockState actualState, final BlockPos pos, final List<BakedQuad> quads, final int alpha)
    {
        final Tessellator tessellator = Tessellator.getInstance();
        final VertexBuffer buffer = tessellator.getBuffer();

        for (final BakedQuad quad : quads)
        {
            buffer.begin(GL11.GL_QUADS, quad.getFormat());

            final int color = quad.hasTintIndex() ? this.getTint(world, actualState, pos, alpha, quad.getTintIndex()) : alpha | 0xffffff;
            LightUtil.renderQuadColor(buffer, quad, color);

            tessellator.draw();
        }
    }

    private int getTint(final World world, final IBlockState actualState, final BlockPos pos, final int alpha, final int tintIndex)
    {
        return alpha | this.mc.getBlockColors().colorMultiplier(actualState, world, pos, tintIndex);
    }

    private void renderWireFrames(final List<BakedQuad> quads, final BlockPos pos, final EntityPlayer player, final float partialTicks)
    {
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
