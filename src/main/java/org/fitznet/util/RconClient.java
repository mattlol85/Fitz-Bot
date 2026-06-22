package org.fitznet.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal client for the Source RCON protocol, used to send commands to a
 * Minecraft server's remote console.
 *
 * <p>The protocol is intentionally tiny, so this is implemented directly over a
 * TCP {@link Socket} rather than pulling in a dependency. Packets are little-endian:
 * {@code int length | int requestId | int type | ASCII body + 0x00 | 0x00}.</p>
 *
 * <p>Note: long command output can be split across multiple response packets. The
 * commands this bot sends (e.g. {@code whitelist add <user>}) return a single short
 * line, so only the first response packet is read.</p>
 */
@Slf4j
public class RconClient implements AutoCloseable {

    /** SERVERDATA_AUTH — authentication request. */
    public static final int TYPE_AUTH = 3;
    /** SERVERDATA_EXECCOMMAND — command request (also the auth-response type). */
    public static final int TYPE_COMMAND = 2;
    /** SERVERDATA_RESPONSE_VALUE — command response (and the empty pre-auth response). */
    public static final int TYPE_RESPONSE = 0;

    private static final int AUTH_FAILED_ID = -1;
    private static final int HEADER_AND_PADDING = Integer.BYTES * 2 + 2; // requestId + type + two null bytes
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private int requestCounter = 0;

    public RconClient(String host, int port) throws IOException {
        this(host, port, DEFAULT_TIMEOUT_MS);
    }

    public RconClient(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /**
     * Authenticates with the server. Must be called before {@link #sendCommand}.
     *
     * @throws IOException if the password is rejected or the connection fails
     */
    public void authenticate(String password) throws IOException {
        int id = nextId();
        writePacket(id, TYPE_AUTH, password);

        // Some servers send an empty SERVERDATA_RESPONSE_VALUE before the auth response.
        Packet response = readPacket();
        if (response.type() == TYPE_RESPONSE) {
            response = readPacket();
        }
        if (response.id() == AUTH_FAILED_ID) {
            throw new IOException("RCON authentication failed (wrong password)");
        }
    }

    /**
     * Sends a command and returns the server's response text.
     */
    public String sendCommand(String command) throws IOException {
        writePacket(nextId(), TYPE_COMMAND, command);
        return readPacket().body();
    }

    private int nextId() {
        return ++requestCounter;
    }

    private void writePacket(int id, int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.US_ASCII);
        int length = HEADER_AND_PADDING + bodyBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(bodyBytes);
        buffer.put((byte) 0); // body null terminator
        buffer.put((byte) 0); // packet padding
        out.write(buffer.array());
        out.flush();
    }

    private Packet readPacket() throws IOException {
        int length = readLittleEndianInt();
        if (length < HEADER_AND_PADDING) {
            throw new IOException("Invalid RCON packet length: " + length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(readFully(length)).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        int type = buffer.getInt();
        byte[] bodyBytes = new byte[length - HEADER_AND_PADDING];
        buffer.get(bodyBytes);
        return new Packet(id, type, new String(bodyBytes, StandardCharsets.US_ASCII));
    }

    private int readLittleEndianInt() throws IOException {
        return ByteBuffer.wrap(readFully(Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private byte[] readFully(int n) throws IOException {
        byte[] data = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(data, offset, n - offset);
            if (read == -1) {
                throw new IOException("Connection closed while reading RCON packet");
            }
            offset += read;
        }
        return data;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private record Packet(int id, int type, String body) {
    }
}
