package com.zweetnekken.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZweetnekkenDataServiceTest
{
    @Test
    void resolvesRelativeAvatarPathAgainstApiOrigin()
    {
        assertEquals(
            ZweetnekkenDataService.ZWEETNEKKEN_API_ORIGIN + "/api/clan/member-avatar?discordId=123456789012345",
            ZweetnekkenDataService.resolveProfileImageUrl("/api/clan/member-avatar?discordId=123456789012345")
        );
    }

    @Test
    void rejectsAbsoluteAndProtocolRelativeAvatarUrls()
    {
        assertNull(ZweetnekkenDataService.resolveProfileImageUrl("https://cdn.discordapp.com/avatars/123/avatar.png"));
        assertNull(ZweetnekkenDataService.resolveProfileImageUrl("//cdn.discordapp.com/avatars/123/avatar.png"));
    }

    @Test
    void createsRelativeFallbackPortraitPath()
    {
        assertEquals(
            "/api/profile-portrait?name=Test%20Player",
            ZweetnekkenDataService.profileImagePath("Test Player")
        );
    }
}
