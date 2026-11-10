package com.riseai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * SHA-256 Merkle root over canonical record strings, in their stored order. Leaves are sha256(record); a parent is
 * sha256(left || right); an odd node is paired with itself. Anyone with the rows can recompute it.
 */
public final class MerkleRoot {
    private MerkleRoot() {
    }

    public static String of(List<String> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("a Merkle root needs at least one record");
        }
        List<byte[]> level = new ArrayList<>(records.size());
        for (String record : records) {
            level.add(sha256(record.getBytes(StandardCharsets.UTF_8)));
        }
        while (level.size() > 1) {
            List<byte[]> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                byte[] left = level.get(i);
                byte[] right = i + 1 < level.size() ? level.get(i + 1) : left;
                byte[] pair = new byte[64];
                System.arraycopy(left, 0, pair, 0, 32);
                System.arraycopy(right, 0, pair, 32, 32);
                next.add(sha256(pair));
            }
            level = next;
        }
        return "0x" + HexFormat.of().formatHex(level.get(0));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
