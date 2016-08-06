package fi.dy.masa.placementpreview.fake;

import java.io.File;
import java.io.IOException;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

public class FakeServer extends MinecraftServer
{

    public FakeServer(Minecraft clientIn, String folderNameIn, String worldNameIn, WorldSettings worldSettingsIn,
            YggdrasilAuthenticationService authServiceIn, MinecraftSessionService sessionServiceIn,
            GameProfileRepository profileRepoIn, PlayerProfileCache profileCacheIn)
    {
        //super(clientIn, folderNameIn, worldNameIn, worldSettingsIn, authServiceIn, sessionServiceIn, profileRepoIn, profileCacheIn);
        super(new File(clientIn.mcDataDir, "saves/pp_fake/"), null, null, null, null, null, null);

        this.setPlayerList(new FakePlayerList(this));
    }

    @Override
    public void run()
    {
        // NO-OP
    }

    @Override
    public void loadAllWorlds(String saveName, String worldNameIn, long seed, WorldType type, String generatorOptions)
    {
        // NO-OP
    }

    @Override
    public boolean startServer() throws IOException
    {
        return true;
    }

    @Override
    public void tick()
    {
        // NO-OP
    }

    @Override
    public boolean canStructuresSpawn()
    {
        return false;
    }

    @Override
    public GameType getGameType()
    {
        return null;
    }

    @Override
    public EnumDifficulty getDifficulty()
    {
        return null;
    }

    @Override
    public boolean isHardcore()
    {
        return false;
    }

    @Override
    public int getOpPermissionLevel()
    {
        return 0;
    }

    @Override
    public boolean shouldBroadcastRconToOps()
    {
        return false;
    }

    @Override
    public boolean shouldBroadcastConsoleToOps()
    {
        return false;
    }

    @Override
    public boolean isDedicatedServer()
    {
        return false;
    }

    @Override
    public boolean shouldUseNativeTransport()
    {
        return false;
    }

    @Override
    public boolean isCommandBlockEnabled()
    {
        return false;
    }

    @Override
    public String shareToLAN(GameType type, boolean allowCheats)
    {
        return null;
    }
}
