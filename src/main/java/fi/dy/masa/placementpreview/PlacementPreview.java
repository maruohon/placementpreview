package fi.dy.masa.placementpreview;

import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.event.InputEventHandler;
import fi.dy.masa.placementpreview.event.RenderEventHandler;
import fi.dy.masa.placementpreview.event.TickHandler;
import fi.dy.masa.placementpreview.fake.FakeServer;
import fi.dy.masa.placementpreview.reference.Reference;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.placementpreview.config.PlacementPreviewGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/placementpreview/master/update.json",
    clientSideOnly=true, acceptedMinecraftVersions = "[1.11,1.11.2]")
public class PlacementPreview
{
    @Mod.Instance(Reference.MOD_ID)
    public static PlacementPreview instance;

    public static Logger logger;

    public static final KeyBinding keyToggleEnabled = new KeyBinding("placementpreview.key.toggle.enabled", KeyConflictContext.IN_GAME, KeyModifier.NONE,    Keyboard.KEY_P, "category.placementpreview");
    public static final KeyBinding keyToggleGhost   = new KeyBinding("placementpreview.key.toggle.ghost",   KeyConflictContext.IN_GAME, KeyModifier.SHIFT,   Keyboard.KEY_P, "category.placementpreview");
    public static final KeyBinding keyToggleWire    = new KeyBinding("placementpreview.key.toggle.wire",    KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_P, "category.placementpreview");

    public static MinecraftServer fakeServer;
    public static int dimId;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());

        MinecraftForge.EVENT_BUS.register(new Configs());
        MinecraftForge.EVENT_BUS.register(new InputEventHandler());
        MinecraftForge.EVENT_BUS.register(new TickHandler());
        MinecraftForge.EVENT_BUS.register(new RenderEventHandler());

        ClientRegistry.registerKeyBinding(keyToggleEnabled);
        ClientRegistry.registerKeyBinding(keyToggleGhost);
        ClientRegistry.registerKeyBinding(keyToggleWire);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        //dimId = DimensionManager.getNextFreeDimId();
        dimId = Integer.MIN_VALUE + (int)(3.14159 * 1337);
        DimensionManager.registerDimension(dimId, DimensionType.register("pp_fake", "", dimId, WorldProviderSurface.class, false));
        fakeServer = new FakeServer(Minecraft.getMinecraft(), "saves", "pp_fake", new WorldSettings(new WorldInfo(new NBTTagCompound())), null, null, null, null);
    }
}
