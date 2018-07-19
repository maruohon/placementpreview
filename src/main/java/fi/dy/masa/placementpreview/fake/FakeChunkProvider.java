package fi.dy.masa.placementpreview.fake;

import javax.annotation.Nullable;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

public class FakeChunkProvider extends ChunkProviderServer
{
    private final FakeWorld world;

    public FakeChunkProvider(FakeWorld world)
    {
        super(null, null, null);
        this.world = world;
    }

    @Override
    public Chunk getLoadedChunk(int x, int z)
    {
        return this.world.getChunk(0, 0);
    }

    @Override
    @Nullable
    public Chunk loadChunk(int x, int z)
    {
        return this.loadChunk(x, z, null);
    }

    @Override
    @Nullable
    public Chunk loadChunk(int x, int z, Runnable runnable)
    {
        return this.world.getChunk(0, 0);
    }

    @Override
    public Chunk provideChunk(int x, int z)
    {
        return this.world.getChunk(0, 0);
    }

    @Override
    public boolean saveChunks(boolean p_186027_1_)
    {
        // NO-OP
        return true;
    }

    @Override
    public boolean tick()
    {
        return true;
    }

    @Override
    public String makeString()
    {
        return "PlacementPreview_FakeChunkProvider";
    }
}
