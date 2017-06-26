package fi.dy.masa.placementpreview.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.DefaultGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import fi.dy.masa.placementpreview.reference.Reference;

public class PlacementPreviewGuiFactory extends DefaultGuiFactory
{
    public PlacementPreviewGuiFactory()
    {
        super(Reference.MOD_ID, getTitle());
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent)
    {
        return new GuiConfig(parent, getConfigElements(), Reference.MOD_ID, false, false, getTitle());
    }

    private static List<IConfigElement> getConfigElements()
    {
        List<IConfigElement> configElements = new ArrayList<IConfigElement>();

        configElements.add(new ConfigElement(Configs.getConfiguration().getCategory(Configs.CATEGORY_GENERIC)));
        configElements.add(new ConfigElement(Configs.getConfiguration().getCategory(Configs.CATEGORY_LISTS)));

        return configElements;
    }

    private static String getTitle()
    {
        return GuiConfig.getAbridgedConfigPath(Configs.getConfigurationFile().toString());
    }
}
