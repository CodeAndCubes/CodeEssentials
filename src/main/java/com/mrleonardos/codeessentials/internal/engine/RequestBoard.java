package com.mrleonardos.codeessentials.internal.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

public final class RequestBoard {

    public enum Kind {

        TO_TARGET,
        HERE
    }

    public enum Outcome {

        SENT,
        ACCEPTED,
        DENIED,
        CANCELLED,
        NONE,
        AMBIGUOUS,
        SELF,
        BLOCKED,
        RATE_LIMITED,
        OFFLINE
    }

    private final TeleportService teleports;
    private final WorldAccess world;
    private final Cooldowns cooldowns;
    private final Supplier<EngineRules> rules;
    private final Scheduler scheduler;
    private final LongSupplier clock;

    private final Map<UUID, List<Ticket>> incoming = new LinkedHashMap<>();
    private final Set<UUID> muted = new LinkedHashSet<>();
    private final AtomicLong ids = new AtomicLong();

    private volatile boolean running;

    public RequestBoard(TeleportService teleports, WorldAccess world, Cooldowns cooldowns, Supplier<EngineRules> rules,
        Scheduler scheduler, LongSupplier clock) {
        this.teleports = teleports;
        this.world = world;
        this.cooldowns = cooldowns;
        this.rules = rules;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public void start() {
        running = true;
        schedule();
    }

    public void stop() {
        running = false;
        incoming.clear();
        muted.clear();
    }

    public Answer send(UUID from, String fromName, UUID to, String toName, Kind kind) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        if (from.equals(to)) {
            return Answer.plain(Outcome.SELF);
        }
        if (muted.contains(to)) {
            return Answer.plain(Outcome.BLOCKED);
        }
        long wait = cooldowns.remainingRequest(from);
        if (wait > 0L) {
            return Answer.waiting(wait);
        }
        expire(clock.getAsLong());
        List<Ticket> pending = incoming.computeIfAbsent(to, key -> new ArrayList<Ticket>());
        drop(pending, from);
        while (pending.size() >= rules.get()
            .maxPending()) {
            pending.remove(0);
        }
        Ticket ticket = new Ticket(
            ids.incrementAndGet(),
            from,
            fromName,
            to,
            toName,
            kind,
            clock.getAsLong() + EngineRules.millis(
                rules.get()
                    .requestTimeoutSeconds()));
        pending.add(ticket);
        cooldowns.chargeRequest(from);
        return Answer.of(Outcome.SENT, ticket);
    }

    public Answer accept(UUID target, String fromName) {
        Answer picked = pick(target, fromName);
        if (picked.outcome() != Outcome.ACCEPTED) {
            return picked;
        }
        Ticket ticket = picked.ticket()
            .get();
        UUID moved = ticket.kind() == Kind.TO_TARGET ? ticket.from() : ticket.to();
        UUID host = ticket.kind() == Kind.TO_TARGET ? ticket.to() : ticket.from();
        Optional<Point> destination = world.position(host);
        if (!destination.isPresent()) {
            return Answer.plain(Outcome.OFFLINE);
        }
        TeleportJob job = teleports.request(
            TeleportRequest.builder(moved, destination.get(), TeleportCause.TPA)
                .actor(ticket.toName() == null ? target.toString() : ticket.toName())
                .build());
        return Answer.accepted(ticket, job);
    }

    public Answer deny(UUID target, String fromName) {
        Answer picked = pick(target, fromName);
        return picked.outcome() == Outcome.ACCEPTED ? Answer.of(
            Outcome.DENIED,
            picked.ticket()
                .get())
            : picked;
    }

    public Answer cancel(UUID from) {
        expire(clock.getAsLong());
        Ticket removed = null;
        for (List<Ticket> pending : incoming.values()) {
            for (Iterator<Ticket> tickets = pending.iterator(); tickets.hasNext();) {
                Ticket ticket = tickets.next();
                if (ticket.from()
                    .equals(from)) {
                    tickets.remove();
                    removed = ticket;
                }
            }
        }
        return removed == null ? Answer.plain(Outcome.NONE) : Answer.of(Outcome.CANCELLED, removed);
    }

    public boolean toggle(UUID player) {
        if (muted.remove(player)) {
            return true;
        }
        muted.add(player);
        incoming.remove(player);
        return false;
    }

    public void left(UUID player) {
        muted.remove(player);
        incoming.remove(player);
        for (List<Ticket> pending : incoming.values()) {
            drop(pending, player);
        }
    }

    void expire(long now) {
        for (Iterator<Map.Entry<UUID, List<Ticket>>> entries = incoming.entrySet()
            .iterator(); entries.hasNext();) {
            Map.Entry<UUID, List<Ticket>> entry = entries.next();
            for (Iterator<Ticket> tickets = entry.getValue()
                .iterator(); tickets.hasNext();) {
                if (tickets.next()
                    .expiresAt() <= now) {
                    tickets.remove();
                }
            }
            if (entry.getValue()
                .isEmpty()) {
                entries.remove();
            }
        }
    }

    private Answer pick(UUID target, String fromName) {
        expire(clock.getAsLong());
        List<Ticket> pending = incoming.get(target);
        if (pending == null || pending.isEmpty()) {
            return Answer.plain(Outcome.NONE);
        }
        Ticket ticket = null;
        if (fromName == null) {
            if (pending.size() > 1) {
                return Answer.ambiguous(names(pending));
            }
            ticket = pending.get(0);
        } else {
            for (Ticket held : pending) {
                if (fromName.equalsIgnoreCase(held.fromName())) {
                    ticket = held;
                    break;
                }
            }
        }
        if (ticket == null) {
            return Answer.plain(Outcome.NONE);
        }
        pending.remove(ticket);
        if (pending.isEmpty()) {
            incoming.remove(target);
        }
        return Answer.of(Outcome.ACCEPTED, ticket);
    }

    private void schedule() {
        if (running) {
            scheduler.afterTicks(
                rules.get()
                    .sweepTicks(),
                this::sweep);
        }
    }

    private void sweep() {
        try {
            expire(clock.getAsLong());
        } finally {
            schedule();
        }
    }

    private static void drop(List<Ticket> pending, UUID from) {
        for (Iterator<Ticket> tickets = pending.iterator(); tickets.hasNext();) {
            if (tickets.next()
                .from()
                .equals(from)) {
                tickets.remove();
            }
        }
    }

    private static List<String> names(List<Ticket> pending) {
        List<String> found = new ArrayList<>();
        for (Ticket ticket : pending) {
            found.add(
                ticket.fromName() == null ? ticket.from()
                    .toString() : ticket.fromName());
        }
        return found;
    }

    public static final class Ticket {

        private final long id;
        private final UUID from;
        private final String fromName;
        private final UUID to;
        private final String toName;
        private final Kind kind;
        private final long expiresAt;

        Ticket(long id, UUID from, String fromName, UUID to, String toName, Kind kind, long expiresAt) {
            this.id = id;
            this.from = from;
            this.fromName = fromName;
            this.to = to;
            this.toName = toName;
            this.kind = kind;
            this.expiresAt = expiresAt;
        }

        public long id() {
            return id;
        }

        public UUID from() {
            return from;
        }

        public String fromName() {
            return fromName;
        }

        public UUID to() {
            return to;
        }

        public String toName() {
            return toName;
        }

        public Kind kind() {
            return kind;
        }

        public long expiresAt() {
            return expiresAt;
        }

        @Override
        public String toString() {
            return kind + " " + fromName + " -> " + toName;
        }
    }

    public static final class Answer {

        private final Outcome outcome;
        private final Ticket ticket;
        private final List<String> names;
        private final long waitMillis;
        private final TeleportJob job;

        private Answer(Outcome outcome, Ticket ticket, List<String> names, long waitMillis, TeleportJob job) {
            this.outcome = outcome;
            this.ticket = ticket;
            this.names = names;
            this.waitMillis = waitMillis;
            this.job = job;
        }

        static Answer plain(Outcome outcome) {
            return new Answer(outcome, null, Collections.<String>emptyList(), 0L, null);
        }

        static Answer of(Outcome outcome, Ticket ticket) {
            return new Answer(outcome, ticket, Collections.<String>emptyList(), 0L, null);
        }

        static Answer accepted(Ticket ticket, TeleportJob job) {
            return new Answer(Outcome.ACCEPTED, ticket, Collections.<String>emptyList(), 0L, job);
        }

        static Answer ambiguous(List<String> names) {
            return new Answer(Outcome.AMBIGUOUS, null, Collections.unmodifiableList(names), 0L, null);
        }

        static Answer waiting(long waitMillis) {
            return new Answer(Outcome.RATE_LIMITED, null, Collections.<String>emptyList(), waitMillis, null);
        }

        public Outcome outcome() {
            return outcome;
        }

        public Optional<Ticket> ticket() {
            return Optional.ofNullable(ticket);
        }

        public List<String> names() {
            return names;
        }

        public long waitMillis() {
            return waitMillis;
        }

        public Optional<TeleportJob> job() {
            return Optional.ofNullable(job);
        }

        @Override
        public String toString() {
            return outcome + (ticket == null ? "" : " " + ticket);
        }
    }
}
