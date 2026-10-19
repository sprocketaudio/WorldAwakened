package net.sprocketgames.worldawakened.progression;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional, reflection-only FTB Teams bridge. A missing or changed API simply means solo progress. */
public final class WorldAwakenedFtbTeamsIntegration {
    private WorldAwakenedFtbTeamsIntegration() {}

    public static Optional<String> teamId(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded("ftbteams")) return Optional.empty();
        try {
            Class<?> apiType = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiType.getMethod("api").invoke(null);
            Object manager = invokeNoArg(api, "getManager");
            if (manager == null) return Optional.empty();
            Object team = invokeOneArg(manager, "getTeamForPlayer", player.getUUID());
            if (team == null) team = invokeOneArg(manager, "getTeamForPlayer", player);
            if (team == null) return Optional.empty();
            Object id = invokeNoArg(team, "getId");
            if (id == null) id = invokeNoArg(team, "getTeamId");
            String value = id == null ? "" : id.toString();
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    private static Object invokeOneArg(Object target, String name, Object argument) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method.invoke(target, argument);
            }
        }
        return null;
    }
}
