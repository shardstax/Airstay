package com.shardx.hotelManagement.airBnb.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Rate-limit rules (prefix {@code airstay.rate-limit}). Mutable so tests can tighten a rule. */
@Data
@Component
@ConfigurationProperties(prefix = "airstay.rate-limit")
public class RateLimitProperties {

    /** Master switch. Off = no request is ever limited. */
    private boolean enabled = true;

    /** Login attempts per e-mail address: stops password guessing against one account. */
    private Rule loginPerEmail = new Rule(10, Duration.ofMinutes(1));

    /** Login attempts per client address: stops one machine trying many accounts. */
    private Rule loginPerIp = new Rule(30, Duration.ofMinutes(1));

    /** Sign-ups per client address: stops account spam. */
    private Rule signupPerIp = new Rule(10, Duration.ofMinutes(1));

    /** Booking attempts per user: protects the inventory locks from a script hammering one room. */
    private Rule bookingInitPerUser = new Rule(20, Duration.ofMinutes(1));
    /** Place searches per client address: the search box asks on every pause while typing, so this is generous. */
    private Rule placesPerIp = new Rule(120, Duration.ofMinutes(1));

    /** At most {@code limit} requests per {@code window}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        private int limit;
        private Duration window;
    }
}
