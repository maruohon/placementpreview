package fi.dy.masa.placementpreview.event;

import org.lwjgl.input.Keyboard;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import fi.dy.masa.placementpreview.PlacementPreview;
import fi.dy.masa.placementpreview.config.Configs;

public class InputEventHandler
{
    @SubscribeEvent
    public void onKeyInputEvent(KeyInputEvent event)
    {
        if (Keyboard.getEventKeyState() == false)
        {
            return;
        }

        int keyCode = Keyboard.getEventKey();

        if (PlacementPreview.keyToggleGhost.isActiveAndMatches(keyCode))
        {
            Configs.enableRenderGhost = ! Configs.enableRenderGhost;
            Configs.getConfiguration().save();
        }
        else if (PlacementPreview.keyToggleWire.isActiveAndMatches(keyCode))
        {
            Configs.enableRenderWire = ! Configs.enableRenderWire;
            Configs.getConfiguration().save();
        }
        else if (PlacementPreview.keyToggleEnabled.isActiveAndMatches(keyCode))
        {
            RenderEventHandler.renderingDisabled = ! RenderEventHandler.renderingDisabled;
        }
    }

    public static boolean isRequiredKeyActive(KeyModifier key)
    {
        if (key == KeyModifier.NONE)
        {
            return true;
        }

        if (key == KeyModifier.ALT)
        {
            return GuiScreen.isAltKeyDown();
        }

        if (key == KeyModifier.CONTROL)
        {
            return GuiScreen.isCtrlKeyDown();
        }

        if (key == KeyModifier.SHIFT)
        {
            return GuiScreen.isShiftKeyDown();
        }

        return false;
    }
}
