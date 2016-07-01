package fi.dy.masa.placementpreview;

import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.proxy.CommonProxy;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.placementpreview.config.PlacementPreviewGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/placementpreview/master/update.json",
    clientSideOnly=true, acceptedMinecraftVersions = "1.9.4")
public class PlacementPreview
{
    @Instance(Reference.MOD_ID)
    public static PlacementPreview instance;

    @SidedProxy(clientSide = "fi.dy.masa.placementpreview.proxy.ClientProxy", serverSide = "fi.dy.masa.placementpreview.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        instance = this;
        logger = event.getModLog();
        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());
        proxy.registerEventHandlers();
    }
}
