import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Copies an APK, replacing only DEX entries while preserving all native-library offsets. */
public final class DexOverlay {
    private static long u32(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static int u16(RandomAccessFile file) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
    }

    private static void word(RandomAccessFile file, int value) throws IOException {
        file.writeShort(Short.reverseBytes((short) value));
    }

    private static void dword(RandomAccessFile file, long value) throws IOException {
        if (value < 0 || value > 0xffffffffL) throw new IOException("ZIP64 is not supported");
        file.writeInt(Integer.reverseBytes((int) value));
    }

    private static Map<String, byte[]> dexFiles(File apk) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                try (InputStream input = zip.getInputStream(entry);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    byte[] dex = output.toByteArray();
                    if (dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x') {
                        throw new IOException("Invalid DEX entry: " + entry.getName());
                    }
                    if (result.put(entry.getName(), dex) != null) {
                        throw new IOException("Duplicate DEX entry: " + entry.getName());
                    }
                }
            }
        }
        if (!result.containsKey("classes.dex")) throw new IOException("Patched APK has no classes.dex");
        return result;
    }

    private static List<byte[]> unchangedEntries(File apk, Set<String> replacements) throws IOException {
        List<byte[]> result = new ArrayList<>();
        try (RandomAccessFile input = new RandomAccessFile(apk, "r")) {
            long end = -1;
            for (long offset = input.length() - 22; offset >= Math.max(0, input.length() - 65557); offset--) {
                input.seek(offset);
                if (u32(input) != 0x06054b50L) continue;
                input.seek(offset + 20);
                if (offset + 22 + u16(input) == input.length()) { end = offset; break; }
            }
            if (end < 0) throw new IOException("ZIP end record not found");
            input.seek(end + 4);
            if (u16(input) != 0 || u16(input) != 0) throw new IOException("Multipart ZIP is unsupported");
            int diskCount = u16(input), count = u16(input);
            long size = u32(input), offset = u32(input);
            if (diskCount != count || count == 65535 || offset + size > end) {
                throw new IOException("Unsupported or invalid ZIP directory");
            }
            input.seek(offset);
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < count; index++) {
                long start = input.getFilePointer();
                if (u32(input) != 0x02014b50L) throw new IOException("Invalid ZIP directory entry");
                input.seek(start + 28);
                int nameLength = u16(input), extraLength = u16(input), commentLength = u16(input);
                input.seek(start + 46);
                byte[] name = new byte[nameLength];
                input.readFully(name);
                String path = new String(name, StandardCharsets.UTF_8);
                if (!seen.add(path)) throw new IOException("Duplicate original ZIP entry: " + path);
                byte[] entry = new byte[46 + nameLength + extraLength + commentLength];
                input.seek(start);
                input.readFully(entry);
                if (!replacements.contains(path)) result.add(entry);
            }
            if (input.getFilePointer() != offset + size) throw new IOException("ZIP directory size mismatch");
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: DexOverlay STOCK.apk PATCHED_CODE.apk OUTPUT.apk");
        File stock = new File(args[0]), patch = new File(args[1]), output = new File(args[2]);
        if (output.exists()) throw new IOException("Output already exists; original files were not modified");
        Map<String, byte[]> dex = dexFiles(patch);
        List<byte[]> directory = unchangedEntries(stock, dex.keySet());
        List<Long> offsets = new ArrayList<>(), checksums = new ArrayList<>();
        Files.copy(stock.toPath(), output.toPath());
        try (RandomAccessFile file = new RandomAccessFile(output, "rw")) {
            file.seek(file.length());
            for (Map.Entry<String, byte[]> entry : dex.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8), data = entry.getValue();
                long start = file.getFilePointer();
                offsets.add(start);
                CRC32 crc = new CRC32(); crc.update(data); checksums.add(crc.getValue());
                int padding = (int) ((4 - ((start + 30 + name.length + 4) % 4)) % 4);
                dword(file, 0x04034b50L); word(file, 20); word(file, 0); word(file, 0);
                word(file, 0); word(file, 33); dword(file, crc.getValue());
                dword(file, data.length); dword(file, data.length);
                word(file, name.length); word(file, 4 + padding); file.write(name);
                word(file, 0xffff); word(file, padding); file.write(new byte[padding]);
                if (file.getFilePointer() % 4 != 0) throw new IOException("DEX alignment failure");
                file.write(data);
            }
            long centralOffset = file.getFilePointer();
            for (byte[] entry : directory) file.write(entry);
            int index = 0;
            for (Map.Entry<String, byte[]> entry : dex.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8), data = entry.getValue();
                dword(file, 0x02014b50L); word(file, 20); word(file, 20); word(file, 0); word(file, 0);
                word(file, 0); word(file, 33); dword(file, checksums.get(index));
                dword(file, data.length); dword(file, data.length); word(file, name.length);
                word(file, 0); word(file, 0); word(file, 0); word(file, 0); dword(file, 0);
                dword(file, offsets.get(index++)); file.write(name);
            }
            long centralSize = file.getFilePointer() - centralOffset;
            int count = directory.size() + dex.size();
            if (count >= 65535) throw new IOException("ZIP64 entry count is unsupported");
            dword(file, 0x06054b50L); word(file, 0); word(file, 0); word(file, count); word(file, count);
            dword(file, centralSize); dword(file, centralOffset); word(file, 0);
            file.getFD().sync();
        }
        try (ZipFile check = new ZipFile(output)) {
            for (String name : dex.keySet()) {
                ZipEntry entry = check.getEntry(name);
                if (entry == null || entry.getSize() != dex.get(name).length) {
                    throw new IOException("Output validation failed for " + name);
                }
            }
        }
        System.out.println("Created " + output + "; original APK and native-library offsets preserved.");
    }
}
