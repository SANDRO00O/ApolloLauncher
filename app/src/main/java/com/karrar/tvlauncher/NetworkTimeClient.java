package com.karrar.ApolloLauncher;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Reads UTC time from public NTP servers.
 *
 * This is only a display-time fallback. It does not change Android's system clock.
 * Keeping the implementation small also makes it compatible with Android 5.1.
 */
public final class NetworkTimeClient {
    private static final long NTP_UNIX_EPOCH_OFFSET_SECONDS = 2208988800L;
    private static final long NTP_FRACTION_SCALE = 4294967296L;
    private static final String[] SERVERS = {
            "time.google.com",
            "time.cloudflare.com",
            "pool.ntp.org"
    };

    private NetworkTimeClient() { }

    public static long fetchUtcTimeMillis() throws Exception {
        Exception lastError = null;

        for (String host : SERVERS) {
            DatagramSocket socket = null;
            try {
                InetAddress address = InetAddress.getByName(host);
                socket = new DatagramSocket();
                socket.setSoTimeout(2500);
                socket.connect(address, 123);

                byte[] request = new byte[48];
                request[0] = 0x1B; // NTP v3 client request.
                DatagramPacket outgoing = new DatagramPacket(request, request.length);
                long startedAtNanos = System.nanoTime();
                socket.send(outgoing);

                byte[] response = new byte[48];
                DatagramPacket incoming = new DatagramPacket(response, response.length);
                socket.receive(incoming);
                long roundTripMillis = Math.max(0L,
                        (System.nanoTime() - startedAtNanos) / 1000000L);

                if (incoming.getLength() < 48) {
                    throw new IllegalStateException("Incomplete NTP response from " + host);
                }

                int mode = response[0] & 0x07;
                int stratum = response[1] & 0xFF;
                if (mode != 4 && mode != 5) {
                    throw new IllegalStateException("Invalid NTP response mode from " + host);
                }
                if (stratum == 0 || stratum > 15) {
                    throw new IllegalStateException("Invalid NTP stratum from " + host);
                }

                long seconds = readUnsignedInt(response, 40);
                long fraction = readUnsignedInt(response, 44);
                if (seconds == 0L) {
                    throw new IllegalStateException("NTP server returned an empty timestamp");
                }

                long unixMillis = (seconds - NTP_UNIX_EPOCH_OFFSET_SECONDS) * 1000L
                        + (fraction * 1000L) / NTP_FRACTION_SCALE;

                // The transmit timestamp is captured before the packet reaches us.
                return unixMillis + (roundTripMillis / 2L);
            } catch (Exception error) {
                lastError = error;
            } finally {
                if (socket != null) socket.close();
            }
        }

        if (lastError != null) throw lastError;
        throw new IllegalStateException("No NTP servers are available");
    }

    private static long readUnsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | ((long) (bytes[offset + 3] & 0xFF));
    }
}
