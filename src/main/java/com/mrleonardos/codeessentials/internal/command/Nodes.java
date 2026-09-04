package com.mrleonardos.codeessentials.internal.command;

public final class Nodes {

    public static final String HOME = "codeessentials.home";
    public static final String HOME_SET = "codeessentials.home.set";
    public static final String HOME_DELETE = "codeessentials.home.delete";

    public static final String WARP = "codeessentials.warp";
    public static final String WARP_GO = "codeessentials.warp.go.";
    public static final String WARP_SET = "codeessentials.warp.set";
    public static final String WARP_DELETE = "codeessentials.warp.delete";

    public static final String SPAWN = "codeessentials.spawn";
    public static final String SPAWN_SET = "codeessentials.spawn.set";

    public static final String BACK = "codeessentials.back";
    public static final String BACK_DEATH = "codeessentials.back.death";
    public static final String BACK_CROSSWORLD = "codeessentials.back.crossworld";

    public static final String RANDOM = "codeessentials.rtp";

    public static final String KIT = "codeessentials.kit.";
    public static final String KIT_ADMIN = "codeessentials.admin.kit";

    public static final String TPA = "codeessentials.tpa";
    public static final String TPA_HERE = "codeessentials.tpa.here";
    public static final String TPA_TOGGLE = "codeessentials.tpa.toggle";

    public static final String ADMIN_TELEPORT = "codeessentials.admin.teleport";
    public static final String ADMIN_RELOAD = "codeessentials.admin.reload";
    public static final String ADMIN_COOLDOWN = "codeessentials.admin.cooldown";
    public static final String ADMIN_JOBS = "codeessentials.admin.jobs";

    public static final String BYPASS_COOLDOWN = "codeessentials.bypass.cooldown";

    private Nodes() {}

    public static String warpGo(String warp) {
        return WARP_GO + warp;
    }

    public static String kit(String kit) {
        return KIT + kit;
    }
}
