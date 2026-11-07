package com.featureflag.core.flag.event;

import com.featureflag.core.flag.cache.FeatureFlagCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FlagChangedEventListener {

    private final FeatureFlagCache flagCache;

    public FlagChangedEventListener(FeatureFlagCache flagCache) {
        this.flagCache = flagCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFlagChanged(FlagChangedEvent event) {
        flagCache.evict(event.flagKey());
    }
}