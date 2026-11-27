package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZweetnekkenMemberSearchPanelTest
{
	private static ZweetnekkenModels.ClanLeaderboardMember member(String name, long pvm, long clan, int rankOrder)
	{
		ZweetnekkenModels.ClanLeaderboardMember member = new ZweetnekkenModels.ClanLeaderboardMember();
		member.name = name;
		member.pvmPoints = pvm;
		member.clanPoints = clan;
		member.clanRankOrder = rankOrder;
		return member;
	}

	@Test
	void sortsPvMPointsHighestFirst()
	{
		assertSortedNames(ZweetnekkenMemberSearchPanel.SortMode.PVM, "Guthixian", "Saradominist", "Zealot");
	}

	@Test
	void sortsClanPointsHighestFirst()
	{
		assertSortedNames(ZweetnekkenMemberSearchPanel.SortMode.CLAN, "Saradominist", "Guthixian", "Zealot");
	}

	@Test
	void sortsClanRankByProgressionOrderHighestFirst()
	{
		assertSortedNames(ZweetnekkenMemberSearchPanel.SortMode.RANK, "Guthixian", "Saradominist", "Zealot");
	}

	@Test
	void searchesRuneScapeNamesCaseInsensitively()
	{
		ZweetnekkenModels.ClanLeaderboardMember member = member("LogicalHash", 0, 0, 0);
		assertTrue(ZweetnekkenMemberSearchPanel.matchesSearch(member, "logic"));
		assertTrue(ZweetnekkenMemberSearchPanel.matchesSearch(member, "LOGICALHASH"));
		assertFalse(ZweetnekkenMemberSearchPanel.matchesSearch(member, "saradominist"));
	}

	@Test
	void displaysOrdinaryRankForClanOwnerAndLeaderRoles()
	{
		assertEquals("Saradominist", ZweetnekkenMemberSearchPanel.displayRank("Clan Leader", 400, 100));
		assertEquals("Zealot", ZweetnekkenMemberSearchPanel.displayRank("Clan Owner", 150, 50));
		assertEquals("Zweetnek", ZweetnekkenMemberSearchPanel.displayRank("Clan Owner", 0, 0));
	}

	private static void assertSortedNames(ZweetnekkenMemberSearchPanel.SortMode mode, String... expected)
	{
		List<ZweetnekkenModels.ClanLeaderboardMember> members = Arrays.asList(
			member("Zealot", 100, 20, 1),
			member("Saradominist", 200, 80, 2),
			member("Guthixian", 300, 40, 3));
		List<String> names = members.stream()
			.sorted(ZweetnekkenMemberSearchPanel.comparator(mode))
			.map(member -> member.name)
			.collect(Collectors.toList());
		assertEquals(Arrays.asList(expected), names);
	}
}
