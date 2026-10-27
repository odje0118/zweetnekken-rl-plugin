package com.zweetnekken.service;

/**
 * Corrects a rendering edge case for opaque animated GIFs whose every frame
 * covers the full canvas. In that case restoring each frame to the logical
 * screen background before drawing the next frame is unnecessary and can
 * expose a pale background color as a flash in AWT/Swing.
 */
final class AnimatedGifDisposalNormalizer
{
    private AnimatedGifDisposalNormalizer()
    {
    }

    static byte[] normalizeOpaqueFullFrameGifDisposal(byte[] bytes)
    {
        if (!isGif(bytes) || bytes.length < 13)
        {
            return bytes;
        }

        int canvasWidth = readUnsignedShortLittleEndian(bytes, 6);
        int canvasHeight = readUnsignedShortLittleEndian(bytes, 8);
        int logicalScreenPacked = bytes[10] & 0xff;
        int offset = 13;
        if ((logicalScreenPacked & 0x80) != 0)
        {
            offset += 3 * (2 << (logicalScreenPacked & 0x07));
        }
        if (offset > bytes.length)
        {
            return bytes;
        }

        java.util.List<Integer> graphicControlPackedOffsets = new java.util.ArrayList<>();
        boolean allFramesCoverCanvas = true;
        boolean hasTransparency = false;
        int imageCount = 0;

        while (offset < bytes.length)
        {
            int marker = bytes[offset] & 0xff;
            if (marker == 0x3b) // GIF trailer
            {
                break;
            }
            if (marker == 0x21) // Extension
            {
                if (offset + 2 >= bytes.length)
                {
                    return bytes;
                }
                int label = bytes[offset + 1] & 0xff;
                if (label == 0xf9) // Graphic Control Extension
                {
                    if (offset + 7 >= bytes.length || (bytes[offset + 2] & 0xff) != 4)
                    {
                        return bytes;
                    }
                    int packedOffset = offset + 3;
                    graphicControlPackedOffsets.add(packedOffset);
                    hasTransparency |= (bytes[packedOffset] & 0x01) != 0;
                }
                offset = skipGifSubBlocks(bytes, offset + 2);
                if (offset < 0)
                {
                    return bytes;
                }
                continue;
            }
            if (marker != 0x2c || offset + 9 >= bytes.length) // Image Descriptor
            {
                return bytes;
            }

            int left = readUnsignedShortLittleEndian(bytes, offset + 1);
            int top = readUnsignedShortLittleEndian(bytes, offset + 3);
            int frameWidth = readUnsignedShortLittleEndian(bytes, offset + 5);
            int frameHeight = readUnsignedShortLittleEndian(bytes, offset + 7);
            int imagePacked = bytes[offset + 9] & 0xff;
            if (left != 0 || top != 0 || frameWidth != canvasWidth || frameHeight != canvasHeight)
            {
                allFramesCoverCanvas = false;
            }

            imageCount++;
            offset += 10;
            if ((imagePacked & 0x80) != 0) // Local color table
            {
                offset += 3 * (2 << (imagePacked & 0x07));
            }
            if (offset >= bytes.length) // LZW minimum code size
            {
                return bytes;
            }
            offset++; // LZW minimum code size
            offset = skipGifSubBlocks(bytes, offset);
            if (offset < 0)
            {
                return bytes;
            }
        }

        if (imageCount > 1 && allFramesCoverCanvas && !hasTransparency)
        {
            for (int packedOffset : graphicControlPackedOffsets)
            {
                int packed = bytes[packedOffset] & 0xff;
                // Disposal is bits 2-4. Method 1 means "do not dispose".
                bytes[packedOffset] = (byte) ((packed & ~0x1c) | (1 << 2));
            }
        }
        return bytes;
    }

    private static boolean isGif(byte[] bytes)
    {
        if (bytes == null || bytes.length < 6) return false;
        boolean gif87a = bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
            && bytes[3] == '8' && bytes[4] == '7' && bytes[5] == 'a';
        boolean gif89a = bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
            && bytes[3] == '8' && bytes[4] == '9' && bytes[5] == 'a';
        return gif87a || gif89a;
    }

    private static int readUnsignedShortLittleEndian(byte[] bytes, int offset)
    {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    /** Returns the offset after a GIF data-sub-block sequence, or -1 if malformed. */
    private static int skipGifSubBlocks(byte[] bytes, int offset)
    {
        while (offset < bytes.length)
        {
            int length = bytes[offset++] & 0xff;
            if (length == 0)
            {
                return offset;
            }
            if (offset + length > bytes.length)
            {
                return -1;
            }
            offset += length;
        }
        return -1;
    }
}
