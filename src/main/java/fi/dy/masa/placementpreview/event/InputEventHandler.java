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
    /*private final Minecraft mc;

    public InputEventHandler()
    {
        this.mc = Minecraft.getMinecraft();
    }*/

    @SubscribeEvent
    public void onKeyInputEvent(KeyInputEvent event)
    {
        int keyCode = Keyboard.getEventKey();
        /*boolean state = Keyboard.getEventKeyState();

        if (state == true && keyCode == PlacementPreview.keyToggleEnabled.getKeyCode())
        {
            if (this.mc.thePlayer.isSneaking())
            {
                Configs.renderGhost = ! Configs.renderGhost;
            }
            else if (GuiScreen.isCtrlKeyDown())
            {
                Configs.renderWire = ! Configs.renderWire;
            }
            else
            {
                RenderEventHandler.renderingEnabled = ! RenderEventHandler.renderingEnabled;
            }
        }*/

        if (PlacementPreview.keyToggleEnabled.isActiveAndMatches(keyCode))
        {
            RenderEventHandler.renderingEnabled = ! RenderEventHandler.renderingEnabled;
        }
        else if (PlacementPreview.keyToggleGhost.isActiveAndMatches(keyCode))
        {
            Configs.renderGhost = ! Configs.renderGhost;
        }
        else if (PlacementPreview.keyToggleWire.isActiveAndMatches(keyCode))
        {
            Configs.renderWire = ! Configs.renderWire;
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
