/**
 * What a log line may carry when it leaves a service: one rule set ({@link
 * com.krizaka.observability.logging.LogRedaction}) applied to the local ECS JSON log and to the
 * OTLP log export, so the two can never differ (Law 25).
 */
@NullMarked
package com.krizaka.observability.logging;

import org.jspecify.annotations.NullMarked;
