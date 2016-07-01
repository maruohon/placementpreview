package fi.dy.masa.placementpreview.proxy;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.event.RenderEventHandler;

public class ClientProxy extends CommonProxy
{
    public static KeyBinding keyToggleMode;

    @Override
    public void registerEventHandlers()
    {
        MinecraftForge.EVENT_BUS.register(new Configs());
        MinecraftForge.EVENT_BUS.register(new RenderEventHandler());
    }
}
