package org.fitznet.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RconClientTest {

    @Test
    void authenticatesAndReturnsCommandResponse() throws Exception {
        String expected = "Added Steve to the whitelist";
        try (FakeRconServer server = new FakeRconServer(expected)) {
            try (RconClient client = new RconClient("127.0.0.1", server.getPort(), 2000)) {
                client.authenticate("secret");
                assertEquals(expected, client.sendCommand("whitelist add Steve"));
            }
            assertEquals("whitelist add Steve", server.getReceivedCommand());
        }
    }

    @Test
    void throwsWhenAuthenticationFails() throws Exception {
        try (FakeRconServer server = new FakeRconServer("ignored").rejectAuth()) {
            try (RconClient client = new RconClient("127.0.0.1", server.getPort(), 2000)) {
                assertThrows(IOException.class, () -> client.authenticate("wrong"));
            }
        }
    }

    /** Minimal in-process RCON server that exercises the client end-to-end. */
    private static class FakeRconServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private volatile String receivedCommand;
        private volatile boolean rejectAuth = false;

        FakeRconServer(String commandResponse) throws IOException {
            serverSocket = new ServerSocket(0);
            Thread thread = new Thread(() -> run(commandResponse));
            thread.setDaemon(true);
            thread.start();
        }

        FakeRconServer rejectAuth() {
            this.rejectAuth = true;
            return this;
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        String getReceivedCommand() {
            return receivedCommand;
        }

        private void run(String commandResponse) {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // Auth request -> auth response (id -1 signals failure).
                Packet auth = readPacket(in);
                writePacket(out, rejectAuth ? -1 : auth.id(), RconClient.TYPE_COMMAND, "");
                if (rejectAuth) {
                    return;
                }

                // Command request -> response value.
                Packet command = readPacket(in);
                receivedCommand = command.body();
                writePacket(out, command.id(), RconClient.TYPE_RESPONSE, commandResponse);
            } catch (IOException ignored) {
                // socket closed by the test — expected on teardown
            }
        }

        private static Packet readPacket(InputStream in) throws IOException {
            int length = readInt(in);
            byte[] payload = in.readNBytes(length);
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            int id = buffer.getInt();
            int type = buffer.getInt();
            byte[] body = new byte[length - 10];
            buffer.get(body);
            return new Packet(id, type, new String(body, StandardCharsets.US_ASCII));
        }

        private static int readInt(InputStream in) throws IOException {
            byte[] b = in.readNBytes(Integer.BYTES);
            if (b.length < Integer.BYTES) {
                throw new IOException("stream closed");
            }
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        private static void writePacket(OutputStream out, int id, int type, String body) throws IOException {
            byte[] bodyBytes = body.getBytes(StandardCharsets.US_ASCII);
            int length = 10 + bodyBytes.length; // id + type + body + two null bytes
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(length);
            buffer.putInt(id);
            buffer.putInt(type);
            buffer.put(bodyBytes);
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            out.write(buffer.array());
            out.flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private record Packet(int id, int type, String body) {
        }
    }
}
