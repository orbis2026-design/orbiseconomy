package me.Short.OrbisEconomy;

public final class PremiumBalance
{
    private long orbs;
    private long votepoints;
    private boolean available;

    public long getOrbs()
    {
        return orbs;
    }

    public void setOrbs(long orbs)
    {
        this.orbs = Math.max(orbs, 0L);
    }

    public long getVotepoints()
    {
        return votepoints;
    }

    public void setVotepoints(long votepoints)
    {
        this.votepoints = Math.max(votepoints, 0L);
    }

    public boolean isAvailable()
    {
        return available;
    }

    public void setAvailable(boolean available)
    {
        this.available = available;
    }

    public long getBalance(String currencyId)
    {
        return switch (currencyId)
        {
            case "orbs" -> orbs;
            case "votepoints" -> votepoints;
            default -> 0L;
        };
    }

    public void applyDelta(String currencyId, long delta)
    {
        if (currencyId.equals("orbs"))
        {
            setOrbs(orbs + delta);
            return;
        }

        if (currencyId.equals("votepoints"))
        {
            setVotepoints(votepoints + delta);
        }
    }
}
