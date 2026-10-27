package com.zweetnekken.service;

import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RulesService
{
	private final ZweetnekkenApiClient api;
	private volatile ZweetnekkenModels.Rules current = new ZweetnekkenModels.Rules();

	@Inject public RulesService(ZweetnekkenApiClient api) { this.api = api; }
	public ZweetnekkenModels.Rules current() { return current; }

	public void refresh(Runnable callback)
	{
		api.getRules(result ->
		{
			if (result.isSuccessful() && result.value != null)
			{
				ZweetnekkenModels.Rules loaded = result.value;
				// The API may still be serving an older, higher threshold during rollout.
				// Never let that suppress loot worth at least the clan's 1.5m baseline.
				if (loaded.minimumLootValue <= 0
					|| loaded.minimumLootValue > ZweetnekkenModels.Rules.BASE_MINIMUM_LOOT_VALUE)
					loaded.minimumLootValue = ZweetnekkenModels.Rules.BASE_MINIMUM_LOOT_VALUE;
				current = loaded;
			}
			if (callback != null) callback.run();
		});
	}
}
