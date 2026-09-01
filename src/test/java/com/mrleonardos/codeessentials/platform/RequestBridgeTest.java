package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.internal.command.TeleportRequests;
import com.mrleonardos.codeessentials.internal.engine.Cooldowns;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.RequestBoard;

class RequestBridgeTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID NOTCH = UUID.fromString("00000000-0000-0000-0000-000000000023");

    private final PlatformStubs.Worlds worlds = new PlatformStubs.Worlds();
    private final PlatformStubs.Teleports teleports = new PlatformStubs.Teleports();
    private long now = 1_000L;

    private RequestBoard board;
    private TeleportRequests bridge;

    @BeforeEach
    void setUp() {
        worlds.put(STEVE, Point.of(0, 1.0D, 64.0D, 1.0D));
        worlds.put(ALEX, Point.of(0, 20.0D, 64.0D, 20.0D));
        worlds.put(NOTCH, Point.of(0, 40.0D, 64.0D, 40.0D));
        build(rules(0));
    }

    @Test
    void aSentRequestNamesTheAddressee() {
        TeleportRequests.Reply reply = bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        assertEquals(TeleportRequests.Answer.SENT, reply.answer());
        assertEquals("Alex", reply.subject());
    }

    @Test
    void askingYourselfIsRefused() {
        TeleportRequests.Reply reply = bridge.send(STEVE, "Steve", STEVE, "Steve", false);

        assertEquals(TeleportRequests.Answer.SELF, reply.answer());
    }

    @Test
    void aClosedAddresseeIsNamedInTheRefusal() {
        bridge.toggle(ALEX);

        TeleportRequests.Reply reply = bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        assertEquals(TeleportRequests.Answer.BLOCKED, reply.answer());
        assertEquals("Alex", reply.subject());
    }

    @Test
    void theSecondRequestInARowWaitsForTheRate() {
        build(rules(10));
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.send(STEVE, "Steve", NOTCH, "Notch", false);

        assertEquals(TeleportRequests.Answer.RATE_LIMITED, reply.answer());
        assertTrue(reply.waitMillis() > 0L, () -> "оставшееся время должно быть положительным: " + reply.waitMillis());
    }

    @Test
    void twoIncomingRequestsAnswerWithTheListOfNames() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);
        bridge.send(NOTCH, "Notch", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.accept(ALEX, null);

        assertEquals(TeleportRequests.Answer.AMBIGUOUS, reply.answer());
        assertEquals(java.util.Arrays.asList("Steve", "Notch"), reply.names());
    }

    @Test
    void acceptingByNameNamesTheAsker() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);
        bridge.send(NOTCH, "Notch", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.accept(ALEX, "Steve");

        assertEquals(TeleportRequests.Answer.ACCEPTED, reply.answer());
        assertEquals("Steve", reply.subject());
        assertEquals(
            1,
            teleports.asked()
                .size());
        assertEquals(
            STEVE,
            teleports.asked()
                .get(0)
                .player());
    }

    @Test
    void anAcceptedTpaCarriesTheAskerAndHisJobBackToTheCommand() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.accept(ALEX, "Steve");

        assertEquals(
            STEVE,
            reply.moved()
                .get(),
            "по /tpa идёт проситель");
        assertEquals(
            STEVE,
            reply.job()
                .get()
                .player());
    }

    @Test
    void anAcceptedTpaHereCarriesTheAddresseeAndHisJob() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", true);

        TeleportRequests.Reply reply = bridge.accept(ALEX, "Steve");

        assertEquals(
            ALEX,
            reply.moved()
                .get(),
            "по /tpahere идёт адресат");
        assertEquals(
            ALEX,
            reply.job()
                .get()
                .player());
        assertEquals(
            Point.of(0, 1.0D, 64.0D, 1.0D),
            teleports.asked()
                .get(0)
                .destination(),
            "адресат идёт к просителю");
    }

    @Test
    void denyingByNameNamesTheAskerAndMovesNobody() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.deny(ALEX, "Steve");

        assertEquals(TeleportRequests.Answer.DENIED, reply.answer());
        assertEquals("Steve", reply.subject());
        assertTrue(
            teleports.asked()
                .isEmpty());
    }

    @Test
    void withdrawingWithoutAnOutgoingRequestAnswersNone() {
        assertEquals(
            TeleportRequests.Answer.NONE,
            bridge.cancel(STEVE)
                .answer());
    }

    @Test
    void withdrawingNamesTheAddressee() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);

        TeleportRequests.Reply reply = bridge.cancel(STEVE);

        assertEquals(TeleportRequests.Answer.CANCELLED, reply.answer());
        assertEquals("Alex", reply.subject());
    }

    @Test
    void anAbsentHostStopsTheAcceptedRequest() {
        bridge.send(STEVE, "Steve", ALEX, "Alex", false);
        worlds.gone(ALEX);

        TeleportRequests.Reply reply = bridge.accept(ALEX, "Steve");

        assertEquals(TeleportRequests.Answer.OFFLINE, reply.answer());
        assertTrue(
            teleports.asked()
                .isEmpty());
    }

    @Test
    void toggleTellsWhetherRequestsAreAcceptedAgain() {
        assertEquals(false, bridge.toggle(ALEX));
        assertEquals(true, bridge.toggle(ALEX));
    }

    private void build(EngineRules rules) {
        Cooldowns cooldowns = new Cooldowns(
            new PlatformStubs.Memory(),
            new PlatformStubs.Rights(),
            () -> rules,
            () -> now);
        board = new RequestBoard(teleports, worlds, cooldowns, () -> rules, new PlatformStubs.Now(), () -> now);
        bridge = new RequestBridge(board);
    }

    private static EngineRules rules(int rateSeconds) {
        return EngineRules.builder()
            .requestRateSeconds(rateSeconds)
            .requestTimeoutSeconds(60)
            .maxPending(8)
            .build();
    }
}
