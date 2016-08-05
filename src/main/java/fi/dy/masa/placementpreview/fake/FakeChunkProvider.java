package fi.dy.masa.placementpreview.fake;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class FakeChunkProvider implements IChunkProvider
{
    private final FakeWorld world;

    public FakeChunkProvider(FakeWorld world)
    {
        this.world = world;
    }

    @Override
    public Chunk getLoadedChunk(int x, int z)
    {
        return this.world.getChunkFromChunkCoords(0, 0);
    }

    @Override
    public Chunk provideChunk(int x, int z)
    {
        return this.world.getChunkFromChunkCoords(0, 0);
    }

    @Override
    public boolean unloadQueuedChunks()
    {
        return true;
    }

    @Override
    public String makeString()
    {
        return "PlacementPreview_FakeChunkProvider";
    }
}
