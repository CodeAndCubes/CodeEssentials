package com.mrleonardos.codeessentials.platform;

import java.util.UUID;

import com.mrleonardos.codeessentials.internal.command.TeleportRequests;
import com.mrleonardos.codeessentials.internal.engine.RequestBoard;

final class RequestBridge implements TeleportRequests {

    private final RequestBoard board;

    RequestBridge(RequestBoard board) {
        this.board = board;
    }

    @Override
    public Reply send(UUID from, String fromName, UUID to, String toName, boolean here) {
        RequestBoard.Kind kind = here ? RequestBoard.Kind.HERE : RequestBoard.Kind.TO_TARGET;
        return reply(board.send(from, fromName, to, toName, kind), toName);
    }

    @Override
    public Reply accept(UUID target, String fromName) {
        return reply(board.accept(target, fromName), fromName);
    }

    @Override
    public Reply deny(UUID target, String fromName) {
        return reply(board.deny(target, fromName), fromName);
    }

    @Override
    public Reply cancel(UUID from) {
        return reply(board.cancel(from), "");
    }

    @Override
    public boolean toggle(UUID player) {
        return board.toggle(player);
    }

    private static Reply reply(RequestBoard.Answer answer, String fallback) {
        switch (answer.outcome()) {
            case SENT:
                return Reply.of(Answer.SENT, subject(answer, fallback, false));
            case ACCEPTED:
                return Reply.accepted(
                    subject(answer, fallback, true),
                    answer.ticket()
                        .map(RequestBoard.Ticket::moved)
                        .orElse(null),
                    answer.job()
                        .orElse(null));
            case DENIED:
                return Reply.of(Answer.DENIED, subject(answer, fallback, true));
            case CANCELLED:
                return Reply.of(Answer.CANCELLED, subject(answer, fallback, false));
            case AMBIGUOUS:
                return Reply.ambiguous(answer.names());
            case RATE_LIMITED:
                return Reply.waiting(answer.waitMillis());
            case SELF:
                return Reply.of(Answer.SELF, fallback);
            case BLOCKED:
                return Reply.of(Answer.BLOCKED, fallback);
            case OFFLINE:
                return Reply.of(Answer.OFFLINE, fallback);
            default:
                return Reply.of(Answer.NONE, fallback);
        }
    }

    private static String subject(RequestBoard.Answer answer, String fallback, boolean asker) {
        RequestBoard.Ticket ticket = answer.ticket()
            .orElse(null);
        if (ticket == null) {
            return fallback;
        }
        String name = asker ? ticket.fromName() : ticket.toName();
        return name == null || name.isEmpty() ? fallback : name;
    }
}
