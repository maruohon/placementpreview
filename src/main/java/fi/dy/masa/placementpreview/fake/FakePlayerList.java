package fi.dy.masa.placementpreview.fake;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;

public class FakePlayerList extends PlayerList
{
    public FakePlayerList(MinecraftServer server)
    {
        super(server);
    }
}
