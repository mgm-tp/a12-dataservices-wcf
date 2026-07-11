/*
 * SPDX-License-Identifier: EUPL-1.2 OR LicenseRef-commercial
 *
 * Copyright (c) 2012-2026 mgm technology partners GmbH
 *
 * Dual License
 * ------------
 * This source file is part of the mgm A12 Platform and available under
 * a choice of two different licenses:
 *
 * 1. Open-Source License – EUPL v1.2
 *    You may redistribute and/or modify this file under the terms of the
 *    European Union Public License, version 1.2 - see https://eupl.eu/.
 *
 * 2. Commercial License
 *    Alternatively, you may obtain a commercial license from
 *    mgm technology partners GmbH, that permits use of this software
 *    under different terms (including support and maintenance services).
 *
 *    Please contact a12-license@mgm-tp.com for more information.
 *
 * You must select and comply with exactly one of the above license options.
 *
 * Warranty Disclaimer (applies to either option)
 * ----------------------------------------------
 * THIS SOFTWARE IS PROVIDED “AS IS” AND WITHOUT WARRANTY OF ANY KIND,
 * WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT, EXCEPT WHERE SUCH DISCLAIMERS ARE HELD TO BE
 * LEGALLY INVALID. SEE THE RESPECTIVE LICENSE TEXT FOR DETAILS.
 */
package com.mgmtp.a12.dataservices.wcf.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a Spring component as a workspace conversion step.
 *
 * Purpose: A converter transforms a workspace (models/files) from
 * one structure or version state to another within the conversion pipeline.
 *
 * Ordering: Use {@link #order()} or the shortcut `@WcfConverter(order = 100)`.
 * The single positional value maps to {@link #order()} which is an alias for {@link Order#value()}.
 * Lower value = earlier execution.
 *
 * Bean Name: {@link #name()} (alias of {@link Component#value()})
 * lets you optionally propose a Spring bean name.
 *
 * Description: {@link #description()} is an optional short free‑text
 * note (e.g. for logging, diagnostics or documentation).
 *
 * Constraint: At runtime a validator ensures the annotated class implements
 * `com.mgmtp.a12.wcf.WorkspaceConverter`. Misuse causes application startup failure.
 *
 * Examples:
 * @WcfConverter(order = 50)
 * class ModelExpansionConverter { ... }
 *
 * @WcfConverter(order = 100, name = "legacyModelNormalizer")
 * class ModelExpansionConverter { ... }
 */
@Documented
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Inherited
@Component
@Bean
@Order
public @interface WcfConverter {

	/**
	 * Positional shortcut for order. Using @WcfConverter(100) sets order=100.
	 * Alias for {@link Order#value()}.
	 * Lower executes earlier.
	 *
	 * @return order value
	 */
	@AliasFor(annotation = Order.class, attribute = "value")
	int order();

	/**
	 * Suggested Spring bean name (optional).
	 *
	 * @return bean name or empty string
	 * @see Component#value()
	 */
	@AliasFor(annotation = Component.class, attribute = "value")
	String name() default "";

	/**
	 * Optional concise human-readable description (recommended ≤ 120 chars).
	 *
	 * @return description text or empty string
	 */
	String description() default "";
}
