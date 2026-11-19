package com.shardx.hotelManagement.airBnb.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for the optional natural-language search interpreter. */
@Data
@Component
@ConfigurationProperties(prefix = "airstay.ai.search")
public class AiSearchProperties {

    /** A false value means no model request can be made. */
    private boolean enabled = true;

}
