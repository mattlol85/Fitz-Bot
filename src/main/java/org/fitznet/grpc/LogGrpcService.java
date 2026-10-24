package org.fitznet.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.fitznet.controller.LogController;
import org.fitznet.grpc.proto.GetLogsByLevelRequest;
import org.fitznet.grpc.proto.LogResponse;
import org.fitznet.grpc.proto.LogServiceGrpc;
import org.fitznet.grpc.proto.SearchLogsRequest;
import org.fitznet.grpc.proto.TailLogsRequest;
import org.springframework.grpc.server.service.GrpcService;

/** gRPC equivalent of {@link LogController}'s JSON endpoints, with the same defaults. */
@GrpcService
public class LogGrpcService extends LogServiceGrpc.LogServiceImplBase {

    static final int DEFAULT_LIMIT = 100;

    private final LogController logController;

    public LogGrpcService(LogController logController) {
        this.logController = logController;
    }

    @Override
    public void tailLogs(TailLogsRequest request, StreamObserver<LogResponse> observer) {
        reply(observer, logController.getTailLogs(request.hasLines() ? request.getLines() : DEFAULT_LIMIT));
    }

    @Override
    public void searchLogs(SearchLogsRequest request, StreamObserver<LogResponse> observer) {
        if (!request.hasQuery()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("query is required").asRuntimeException());
            return;
        }
        reply(observer, logController.searchLogs(request.getQuery(),
                request.hasMaxResults() ? request.getMaxResults() : DEFAULT_LIMIT));
    }

    @Override
    public void getLogsByLevel(GetLogsByLevelRequest request, StreamObserver<LogResponse> observer) {
        if (request.getLevel().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("level is required").asRuntimeException());
            return;
        }
        reply(observer, logController.getLogsByLevel(request.getLevel(),
                request.hasMaxResults() ? request.getMaxResults() : DEFAULT_LIMIT));
    }

    private static void reply(StreamObserver<LogResponse> observer, LogController.LogResponse response) {
        observer.onNext(LogResponse.newBuilder()
                .addAllLines(response.lines)
                .setTotalCount(response.totalCount)
                .build());
        observer.onCompleted();
    }
}
