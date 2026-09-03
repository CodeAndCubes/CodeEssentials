package com.mrleonardos.codeessentials.api;

import com.mrleonardos.codecore.api.adapter.RoleCapability;

/**
 * Умения роли {@code essentials}: перечень объявляет CodeEssentials, потому что роль объявляет он.
 *
 * <p>
 * Константы лежат в api, а не рядом с объявлением роли, ровно затем, чтобы их было чем назвать снаружи.
 * Чужой мод спрашивает {@code CodeApi.adapters().missing(ConfigRoles.ESSENTIALS).contains(TPA)} и не лезет
 * за строкой во внутренности. По той же причине заявка на роль называет своё подмножество этими же
 * константами, а не повторяет строки: две копии перечня расходятся молча, и опечатка в любой из них делает
 * умение навсегда недостающим.
 *
 * <p>
 * Строка умения совпадает с именем корневой команды, а не с именем константы: админ видит её в
 * {@code /codecore adapters} и набирает в чате.
 */
public final class EssentialsCapabilities {

    /** Дома игрока: {@code /home}, {@code /sethome}, {@code /delhome}, {@code /homes}. */
    public static final RoleCapability HOMES = RoleCapability.of("homes");

    /** Общие точки: {@code /warp}, {@code /setwarp}, {@code /delwarp}, {@code /warps}. */
    public static final RoleCapability WARPS = RoleCapability.of("warps");

    /** Спавн мира и точка входа новичка: {@code /spawn}, {@code /setspawn}. */
    public static final RoleCapability SPAWN = RoleCapability.of("spawn");

    /** Возврат в место, откуда игрока унесло: {@code /back}. */
    public static final RoleCapability BACK = RoleCapability.of("back");

    /** Запросы телепорта между игроками: {@code /tpa}, {@code /tpahere}, {@code /tpaccept}. */
    public static final RoleCapability TPA = RoleCapability.of("tpa");

    /** Перенос в случайную точку мира: {@code /rtp}. */
    public static final RoleCapability RANDOM = RoleCapability.of("rtp");

    private EssentialsCapabilities() {}
}
