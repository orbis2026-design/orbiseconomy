package me.Short.OrbisEconomy;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class OrbisPaperAgentBridge
{
    private static final String PLUGIN_NAME = "OrbisPaperAgent";

    public boolean isAgentOnline()
    {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    public CompletableFuture<JsonObject> makeSignedApiCall(String method, String path, JsonObject payload)
    {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null)
        {
            return CompletableFuture.failedFuture(new IllegalStateException("OrbisPaperAgent plugin is not installed."));
        }

        return CompletableFuture.supplyAsync(() -> invokeAgent(plugin, method, path, payload));
    }

    @SuppressWarnings("unchecked")
    private JsonObject invokeAgent(Plugin plugin, String method, String path, JsonObject payload)
    {
        try
        {
            Method apiMethod = plugin.getClass().getMethod("makeSignedApiCall", String.class, String.class, JsonObject.class);
            Object raw = apiMethod.invoke(plugin, method, path, payload);

            if (!(raw instanceof CompletableFuture<?> future))
            {
                throw new IllegalStateException("OrbisPaperAgent returned a non-future response.");
            }

            Object response = future.join();
            if (!(response instanceof JsonObject jsonObject))
            {
                throw new IllegalStateException("OrbisPaperAgent returned a non-JSON response.");
            }

            return jsonObject;
        }
        catch (ReflectiveOperationException exception)
        {
            throw new RuntimeException("OrbisPaperAgent API bridge call failed.", exception);
        }
    }
}
