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
package com.mgmtp.a12.dataservices.wcf;

import java.nio.file.Path;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Supported programmatic entry point for embedded/library use of WCF.
 *
 * Use this when WCF is consumed as an ordinary library (converters already on the launch
 * classpath) rather than through the {@code wcf-cli} fat JAR. It owns the Spring bootstrap so
 * consumers depend on this documented API instead of hand-rolling a {@code @SpringBootApplication}
 * that re-implements the context wiring.
 *
 * Typical usage:
 *
 * [source,java]
 * ----
 * WcfRunner.run(inputWorkspaceDir, outputSeedDataDir);
 * ----
 *
 * The Spring context is initialized once on the first call (lazy, thread-safe via the
 * initialization-on-demand holder idiom) and reused for all subsequent calls. It is closed
 * automatically when the JVM exits via Spring's registered shutdown hook.
 *
 * Converter discovery relies on a component scan rooted at {@code com.mgmtp.a12}. All
 * {@link com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter @WcfConverter}-annotated
 * classes and their {@code @Component} / {@code @Service} collaborators must therefore reside
 * under that package to be picked up automatically.
 */
public final class WcfRunner {

	/**
	 * Internal Spring bootstrap class, used only as an explicit {@link SpringApplicationBuilder}
	 * source to activate WCF's auto-configuration and component scan.
	 *
	 * <p>Named with the {@code A12} prefix so it is immediately recognisable as a WCF
	 * infrastructure class in Spring context logs and stack traces — and to distinguish it from any
	 * application-level bootstrap a consumer or the CLI may define.</p>
	 *
	 * <p>{@code @EnableAutoConfiguration} imports {@link BeanConfiguration} via
	 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
	 * providing the default supplier, consumer and {@link WorkspaceConversionService} beans.</p>
	 *
	 * <p>{@code @ComponentScan} over {@code com.mgmtp.a12} registers all
	 * {@link com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter @WcfConverter}-annotated
	 * converters and their collaborators. {@link TypeExcludeFilter} and
	 * {@link AutoConfigurationExcludeFilter} are included to mirror {@code @SpringBootApplication}
	 * behaviour: test-only types are excluded and auto-configuration classes are not
	 * double-registered via the component scan.</p>
	 *
	 * <p>Deliberately <em>not</em> annotated with {@code @Configuration} or
	 * {@code @SpringBootApplication}: without {@code @Component} (or any meta-annotation of it),
	 * this class is invisible to component scans, so it is never accidentally picked up as a bean
	 * in a consumer's context or in the CLI's scan over {@code com.mgmtp.a12}.</p>
	 */
	@EnableAutoConfiguration
	@ComponentScan(
		basePackages = "com.mgmtp.a12",
		excludeFilters = {
			@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
			@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
		}
	)
	static class A12WcfBootstrap {
	}

	/** Lazily creates the application context on first {@link WcfRunner#run} call; thread-safe by class-loading guarantee. */
	private static final class ContextHolder {
		static final ConfigurableApplicationContext CONTEXT =
			new SpringApplicationBuilder(A12WcfBootstrap.class)
				.web(WebApplicationType.NONE)
				.bannerMode(Banner.Mode.OFF)
				.run();
	}

	private WcfRunner() {
	}

	/**
	 * Runs the full conversion pipeline against the current classpath.
	 *
	 * The Spring context is initialized on the first call and reused thereafter.
	 * Relative paths are resolved against the current working directory. The output
	 * directory is created if it does not exist.
	 *
	 * @param inputWorkspaceDir input workspace directory (must exist and be a directory)
	 * @param outputSeedDataDir output seed data models directory (created if missing)
	 * @throws IllegalArgumentException if either argument is null, or if the input workspace directory does not exist or is not a directory
	 * @throws java.io.UncheckedIOException if the output directory cannot be created
	 */
	public static void run(Path inputWorkspaceDir, Path outputSeedDataDir) {
		ContextHolder.CONTEXT.getBean(WorkspaceConversionService.class)
			.process(inputWorkspaceDir, outputSeedDataDir);
	}
}
