package fi.dy.masa.placementpreview.event;

import java.util.List;
import org.lwjgl.opengl.GL11;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
        if (TickHandler.shouldRenderGhostBlocks(player))
        {
            List<ModelHolder> models = TickHandler.getInstance().getModels();

            for (ModelHolder holder : models)
            {
                this.renderGhostBlock(fakeWorld, holder, player, partialTicks);
            }
        }

        if (TickHandler.shouldRenderWireFrame(player))
        {
            List<ModelHolder> models = TickHandler.getInstance().getModels();

            for (ModelHolder holder : models)
            {
                this.renderWireFrames(holder.quads, holder.pos, player, partialTicks);
            }
        }
    }

    private void renderGhostBlock(final World fakeWorld, ModelHolder holder, final EntityPlayer player, final float partialTicks)
    {
        BlockPos pos = holder.pos;
        boolean existingModel = this.mc.theWorld.isAirBlock(pos) == false;

        if (Configs.renderOverlapping == false && existingModel)
        {
            return;
        }

        IBlockState actualState = holder.actualState;
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        float brightness = 0.9f;

        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        EnumBlockRenderType renderType = actualState.getRenderType();

        if (renderType == EnumBlockRenderType.MODEL || renderType == EnumBlockRenderType.LIQUID)
        {
            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.getX() - dx, pos.getY() - dy, pos.getZ() - dz);

            if (existingModel)
            {
                GlStateManager.scale(1.001, 1.001, 1.001);
            }

            RenderHelper.disableStandardItemLighting();
            BlockRenderLayer layer = actualState.getBlock().getBlockLayer();

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
                IBakedModel model = holder.model;
                IBlockState extendedState = holder.extendedState;

                GlStateManager.color(1f, 1f, 1f, 1f);

                if (Configs.useTransparency)
                {
                    int alpha = ((int)(Configs.transparencyAlpha * 0xFF)) << 24;

                    GlStateManager.enableBlend();
                    GlStateManager.enableTexture2D();

                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.colorMask(false, false, false, false);
                    this.renderModel(fakeWorld, extendedState, model, pos, alpha);

                    GlStateManager.colorMask(true, true, true, true);
                    GlStateManager.depthFunc(GL11.GL_LEQUAL);
                    this.renderModel(fakeWorld, extendedState, model, pos, alpha);

                    GlStateManager.disableBlend();
                }
                else
                {
                    GlStateManager.rotate(-90, 0, 1, 0);
                    this.mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightness(model, extendedState, brightness, true);
                }

                if (layer == BlockRenderLayer.CUTOUT)
                {
                    this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
                }
            }

            GlStateManager.popMatrix();
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

    private void renderModel(final World world, final IBlockState extendedState, final IBakedModel model, final BlockPos pos, final int alpha)
    {
        final Tessellator tessellator = Tessellator.getInstance();
        final VertexBuffer buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);

        for (final EnumFacing facing : EnumFacing.values())
        {
            this.renderQuads(world, extendedState, pos, buffer, model.getQuads(extendedState, facing, 0), alpha);
        }

        this.renderQuads(world, extendedState, pos, buffer, model.getQuads(extendedState, null, 0), alpha);

        tessellator.draw();
    }

    private void renderQuads(final World world, final IBlockState state, final BlockPos pos,
            final VertexBuffer buffer, final List<BakedQuad> quads, final int alpha)
    {
        for (final BakedQuad quad : quads)
        {
            final int color = quad.getTintIndex() == -1 ? alpha | 0xffffff : this.getTint(world, state, pos, alpha, quad.getTintIndex());
            LightUtil.renderQuadColor(buffer, quad, color);
        }
    }

    private int getTint(final World world, final IBlockState state, final BlockPos pos, final int alpha, final int tintIndex)
    {
        return alpha | this.mc.getBlockColors().colorMultiplier(state, world, pos, tintIndex);
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
