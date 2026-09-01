package com.mrleonardos.codeessentials.internal.command;

import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;

public final class EssentialsMessages {

    public static final String USAGE_HOME = "codeessentials.command.usage.home";
    public static final String USAGE_SETHOME = "codeessentials.command.usage.sethome";
    public static final String USAGE_DELHOME = "codeessentials.command.usage.delhome";
    public static final String USAGE_HOMES = "codeessentials.command.usage.homes";
    public static final String USAGE_WARP = "codeessentials.command.usage.warp";
    public static final String USAGE_WARPS = "codeessentials.command.usage.warps";
    public static final String USAGE_SETWARP = "codeessentials.command.usage.setwarp";
    public static final String USAGE_DELWARP = "codeessentials.command.usage.delwarp";
    public static final String USAGE_SPAWN = "codeessentials.command.usage.spawn";
    public static final String USAGE_SETSPAWN = "codeessentials.command.usage.setspawn";
    public static final String USAGE_BACK = "codeessentials.command.usage.back";
    public static final String USAGE_TPA = "codeessentials.command.usage.tpa";
    public static final String USAGE_TPAHERE = "codeessentials.command.usage.tpahere";
    public static final String USAGE_TPACCEPT = "codeessentials.command.usage.tpaccept";
    public static final String USAGE_TPDENY = "codeessentials.command.usage.tpdeny";
    public static final String USAGE_TPACANCEL = "codeessentials.command.usage.tpacancel";
    public static final String USAGE_TPATOGGLE = "codeessentials.command.usage.tpatoggle";
    public static final String USAGE_TP = "codeessentials.command.usage.tp";
    public static final String USAGE_TPPOS = "codeessentials.command.usage.tppos";
    public static final String USAGE_ECANCEL = "codeessentials.command.usage.ecancel";
    public static final String USAGE_ESSENTIALS = "codeessentials.command.usage.essentials";

    public static final String ERROR_SENDER_NOT_PLAYER = "codeessentials.error.sender_not_player";
    public static final String ERROR_INVALID_NAME = "codeessentials.error.invalid_name";
    public static final String ERROR_UNKNOWN_HOME = "codeessentials.error.unknown_home";
    public static final String ERROR_UNKNOWN_WARP = "codeessentials.error.unknown_warp";
    public static final String ERROR_UNKNOWN_PLAYER = "codeessentials.error.unknown_player";
    public static final String ERROR_PLAYER_OFFLINE = "codeessentials.error.player_offline";
    public static final String ERROR_NO_HOMES = "codeessentials.error.no_homes";
    public static final String ERROR_NO_SPAWN = "codeessentials.error.no_spawn";
    public static final String ERROR_NO_BACK = "codeessentials.error.no_back";
    public static final String ERROR_NO_JOB = "codeessentials.error.no_job";
    public static final String ERROR_NO_REQUEST = "codeessentials.error.no_request";
    public static final String ERROR_NO_OUTGOING = "codeessentials.error.no_outgoing";
    public static final String ERROR_MANY_REQUESTS = "codeessentials.error.many_requests";
    public static final String ERROR_REQUESTS_CLOSED = "codeessentials.error.requests_closed";
    public static final String ERROR_SELF_TARGET = "codeessentials.error.self_target";
    public static final String ERROR_COOLDOWN = "codeessentials.error.cooldown";
    public static final String ERROR_WARP_DENIED = "codeessentials.error.warp_denied";
    public static final String ERROR_BACK_DEATH_DENIED = "codeessentials.error.back_death_denied";
    public static final String ERROR_BACK_CROSSWORLD_DENIED = "codeessentials.error.back_crossworld_denied";
    public static final String ERROR_BAD_ARGUMENTS = "codeessentials.error.bad_arguments";
    public static final String ERROR_HOME_LIMIT = "codeessentials.error.home_limit";
    public static final String ERROR_NOT_FOUND = "codeessentials.error.not_found";
    public static final String ERROR_ALREADY_EXISTS = "codeessentials.error.already_exists";
    public static final String ERROR_LIMIT_REACHED = "codeessentials.error.limit_reached";
    public static final String ERROR_INVALID_VALUE = "codeessentials.error.invalid_value";
    public static final String ERROR_UNSAFE_SPOT = "codeessentials.error.unsafe_spot";
    public static final String ERROR_VETOED = "codeessentials.error.vetoed";
    public static final String ERROR_TIMEOUT = "codeessentials.error.timeout";
    public static final String ERROR_UNSUPPORTED = "codeessentials.error.unsupported";
    public static final String ERROR_PROVIDER_FAILED = "codeessentials.error.provider_failed";

    public static final String HOMES = "codeessentials.message.homes";
    public static final String HOMES_EMPTY = "codeessentials.message.homes_empty";
    public static final String HOME_SET = "codeessentials.message.home_set";
    public static final String HOME_MOVED = "codeessentials.message.home_moved";
    public static final String HOME_DELETED = "codeessentials.message.home_deleted";
    public static final String WARPS = "codeessentials.message.warps";
    public static final String WARPS_EMPTY = "codeessentials.message.warps_empty";
    public static final String WARP_SET = "codeessentials.message.warp_set";
    public static final String WARP_MOVED = "codeessentials.message.warp_moved";
    public static final String WARP_DELETED = "codeessentials.message.warp_deleted";
    public static final String SPAWN_GLOBAL_SET = "codeessentials.message.spawn_global_set";
    public static final String SPAWN_DIMENSION_SET = "codeessentials.message.spawn_dimension_set";
    public static final String WARMUP = "codeessentials.message.warmup";
    public static final String CORRECTED = "codeessentials.message.corrected";
    public static final String MOVED = "codeessentials.message.moved";
    public static final String REQUEST_SENT = "codeessentials.message.request_sent";
    public static final String REQUEST_INCOMING = "codeessentials.message.request_incoming";
    public static final String REQUEST_ACCEPTED = "codeessentials.message.request_accepted";
    public static final String REQUEST_DENIED = "codeessentials.message.request_denied";
    public static final String REQUEST_WITHDRAWN = "codeessentials.message.request_withdrawn";
    public static final String REQUESTS_ON = "codeessentials.message.requests_on";
    public static final String REQUESTS_OFF = "codeessentials.message.requests_off";
    public static final String CANCELLED = "codeessentials.message.cancelled";
    public static final String CANCELLED_OTHER = "codeessentials.message.cancelled_other";
    public static final String RELOAD_DONE = "codeessentials.message.reload_done";
    public static final String COOLDOWNS = "codeessentials.message.cooldowns";
    public static final String COOLDOWNS_EMPTY = "codeessentials.message.cooldowns_empty";
    public static final String COOLDOWNS_CLEARED = "codeessentials.message.cooldowns_cleared";
    public static final String JOBS = "codeessentials.message.jobs";
    public static final String JOBS_EMPTY = "codeessentials.message.jobs_empty";
    public static final String BRANCHES = "codeessentials.message.branches";

    public static final String CANCEL_SUPERSEDED = "codeessentials.cancel.superseded";
    public static final String CANCEL_MOVED = "codeessentials.cancel.moved";
    public static final String CANCEL_DAMAGED = "codeessentials.cancel.damaged";
    public static final String CANCEL_DEAD = "codeessentials.cancel.dead";
    public static final String CANCEL_DISCONNECTED = "codeessentials.cancel.disconnected";
    public static final String CANCEL_VETOED = "codeessentials.cancel.vetoed";
    public static final String CANCEL_BY_COMMAND = "codeessentials.cancel.by_command";

    public static final String FAILED_UNSAFE = "codeessentials.failed.unsafe";
    public static final String FAILED_CHUNK_MISSING = "codeessentials.failed.chunk_missing";
    public static final String FAILED_DIMENSION_MISSING = "codeessentials.failed.dimension_missing";
    public static final String FAILED_TIMEOUT = "codeessentials.failed.timeout";

    private static final String CANCEL_PREFIX = "codeessentials.cancel.";
    private static final String FAILED_PREFIX = "codeessentials.failed.";

    private EssentialsMessages() {}

    public static String outcomeKey(CancelReason reason) {
        return (reason.failure() ? FAILED_PREFIX : CANCEL_PREFIX) + reason.key();
    }

    public static String failureKey(StoreResult result) {
        StoreResult.Failure failure = result.failure()
            .orElse(StoreResult.Failure.UNSUPPORTED);
        switch (failure) {
            case NOT_FOUND:
                return ERROR_NOT_FOUND;
            case ALREADY_EXISTS:
                return ERROR_ALREADY_EXISTS;
            case LIMIT_REACHED:
                return ERROR_LIMIT_REACHED;
            case INVALID_VALUE:
                return ERROR_INVALID_VALUE;
            case UNSAFE_SPOT:
                return ERROR_UNSAFE_SPOT;
            case VETOED:
                return ERROR_VETOED;
            case TIMEOUT:
                return ERROR_TIMEOUT;
            case PROVIDER_FAILED:
                return ERROR_PROVIDER_FAILED;
            default:
                return ERROR_UNSUPPORTED;
        }
    }
}
