package org.fitznet.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.fitznet.controller.BotController;
import org.fitznet.grpc.proto.BotReply;
import org.fitznet.grpc.proto.BotServiceGrpc;
import org.fitznet.grpc.proto.ForceUpdateCommandsRequest;
import org.fitznet.grpc.proto.GetStatusRequest;
import org.fitznet.grpc.proto.GuildRequest;
import org.fitznet.grpc.proto.ShutdownRequest;
import org.fitznet.grpc.proto.StartupRequest;
import org.springframework.grpc.server.service.GrpcService;

import java.util.function.Supplier;

/**
 * gRPC equivalent of {@link BotController}; each RPC delegates to the controller and returns its
 * plain-text reply.
 */
@GrpcService
public class BotGrpcService extends BotServiceGrpc.BotServiceImplBase {

    private final BotController botController;

    public BotGrpcService(BotController botController) {
        this.botController = botController;
    }

    @Override
    public void startup(StartupRequest request, StreamObserver<BotReply> observer) {
        reply(observer, botController::startup);
    }

    @Override
    public void forceUpdateCommands(ForceUpdateCommandsRequest request, StreamObserver<BotReply> observer) {
        reply(observer, botController::forceUpdateCommands);
    }

    @Override
    public void registerGuildCommands(GuildRequest request, StreamObserver<BotReply> observer) {
        if (missingGuild(request, observer)) {
            return;
        }
        reply(observer, () -> botController.registerGuildCommands(request.getGuildId()));
    }

    @Override
    public void shutdown(ShutdownRequest request, StreamObserver<BotReply> observer) {
        reply(observer, botController::shutdown);
    }

    @Override
    public void getStatus(GetStatusRequest request, StreamObserver<BotReply> observer) {
        reply(observer, botController::getStatus);
    }

    @Override
    public void resetJoinCounts(GuildRequest request, StreamObserver<BotReply> observer) {
        if (missingGuild(request, observer)) {
            return;
        }
        reply(observer, () -> botController.resetJoinCounts(request.getGuildId()));
    }

    /** REST takes the guild id from the path, so it can never be blank there. */
    private static boolean missingGuild(GuildRequest request, StreamObserver<BotReply> observer) {
        if (request.getGuildId().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("guild_id is required").asRuntimeException());
            return true;
        }
        return false;
    }

    private static void reply(StreamObserver<BotReply> observer, Supplier<String> call) {
        String message = call.get();
        observer.onNext(BotReply.newBuilder().setMessage(message != null ? message : "").build());
        observer.onCompleted();
    }
}
