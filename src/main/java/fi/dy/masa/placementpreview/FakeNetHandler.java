package fi.dy.masa.placementpreview;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

public class FakeNetHandler extends NetHandlerPlayClient
{
    public FakeNetHandler(Minecraft mc, GuiScreen gui, NetworkManager networkManagerIn, GameProfile profileIn)
    {
        super(mc, gui, networkManagerIn, profileIn);
    }

    @Override
    public void sendPacket(Packet<?> packetIn)
    {
        // NO-OP
    }
}
