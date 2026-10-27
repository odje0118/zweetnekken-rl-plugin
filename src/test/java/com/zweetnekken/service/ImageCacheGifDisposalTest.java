package com.zweetnekken.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageCacheGifDisposalTest
{
    // Two opaque, full-canvas frames with disposal method 2 ("restore to background").
    private static final String TWO_FRAME_GIF =
        "R0lGODlhAgACAIEAABQeKAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQICgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQICgAAACwAAAAAAgACAIHIMigAAAAAAAAAAAAIBgABCAQQEAA7";

    @Test
    void normalizesDisposalForOpaqueFullCanvasGifFrames() throws IOException
    {
        byte[] original = Base64.getDecoder().decode(TWO_FRAME_GIF);
        byte[] fixed = original.clone();

        AnimatedGifDisposalNormalizer.normalizeOpaqueFullFrameGifDisposal(fixed);

        assertEquals(2, countDisposalMethod(original, 2));
        assertEquals(0, countDisposalMethod(fixed, 2));
        assertEquals(2, countDisposalMethod(fixed, 1));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(fixed)));
    }

    private static int countDisposalMethod(byte[] gif, int method)
    {
        int count = 0;
        for (int i = 0; i + 7 < gif.length; i++)
        {
            if ((gif[i] & 0xff) == 0x21
                && (gif[i + 1] & 0xff) == 0xf9
                && (gif[i + 2] & 0xff) == 4)
            {
                if (((gif[i + 3] & 0xff) >> 2 & 0x07) == method)
                {
                    count++;
                }
                i += 7;
            }
        }
        return count;
    }
}
