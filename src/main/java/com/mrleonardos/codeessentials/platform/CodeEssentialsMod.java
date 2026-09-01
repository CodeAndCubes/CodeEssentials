package com.mrleonardos.codeessentials.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeessentials.Tags;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = "codeessentials",
    name = "CodeEssentials",
    version = Tags.VERSION,
    dependencies = "required-after:codecore",
    acceptableRemoteVersions = "*")
public final class CodeEssentialsMod {

    public static final Logger LOG = LogManager.getLogger("CodeEssentials");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEssentials {} is starting up", Tags.VERSION);
    }
}
