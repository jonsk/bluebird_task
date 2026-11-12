import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Rewrites a Spring Boot jar so that sqlite-jdbc only carries the native library
 * for one target platform/arch. Run with: java build/SlimSqlite.java <jar> <outDir> <version> <platform> <arch>
 *
 * The zip is written by hand (instead of ZipOutputStream) because Spring Boot's loader
 * reads nested jars through their local file headers; entries must therefore carry exact
 * sizes and CRCs instead of data descriptors.
 */
public class SlimSqlite {

    private static final String NATIVE_PREFIX = "org/sqlite/native/";
    private static final int DOS_TIME = 0;
    private static final int DOS_DATE = 0x0021; // 1980-01-01, deterministic

    private record Entry(String name, byte[] data) {
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            fail("usage: SlimSqlite <jar> <outDir> [version] <platform> <arch>");
        }
        try {
            if (args.length >= 5) {
                run(Paths.get(args[0]), Paths.get(args[1]), args[2], args[3], args[4]);
            } else {
                run(Paths.get(args[0]), Paths.get(args[1]), "", args[2], args[3]);
            }
        } catch (Exception e) {
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void run(Path jar, Path outDir, String version, String platform, String arch) throws IOException {
        if (!Files.isRegularFile(jar)) {
            fail("jar 不存在：" + jar);
        }
        String nativeDir = switch (platform) {
            case "linux" -> "Linux";
            case "linux-musl" -> "Linux-Musl";
            case "windows" -> "Windows";
            case "mac" -> "Mac";
            case "freebsd" -> "FreeBSD";
            default -> null;
        };
        if (nativeDir == null) {
            fail("不支持的平台：" + platform);
        }
        if (!arch.equals("x86_64") && !arch.equals("aarch64")) {
            fail("不支持的架构：" + arch);
        }
        String keepPrefix = NATIVE_PREFIX + nativeDir + "/" + arch + "/";

        List<Entry> outer = readJar(jar);
        Entry sqlite = null;
        for (Entry entry : outer) {
            if (entry.name().matches("BOOT-INF/lib/sqlite-jdbc-.*\\.jar")) {
                sqlite = entry;
                break;
            }
        }
        if (sqlite == null) {
            fail("jar 内未找到 BOOT-INF/lib/sqlite-jdbc-*.jar");
        }

        List<Entry> slimInner = new ArrayList<>();
        boolean kept = false;
        for (Entry entry : readStream(sqlite.data())) {
            if (entry.name().startsWith(NATIVE_PREFIX)) {
                if (!entry.name().startsWith(keepPrefix)) {
                    continue;
                }
                kept = true;
            }
            slimInner.add(entry);
        }
        if (!kept) {
            fail("sqlite-jdbc 内未找到 " + keepPrefix + "，可能需要更新脚本或驱动版本");
        }
        byte[] slimInnerBytes = writeZip(slimInner, true);

        List<Entry> output = new ArrayList<>(outer.size());
        for (Entry entry : outer) {
            output.add(entry.name().equals(sqlite.name())
                    ? new Entry(entry.name(), slimInnerBytes)
                    : entry);
        }
        byte[] result = writeZip(output, false);

        Files.createDirectories(outDir);
        List<String> parts = new ArrayList<>();
        parts.add("bluebird-task");
        if (version != null && !version.isBlank()) {
            parts.add(version.trim());
        }
        parts.add(platform);
        parts.add(arch);
        String outName = String.join("-", parts) + ".jar";
        Path target = outDir.resolve(outName);
        Path temp = outDir.resolve(outName + ".tmp");
        Files.write(temp, result);
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println(outName);
    }

    private static List<Entry> readJar(Path jar) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                byte[] data;
                try (var stream = zip.getInputStream(entry)) {
                    data = stream.readAllBytes();
                }
                entries.add(new Entry(entry.getName(), data));
            }
        }
        return entries;
    }

    private static List<Entry> readStream(byte[] data) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(new Entry(entry.getName(), zip.readAllBytes()));
            }
        }
        return entries;
    }

    private static byte[] writeZip(List<Entry> entries, boolean forceDeflate) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        List<byte[]> central = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            boolean stored = !forceDeflate && entry.name().startsWith("BOOT-INF/lib/");
            int method = stored ? 0 : 8;
            byte[] payload = stored ? entry.data() : deflate(entry.data());
            byte[] name = entry.name().getBytes(StandardCharsets.UTF_8);
            int crc = crc32(entry.data());
            int offset = out.size();

            writeInt(out, 0x04034b50);
            writeShort(out, 20);
            writeShort(out, 0);
            writeShort(out, method);
            writeShort(out, DOS_TIME);
            writeShort(out, DOS_DATE);
            writeInt(out, crc);
            writeInt(out, payload.length);
            writeInt(out, entry.data().length);
            writeShort(out, name.length);
            writeShort(out, 0);
            out.write(name);
            out.write(payload);

            ByteArrayOutputStream header = new ByteArrayOutputStream(64);
            writeInt(header, 0x02014b50);
            writeShort(header, 20);
            writeShort(header, 20);
            writeShort(header, 0);
            writeShort(header, method);
            writeShort(header, DOS_TIME);
            writeShort(header, DOS_DATE);
            writeInt(header, crc);
            writeInt(header, payload.length);
            writeInt(header, entry.data().length);
            writeShort(header, name.length);
            writeShort(header, 0);
            writeShort(header, 0);
            writeShort(header, 0);
            writeShort(header, 0);
            writeInt(header, 0644 << 16);
            writeInt(header, offset);
            header.write(name);
            central.add(header.toByteArray());
        }
        int centralStart = out.size();
        for (byte[] header : central) {
            out.write(header);
        }
        int centralSize = out.size() - centralStart;

        writeInt(out, 0x06054b50);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, entries.size());
        writeShort(out, entries.size());
        writeInt(out, centralSize);
        writeInt(out, centralStart);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, data.length / 2));
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int length = deflater.deflate(buffer);
            out.write(buffer, 0, length);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static int crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static void fail(String message) {
        System.err.println("[slim-sqlite] ERROR: " + message);
        System.exit(1);
    }
}
