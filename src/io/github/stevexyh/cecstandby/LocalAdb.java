package io.github.stevexyh.cecstandby;

import android.content.Context;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.Cipher;

/** ADB client restricted to this TV's loopback. Its independent key never leaves app storage. */
final class LocalAdb implements java.io.Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f, WRTE = 0x45545257, CLSE = 0x45534c43;
    private final Socket socket = new Socket();
    private final Context context;
    private InputStream in;
    private OutputStream out;
    private int remote;

    LocalAdb(Context context) { this.context = context; }

    void connect(String command) throws Exception {
        socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        socket.setSoTimeout(15000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        send(CNXN, 0x01000000, 4096, "host::cec-standby\0".getBytes(StandardCharsets.UTF_8));
        RSAPrivateKey key = null;
        boolean signed = false, offered = false;
        for (;;) {
            Packet packet = receive();
            if (packet.command == CNXN) break;
            if (packet.command != AUTH || packet.a != 1) throw new java.io.IOException("Unexpected ADB authentication response");
            if (key == null) key = loadKey();
            if (!signed) {
                if (packet.data.length != 20) throw new java.io.IOException("Invalid ADB auth token");
                byte[] prefix = new byte[] {0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
                ByteBuffer digest = ByteBuffer.allocate(prefix.length + packet.data.length);
                digest.put(prefix).put(packet.data);
                Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsa.init(Cipher.ENCRYPT_MODE, key);
                send(AUTH, 2, 0, rsa.doFinal(digest.array()));
                signed = true;
            } else if (!offered) {
                send(AUTH, 3, 0, publicKey(key));
                offered = true;
            } else throw new java.io.IOException("Waiting for TV authorization");
        }
        send(OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        Packet packet = receive();
        if (packet.command != OKAY || packet.b != 1) throw new java.io.IOException("ADB shell not accepted");
        remote = packet.a;
    }

    byte[] read() throws Exception {
        for (;;) {
            Packet packet = receive();
            if (packet.b != 1) throw new java.io.IOException("Unexpected ADB stream");
            if (packet.command == CLSE) throw new java.io.EOFException("Local ADB shell closed");
            if (packet.command != WRTE || packet.a != remote) throw new java.io.IOException("Unexpected ADB shell packet");
            send(OKAY, 1, remote, new byte[0]);
            return packet.data;
        }
    }

    private RSAPrivateKey loadKey() throws Exception {
        File file = new File(context.getFilesDir(), "local-adb-key.pk8");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (file.exists()) {
            if (file.length() > 8192) throw new java.io.IOException("Invalid private key length");
            try (FileInputStream stream = new FileInputStream(file)) {
                byte[] bytes = readFully(stream, (int) file.length());
                return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
            }
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new java.security.spec.RSAKeyGenParameterSpec(2048, java.security.spec.RSAKeyGenParameterSpec.F4));
        KeyPair pair = generator.generateKeyPair();
        try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(pair.getPrivate().getEncoded()); }
        return (RSAPrivateKey) pair.getPrivate();
    }

    private static byte[] publicKey(RSAPrivateKey key) {
        BigInteger n = key.getModulus();
        BigInteger radix = BigInteger.ONE.shiftLeft(32);
        int n0inv = n.mod(radix).modInverse(radix).negate().mod(radix).intValue();
        ByteBuffer binary = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        binary.putInt(64).putInt(n0inv).put(little(n)).put(little(BigInteger.ONE.shiftLeft(4096).mod(n))).putInt(65537);
        return (Base64.encodeToString(binary.array(), Base64.NO_WRAP) + " cec-standby@localhost\0").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] little(BigInteger value) {
        byte[] big = value.toByteArray(), result = new byte[256];
        for (int i = 0; i < result.length && i < big.length; i++) result[i] = big[big.length - 1 - i];
        return result;
    }

    private static final class Packet {
        final int command, a, b;
        final byte[] data;
        Packet(int command, int a, int b, byte[] data) { this.command = command; this.a = a; this.b = b; this.data = data; }
    }

    private Packet receive() throws Exception {
        ByteBuffer header = ByteBuffer.wrap(readFully(in, 24)).order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt(), a = header.getInt(), b = header.getInt();
        int length = header.getInt(), checksum = header.getInt(), magic = header.getInt();
        if (magic != ~command || length < 0 || length > 1024 * 1024) throw new java.io.IOException("Invalid ADB packet");
        byte[] data = readFully(in, length);
        int sum = 0;
        for (byte value : data) sum += value & 255;
        if (sum != checksum) throw new java.io.IOException("Invalid ADB checksum");
        return new Packet(command, a, b, data);
    }

    private static byte[] readFully(InputStream in, int length) throws Exception {
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length) {
            int count = in.read(bytes, read, length - read);
            if (count < 0) throw new java.io.EOFException();
            read += count;
        }
        return bytes;
    }

    private void send(int command, int a, int b, byte[] data) throws Exception {
        int sum = 0;
        for (byte value : data) sum += value & 255;
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(~command);
        out.write(header.array()); out.write(data); out.flush();
    }

    @Override public void close() { try { socket.close(); } catch (java.io.IOException ignored) { } }
}
