package fi.dy.masa.placementpreview.fake;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;

public class FakePlayerSP extends EntityPlayerSP
{
    public FakePlayerSP (Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatisticsManager statFile)
    {
        super(mcIn, worldIn, netHandler, statFile);
    }

    @Override
    public void addChatComponentMessage(ITextComponent chatComponent)
    {
        // No-OP
    }

    @Override
    public void addChatMessage(ITextComponent component)
    {
        // No-OP
    }

    @Override
    public void displayGui(IInteractionObject guiOwner)
    {
        // NO-OP
    }

    @Override
    public boolean isSneaking()
    {
        return this.mc.player.isSneaking();
    }
}
