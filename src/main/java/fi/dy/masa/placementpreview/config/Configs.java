package fi.dy.masa.placementpreview.config;

import java.io.File;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.Reference;

public class Configs
{
    public static int renderDelay;
    public static float transparencyAlpha;
    public static boolean renderAfterDelay;
    public static boolean renderGhost;
    public static boolean renderOverlapping;
    public static boolean renderWire;
    public static boolean requireSneak;
    public static boolean resetHoverTimerOnPosChange;
    public static boolean toggleOnSneak;
    public static boolean useTransparency;
    public static KeyModifier keyGhost;
    public static KeyModifier keyWire;

    public static File configurationFile;
    public static Configuration config;

    public static final String CATEGORY_GENERIC = "Generic";

    @SubscribeEvent
    public void onConfigChangedEvent(OnConfigChangedEvent event)
    {
        if (Reference.MOD_ID.equals(event.getModID()) == true)
        {
            loadConfigs(config);
        }
    }

    public static void loadConfigsFromFile(File configFile)
    {
        configurationFile = configFile;
        config = new Configuration(configFile, null, false);
        config.load();

        loadConfigs(config);
    }

    public static void loadConfigs(Configuration conf)
    {
        Property prop;

        prop = conf.get(CATEGORY_GENERIC, "renderAfterDelay", true);
        prop.setComment("Render the preview after the given delay (holding the cursor over the same block face for that amount of time)");
        renderAfterDelay = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "renderDelay", 500);
        prop.setComment("Rendering delay (in milliseconds), see renderAfterDelay");
        renderDelay = MathHelper.clamp_int(prop.getInt(), 0, 120000);

        prop = conf.get(CATEGORY_GENERIC, "renderGhost", true);
        prop.setComment("Render the \"ghost blocks\" ie. actual models");
        renderGhost = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "renderWire", false);
        prop.setComment("Render a wire frame outline of the model");
        renderWire = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "renderOverlapping", true);
        prop.setComment("Whether to render block models where blocks already exist (= changing model). They will usually look a bit derpy because they overlap the old model.");
        renderOverlapping = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "requireSneak", false);
        prop.setComment("Require holding sneak to render anything");
        requireSneak = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "resetHoverTimerOnPosChange", true);
        prop.setComment("Reset the hover delay timer when the cursor moves to a different block position");
        resetHoverTimerOnPosChange = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "toggleOnSneak", false);
        prop.setComment("Toggle the rendering state (on/off) _while_ holding sneak, based on the requireSneak value");
        toggleOnSneak = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "transparencyAlpha", 0.8);
        prop.setComment("The alpha value to use for translucent ghost blocks. 0 is fully transparent, 1 is fully opaque.");
        transparencyAlpha = MathHelper.clamp_float((float)prop.getDouble(), 0f, 1f);

        prop = conf.get(CATEGORY_GENERIC, "useTransparency", true);
        prop.setComment("Render the ghost blocks as transparent/translucent");
        useTransparency = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "keyGhost", "none");
        prop.setComment("A key that should be held for the ghost blocks to be rendered. Valid values: none, alt, control, shift");
        keyGhost = getKeyModifier(prop.getString());

        prop = conf.get(CATEGORY_GENERIC, "keyWire", "none");
        prop.setComment("A key that should be held for the wire frame to be rendered. Valid values: none, alt, control, shift");
        keyWire = getKeyModifier(prop.getString());

        if (conf.hasChanged() == true)
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
