package fi.dy.masa.placementpreview.config;

import java.io.File;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.event.RenderEventHandler;
import fi.dy.masa.placementpreview.event.TickHandler;
import fi.dy.masa.placementpreview.reference.Reference;

public class Configs
{
    public static int fakeWorldCopyRadius;
    public static int renderDelay;
    public static float transparencyAlpha;
    public static boolean defaultStateGhost;
    public static boolean defaultStateWire;
    public static boolean enableRenderGhost;
    public static boolean enableRenderWire;
    public static boolean enableTileEntityDataCopying;
    public static boolean enableVerboseLogging;
    public static boolean renderAfterDelay;
    public static boolean renderOverlapping;
    public static boolean requireSneakForGhost;
    public static boolean requireSneakForWire;
    public static boolean resetHoverTimerOnPosChange;
    public static boolean toggleGhostWhileHoldingKey;
    public static boolean toggleWireWhileHoldingKey;
    public static boolean useTransparency;
    public static KeyModifier toggleKeyGhost;
    public static KeyModifier toggleKeyWire;

    public static boolean itemListIsWhitelist;
    public static String[] blacklistedItems;
    public static String[] whitelistedItems;
    public static String[] blacklistedBlocks;

    private static File configurationFile;
    private static Configuration config;

    public static final String CATEGORY_LISTS = "Lists";
    public static final String CATEGORY_GENERIC = "Generic";

    @SubscribeEvent
    public void onConfigChangedEvent(OnConfigChangedEvent event)
    {
        if (Reference.MOD_ID.equals(event.getModID()))
        {
            loadConfigs(config);

            TickHandler.getInstance().setBlacklistedItems(blacklistedItems);
            TickHandler.getInstance().setWhitelistedItems(whitelistedItems);
            TickHandler.getInstance().setBlacklistedBlocks(blacklistedBlocks);
        }
    }

    public static void loadConfigsFromFile(File configFile)
    {
        configurationFile = configFile;
        config = new Configuration(configurationFile, null, true);
        config.load();

        loadConfigs(config);
    }

    public static File getConfigurationFile()
    {
        return configurationFile;
    }

    public static Configuration getConfiguration()
    {
        return config;
    }

    private static void loadConfigs(Configuration conf)
    {
        Property prop;

        prop = conf.get(CATEGORY_GENERIC, "defaultStateGhost", false);
        prop.setComment("The default state for rendering the ghost block, when not pressing any keys");
        defaultStateGhost = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "defaultStateWire", false);
        prop.setComment("The default state for rendering the ghost block, when not pressing any keys");
        defaultStateWire = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableRenderGhost", true);
        prop.setComment("Main enable for rendering the \"ghost blocks\" ie. actual models");
        enableRenderGhost = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableRenderWire", true);
        prop.setComment("Main enable for rendering a wire frame outline of the model");
        enableRenderWire = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableRenderingByDefault", true);
        prop.setComment("Enable rendering (the main toggle option controlled by the main keybind) by default on game launch");
        RenderEventHandler.renderingEnabled = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableTileEntityDataCopying", false);
        prop.setComment("Enable copying TileEntity data to the fake world. This may cause issues with some modded TEs.");
        enableTileEntityDataCopying = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableVerboseLogging", false);
        prop.setComment("Enable more verbose logging, which is helpful for debugging any issues");
        enableVerboseLogging = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "fakeWorldCopyRadius", 3);
        prop.setComment("The radius of blocks to copy to the fake world each time the player look position changes");
        fakeWorldCopyRadius = prop.getInt();

        prop = conf.get(CATEGORY_GENERIC, "renderAfterDelay", false);
        prop.setComment("Render the preview after the given delay (holding the cursor over the same block face for that amount of time)");
        renderAfterDelay = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "renderDelay", 500);
        prop.setComment("Rendering delay (in milliseconds), see renderAfterDelay");
        renderDelay = MathHelper.clamp(prop.getInt(), 0, 120000);

        prop = conf.get(CATEGORY_GENERIC, "renderOverlapping", true);
        prop.setComment("Whether to render block models where blocks already exist (= changing model). They will usually look a bit derpy because they overlap the old model.");
        renderOverlapping = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "requireSneakForGhost", false);
        prop.setComment("Require holding sneak to render the ghost block");
        requireSneakForGhost = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "requireSneakForWire", false);
        prop.setComment("Require holding sneak to render the wire frame");
        requireSneakForWire = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "resetHoverTimerOnPosChange", false);
        prop.setComment("Reset the hover delay timer when the cursor moves to a different block position");
        resetHoverTimerOnPosChange = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "toggleGhostWhileHoldingKey", true);
        prop.setComment("Toggle the rendering state (on/off) while holding the key set in toggleKeyGhost");
        toggleGhostWhileHoldingKey = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "toggleKeyGhost", "ctrl");
        prop.setComment("A key that should be held for the ghost blocks rendering state to change to the opposite from the default state. Valid values: none, alt, ctrl, shift");
        toggleKeyGhost = getKeyModifier(prop.getString());

        prop = conf.get(CATEGORY_GENERIC, "toggleKeyWire", "alt");
        prop.setComment("A key that should be held for the wire frame rendering state to change to the opposite from the default state. Valid values: none, alt, ctrl, shift");
        toggleKeyWire = getKeyModifier(prop.getString());

        prop = conf.get(CATEGORY_GENERIC, "toggleWireWhileHoldingKey", true);
        prop.setComment("Toggle the rendering state (on/off) while holding the key set in toggleKeyWire");
        toggleWireWhileHoldingKey = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "transparencyAlpha", 0.7);
        prop.setComment("The alpha value to use for translucent ghost blocks. 0 is fully transparent, 1 is fully opaque.");
        transparencyAlpha = MathHelper.clamp((float)prop.getDouble(), 0f, 1f);

        prop = conf.get(CATEGORY_GENERIC, "useTransparency", true);
        prop.setComment("Render the ghost blocks as transparent/translucent");
        useTransparency = prop.getBoolean();


        // Item black- and whitelists
        prop = conf.get(CATEGORY_LISTS, "itemListIsWhitelist", false);
        prop.setComment("If true, then the itemWhitelist is used. If false, then the itemBlacklist is used.");
        itemListIsWhitelist = prop.getBoolean();

        prop = conf.get(CATEGORY_LISTS, "itemBlacklist", new String[0]);
        prop.setComment("A blacklist of items the mod should not try to preview (ie. \"fake use\").\nMust be in ResourceLocation format, for example minecraft:dirt");
        blacklistedItems = prop.getStringList();

        prop = conf.get(CATEGORY_LISTS, "itemWhitelist", new String[0]);
        prop.setComment("A whitelist of items the mod is ONLY allowed to preview (ie. \"fake use\").\nMust be in ResourceLocation format, for example minecraft:dirt");
        whitelistedItems = prop.getStringList();

        prop = conf.get(CATEGORY_LISTS, "blockBlacklist", new String[0]);
        prop.setComment("A blacklist of blocks the mod should not try to copy to the fake world.\nMust be in ResourceLocation format, for example minecraft:dirt");
        blacklistedBlocks = prop.getStringList();

        if (conf.hasChanged())
        {
            conf.save();
        }
    }

    private static KeyModifier getKeyModifier(String value)
    {
        if (value == null)
        {
            return KeyModifier.NONE;
        }

        if (value.equalsIgnoreCase("shift"))
        {
            return KeyModifier.SHIFT;
        }

        if (value.equalsIgnoreCase("ctrl") || value.equalsIgnoreCase("control"))
        {
            return KeyModifier.CONTROL;
        }

        if (value.equalsIgnoreCase("alt"))
        {
            return KeyModifier.ALT;
        }

        return KeyModifier.NONE;
    }
}
