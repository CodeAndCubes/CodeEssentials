package com.mrleonardos.codeessentials.api.teleport;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.Point;

/**
 * Работа переноса: одна просьба на всём пути от подачи до приземления или отказа.
 *
 * <p>
 * Каждый переход отдаёт новую работу, а не правит прежнюю. Отчёт о переносе после этого нельзя
 * переписать задним числом, и слушатель, увидевший {@code DONE} с точкой приземления, держит в
 * руках именно то, что случилось.
 *
 * <p>
 * Незаконный переход это ошибка в движке, а не отказ игроку, поэтому он приходит исключением.
 * Отказы игроку приходят состоянием {@code CANCELLED} или {@code FAILED} с причиной.
 */
public final class TeleportJob {

    /** Где работа находится. */
    public enum State {

        /** Идёт тёплая задержка, игрока ещё можно отпустить без последствий. */
        WARMUP,

        /** Перенос начат: чанки грузятся, игрок вот-вот сменит место. */
        MOVING,

        /** Игрок перенесён. */
        DONE,

        /** Перенос снят игроком, модом или самим игроком по неосторожности. */
        CANCELLED,

        /** Перенос не вышел из-за мира: нет безопасного места, чанка или измерения. */
        FAILED
    }

    private final long id;
    private final TeleportRequest request;
    private final State state;
    private final CancelReason reason;
    private final Point landing;

    private TeleportJob(long id, TeleportRequest request, State state, CancelReason reason, Point landing) {
        this.id = id;
        this.request = request;
        this.state = state;
        this.reason = reason;
        this.landing = landing;
    }

    /** Завести работу в состоянии {@code WARMUP}. Перенос без задержки сразу зовёт {@link #moving()}. */
    public static TeleportJob starting(long id, TeleportRequest request) {
        Objects.requireNonNull(request, "request");
        return new TeleportJob(id, request, State.WARMUP, null, null);
    }

    /** Номер работы, растёт в порядке подачи просьб. */
    public long id() {
        return id;
    }

    /** Исходная просьба. */
    public TeleportRequest request() {
        return request;
    }

    /** Кого несём. */
    public UUID player() {
        return request.player();
    }

    /** Почему несём. */
    public TeleportCause cause() {
        return request.cause();
    }

    /** Где работа находится. */
    public State state() {
        return state;
    }

    /** Правда ли работа ещё идёт. */
    public boolean active() {
        return state == State.WARMUP || state == State.MOVING;
    }

    /** Правда ли работа закончилась, чем угодно. */
    public boolean finished() {
        return !active();
    }

    /** Правда ли игрок перенесён. */
    public boolean applied() {
        return state == State.DONE;
    }

    /** Причина отказа или пустой ответ. */
    public Optional<CancelReason> reason() {
        return Optional.ofNullable(reason);
    }

    /** Куда игрок встал на самом деле, или пустой ответ, пока перенос не случился. */
    public Optional<Point> landing() {
        return Optional.ofNullable(landing);
    }

    /** Правда ли точку приземления поправил поиск безопасного места. */
    public boolean corrected() {
        return landing != null && !landing.equals(request.destination());
    }

    /**
     * Начать перенос.
     *
     * @throws IllegalStateException если работа уже не в {@code WARMUP}
     */
    public TeleportJob moving() {
        if (state != State.WARMUP) {
            throw new IllegalStateException("Only a warming up job starts moving, this one is " + state);
        }
        return new TeleportJob(id, request, State.MOVING, null, null);
    }

    /**
     * Закончить перенос.
     *
     * @param spot куда игрок встал, с учётом поправки безопасного места
     * @throws IllegalStateException если работа не в {@code MOVING}
     */
    public TeleportJob done(Point spot) {
        Objects.requireNonNull(spot, "spot");
        if (state != State.MOVING) {
            throw new IllegalStateException("Only a moving job lands, this one is " + state);
        }
        return new TeleportJob(id, request, State.DONE, null, spot);
    }

    /**
     * Снять работу: игрок остаётся на месте, кулдаун не списывается, стек возврата не трогается.
     *
     * @throws IllegalStateException если работа уже закончилась
     */
    public TeleportJob stopped(CancelReason cancelReason) {
        Objects.requireNonNull(cancelReason, "cancelReason");
        if (finished()) {
            throw new IllegalStateException("Job " + id + " has already finished as " + state);
        }
        return new TeleportJob(id, request, cancelReason.state(), cancelReason, null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeleportJob)) {
            return false;
        }
        TeleportJob that = (TeleportJob) other;
        return id == that.id && state == that.state
            && reason == that.reason
            && request.equals(that.request)
            && Objects.equals(landing, that.landing);
    }

    @Override
    public int hashCode() {
        return (((Long.hashCode(id) * 31 + request.hashCode()) * 31 + state.hashCode()) * 31 + Objects.hashCode(reason))
            * 31 + Objects.hashCode(landing);
    }

    @Override
    public String toString() {
        return "#" + id + " " + state + (reason == null ? "" : " " + reason) + " " + request;
    }
}
