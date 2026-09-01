package com.mrleonardos.codeessentials.internal.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.teleport.TeleportJob;

public interface TeleportRequests {

    enum Answer {

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

    final class Reply {

        private final Answer answer;
        private final String subject;
        private final List<String> names;
        private final long waitMillis;
        private final UUID moved;
        private final TeleportJob job;

        private Reply(Answer answer, String subject, List<String> names, long waitMillis, UUID moved, TeleportJob job) {
            this.answer = answer;
            this.subject = subject;
            this.names = names;
            this.waitMillis = waitMillis;
            this.moved = moved;
            this.job = job;
        }

        public static Reply of(Answer answer, String subject) {
            Objects.requireNonNull(answer, "answer");
            return new Reply(answer, subject == null ? "" : subject, Collections.<String>emptyList(), 0L, null, null);
        }

        public static Reply plain(Answer answer) {
            return of(answer, "");
        }

        public static Reply accepted(String subject, UUID moved, TeleportJob job) {
            return new Reply(
                Answer.ACCEPTED,
                subject == null ? "" : subject,
                Collections.<String>emptyList(),
                0L,
                moved,
                job);
        }

        public static Reply ambiguous(List<String> names) {
            return new Reply(
                Answer.AMBIGUOUS,
                "",
                Collections.unmodifiableList(new ArrayList<>(names)),
                0L,
                null,
                null);
        }

        public static Reply waiting(long waitMillis) {
            return new Reply(
                Answer.RATE_LIMITED,
                "",
                Collections.<String>emptyList(),
                Math.max(0L, waitMillis),
                null,
                null);
        }

        public Answer answer() {
            return answer;
        }

        public String subject() {
            return subject;
        }

        public List<String> names() {
            return names;
        }

        public long waitMillis() {
            return waitMillis;
        }

        public Optional<UUID> moved() {
            return Optional.ofNullable(moved);
        }

        public Optional<TeleportJob> job() {
            return Optional.ofNullable(job);
        }

        @Override
        public String toString() {
            return answer + (subject.isEmpty() ? "" : " " + subject);
        }
    }

    Reply send(UUID from, String fromName, UUID to, String toName, boolean here);

    Reply accept(UUID target, String fromName);

    Reply deny(UUID target, String fromName);

    Reply cancel(UUID from);

    boolean toggle(UUID player);
}
