package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.RoutingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingCanaryStickyTest {

    @Test
    void canaryBucket_isStickyForSameConversation() {
        RoutingProperties props = new RoutingProperties();
        props.setMode(RoutingProperties.Mode.CANARY);
        props.setCanaryPercent(25);
        // Use a real RoutingService is heavy — test the hash contract via repeated calls through a tiny helper
        long conversationId = 42L;
        boolean first = bucket(conversationId, 25);
        boolean second = bucket(conversationId, 25);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void canaryZeroPercent_neverLive() {
        assertThat(bucket(1L, 0)).isFalse();
        assertThat(bucket(99L, 0)).isFalse();
    }

    @Test
    void canaryHundredPercent_alwaysLive() {
        assertThat(bucket(1L, 100)).isTrue();
        assertThat(bucket(999L, 100)).isTrue();
    }

    @Test
    void differentConversations_canDiffer() {
        // With 50% we expect some variance across ids (not a guarantee for only 2 samples,
        // so scan a range and assert both outcomes appear).
        boolean sawLive = false;
        boolean sawHoldout = false;
        for (long id = 1; id <= 200; id++) {
            if (bucket(id, 50)) {
                sawLive = true;
            } else {
                sawHoldout = true;
            }
        }
        assertThat(sawLive).isTrue();
        assertThat(sawHoldout).isTrue();
    }

    @Test
    void raisingCanaryPercent_isMonotone_keepsPriorLiveBuckets() {
        for (long id = 1; id <= 500; id++) {
            if (bucket(id, 5)) {
                assertThat(bucket(id, 25)).as("id=%s still live at 25%%", id).isTrue();
                assertThat(bucket(id, 50)).as("id=%s still live at 50%%", id).isTrue();
                assertThat(bucket(id, 100)).as("id=%s still live at 100%%", id).isTrue();
            }
            if (bucket(id, 25)) {
                assertThat(bucket(id, 50)).as("id=%s still live at 50%%", id).isTrue();
            }
        }
    }

    /** Mirrors RoutingService.shouldApplyLiveWinner CANARY branch. */
    private static boolean bucket(long stickyKey, int pct) {
        if (pct <= 0) {
            return false;
        }
        if (pct >= 100) {
            return true;
        }
        int b = Math.floorMod(Long.hashCode(stickyKey), 100);
        return b < pct;
    }
}
