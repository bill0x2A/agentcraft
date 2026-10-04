package dev.agentcraft;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Namespace retained for the MIT-licensed AgentCraft studio assets. No Foreman is started. */
public final class AgentCraft {
    public static final String MOD_ID = "agentcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
