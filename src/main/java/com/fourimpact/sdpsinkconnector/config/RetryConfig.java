package com.fourimpact.sdpsinkconnector.config;

import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * RetryTemplate bean — available for programmatic retry use cases.
 * @Retryable-based retry is configured via annotations on ServiceDeskPlusClientImpl.
 */
@Configuration
public class RetryConfig {

    @Value("${sdp.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${sdp.retry.initial-interval-ms:1000}")
    private long initialInterval;

    @Value("${sdp.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${sdp.retry.max-interval-ms:15000}")
    private long maxInterval;

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                maxAttempts,
                Map.of(TransientSdpException.class, true),
                true
        );
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}
