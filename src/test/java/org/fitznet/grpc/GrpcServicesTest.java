package org.fitznet.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.fitznet.grpc.proto.BotServiceGrpc;
import org.fitznet.grpc.proto.GetLogsByLevelRequest;
import org.fitznet.grpc.proto.GetStatusRequest;
import org.fitznet.grpc.proto.GuildRequest;
import org.fitznet.grpc.proto.LogResponse;
import org.fitznet.grpc.proto.LogServiceGrpc;
import org.fitznet.grpc.proto.SearchLogsRequest;
import org.fitznet.grpc.proto.ShutdownRequest;
import org.fitznet.grpc.proto.TailLogsRequest;
import org.fitznet.service.BotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "discord.bot.token=test-token")
@Execution(ExecutionMode.SAME_THREAD)
class GrpcServicesTest {

    @TempDir
    static Path logDir;

    @MockitoBean
    private BotService botService;

    @LocalGrpcServerPort
    private int port;

    private ManagedChannel channel;

    @BeforeAll
    static void writeLog() throws Exception {
        Files.write(logDir.resolve("bot.log"), List.of(
                "10:00 INFO started",
                "10:01 WARN slow gateway",
                "10:02 INFO Joined voice",
                "10:03 ERROR boom"));
    }

    @DynamicPropertySource
    static void logFile(DynamicPropertyRegistry registry) {
        registry.add("logging.file.name", () -> logDir.resolve("bot.log").toString());
    }

    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
    }

    @Test
    void botRpcsDelegateToBotService() {
        var bot = BotServiceGrpc.newBlockingStub(channel);
        when(botService.getStatus()).thenReturn("Bot is running.");
        when(botService.stopBot()).thenReturn("Bot is shutting down.");
        when(botService.resetGuildJoinCounts("123")).thenReturn("Reset join counts for 2 users");

        assertEquals("Bot is running.", bot.getStatus(GetStatusRequest.getDefaultInstance()).getMessage());
        assertEquals("Bot is shutting down.", bot.shutdown(ShutdownRequest.getDefaultInstance()).getMessage());
        assertEquals("Reset join counts for 2 users",
                bot.resetJoinCounts(GuildRequest.newBuilder().setGuildId("123").build()).getMessage());
        verify(botService).resetGuildJoinCounts("123");

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> bot.registerGuildCommands(GuildRequest.getDefaultInstance()));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
    }

    @Test
    void logRpcsMatchRestDefaults() {
        var logs = LogServiceGrpc.newBlockingStub(channel);

        LogResponse tail = logs.tailLogs(TailLogsRequest.newBuilder().setLines(2).build());
        assertEquals(List.of("10:02 INFO Joined voice", "10:03 ERROR boom"), tail.getLinesList());
        assertEquals(4, tail.getTotalCount());

        LogResponse search = logs.searchLogs(SearchLogsRequest.newBuilder().setQuery("joined").build());
        assertEquals(List.of("10:02 INFO Joined voice"), search.getLinesList());

        LogResponse warn = logs.getLogsByLevel(GetLogsByLevelRequest.newBuilder().setLevel("warn").build());
        assertEquals(List.of("10:01 WARN slow gateway"), warn.getLinesList());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> logs.searchLogs(SearchLogsRequest.getDefaultInstance()));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
    }
}
