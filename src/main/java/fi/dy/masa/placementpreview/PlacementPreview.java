package fi.dy.masa.placementpreview;

import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import fi.dy.masa.placementpreview.config.Configs;
import fi.dy.masa.placementpreview.event.InputEventHandler;
import fi.dy.masa.placementpreview.event.RenderEventHandler;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.placementpreview.config.PlacementPreviewGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/placementpreview/master/update.json",
    clientSideOnly=true)
public class PlacementPreview
{
    @Instance(Reference.MOD_ID)
    public static PlacementPreview instance;

    public static Logger logger;

    public static final KeyBinding keyToggleEnabled = new KeyBinding("placementpreview.key.toggle.enabled", KeyConflictContext.IN_GAME, KeyModifier.NONE,    Keyboard.KEY_P, "category.placementpreview");
    public static final KeyBinding keyToggleGhost   = new KeyBinding("placementpreview.key.toggle.ghost",   KeyConflictContext.IN_GAME, KeyModifier.SHIFT,   Keyboard.KEY_P, "category.placementpreview");
    public static final KeyBinding keyToggleWire    = new KeyBinding("placementpreview.key.toggle.wire",    KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_P, "category.placementpreview");

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        instance = this;
        logger = event.getModLog();
        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());

        MinecraftForge.EVENT_BUS.register(new Configs());
        MinecraftForge.EVENT_BUS.register(new InputEventHandler());
        MinecraftForge.EVENT_BUS.register(new RenderEventHandler());

        ClientRegistry.registerKeyBinding(keyToggleEnabled);
        ClientRegistry.registerKeyBinding(keyToggleGhost);
        ClientRegistry.registerKeyBinding(keyToggleWire);
    }
}
