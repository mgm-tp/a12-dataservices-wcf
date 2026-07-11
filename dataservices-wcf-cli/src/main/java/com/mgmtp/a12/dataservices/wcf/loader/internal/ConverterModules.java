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
package com.mgmtp.a12.dataservices.wcf.loader.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter;
import com.mgmtp.a12.dataservices.wcf.internal.WcfConverterEnforcer;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@link WorkspaceConverter}s from one or more converter fat-jars, each isolated in its own
 * {@link java.net.URLClassLoader} and its own child Spring {@link ApplicationContext}.
 *
 * Each module's class loader uses standard parent-first delegation: any class the host CLI already
 * provides (the WCF SPI, the A12 model API, Spring, Jackson, logging, ...) is loaded from the common
 * parent so its type identity is shared across the host and every converter, which avoids
 * {@link LinkageError} loader-constraint violations. Only classes the host does not have - the
 * converter's own classes and any private dependency it bundles that the CLI lacks (e.g. Groovy) -
 * are loaded from the jar. The trade-off is intentional: the host's versions of shared libraries
 * win; a converter may add new libraries but cannot override a library the CLI already ships.
 *
 * Each module is exposed through a child context whose parent is the CLI's application context, so
 * converters can autowire shared beans (e.g. `WorkspaceFactory`). Scanning is scoped to the packages
 * that actually contain `@WcfConverter` classes and uses the default stereotype filters, so a
 * converter's own `@Component` / `@Service` / `@Repository` collaborators bundled in the same module
 * are registered too - while `@Configuration` classes (and therefore `@SpringBootApplication` /
 * `@EnableAsync` application bootstrap) are excluded. The {@link WcfConverterEnforcer} is registered
 * in every child context to preserve the fail-fast validation contract. The discovered converters
 * are merged across all modules and ordered globally by their `@WcfConverter(order = ...)` value.
 *
 * This type owns the created class loaders and child contexts; callers must {@link #close()} it once
 * conversion has finished.
 */
@Slf4j
public final class ConverterModules implements AutoCloseable {

	private final List<ConverterModule> modules;
	private final List<AnnotationConfigApplicationContext> contexts;
	private final List<WorkspaceConverter> converters;

	private ConverterModules(List<ConverterModule> modules, List<AnnotationConfigApplicationContext> contexts,
		List<WorkspaceConverter> converters) {
		this.modules = modules;
		this.contexts = contexts;
		this.converters = converters;
	}

	/**
	 * Loads and wires the converters contained in the given fat-jars.
	 *
	 * @param jars          flat converter fat-jars (shadow/uber or plain), never empty
	 * @param parentLoader  the class loader that loaded the WCF runtime; parent of every module loader
	 * @param parentContext the CLI application context providing shared beans for converter autowiring
	 * @return a closeable handle exposing the globally ordered converters
	 * @throws IOException if a jar cannot be read or extracted
	 */
	public static ConverterModules load(List<Path> jars, ClassLoader parentLoader, ApplicationContext parentContext)
		throws IOException {
		List<ConverterModule> modules = new ArrayList<>();
		List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();
		List<WorkspaceConverter> converters = new ArrayList<>();
		Path currentJar = null;
		try {
			for (Path jar : jars) {
				currentJar = jar;
				ConverterModule module = ConverterJarResolver.resolve(jar, parentLoader);
				modules.add(module);
				AnnotationConfigApplicationContext context = createContext(module, parentContext);
				contexts.add(context);
				converters.addAll(context.getBeansOfType(WorkspaceConverter.class).values());
			}
		} catch (LinkageError e) {
			closeQuietly(contexts, modules);
			throw new IllegalStateException(versionConflictMessage(currentJar, e), e);
		} catch (RuntimeException | IOException e) {
			closeQuietly(contexts, modules);
			throw e;
		}
		AnnotationAwareOrderComparator.sort(converters);
		log.info("Loaded {} converter(s) from {} module(s)", converters.size(), modules.size());
		return new ConverterModules(modules, contexts, List.copyOf(converters));
	}

	private static AnnotationConfigApplicationContext createContext(ConverterModule module,
		ApplicationContext parentContext) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.setId("wcf-converter[" + module.name() + "]");
		context.setParent(parentContext);
		context.setClassLoader(module.classLoader());
		// Preserve the @WcfConverter <-> WorkspaceConverter fail-fast contract inside the module context.
		context.register(WcfConverterEnforcer.class);

		// Two-pass, scoped scan:
		//
		// 1. Locate the @WcfConverter classes and derive the packages they live in (the module's own
		//    application code). This is restricted to @WcfConverter, which is unique to converters, so it
		//    never matches bundled third-party classes.
		// 2. Component-scan ONLY those converter packages with the default stereotype filters, so the
		//    converter's own @Component / @Service / @Repository collaborators are registered and can be
		//    autowired - while @Configuration classes are explicitly excluded. A converter fat-jar is
		//    frequently a full Spring Boot application jar; scanning its @Configuration /
		//    @SpringBootApplication / @EnableAsync (etc.) would try to bootstrap the converter's entire
		//    application context inside ours and fail. Scoping to the converter packages also keeps the
		//    bundled libraries (org.*, groovy, jackson, ...) out of the scan.
		List<String> converterPackages = findConverterPackages(module);
		if (converterPackages.isEmpty()) {
			log.warn("Converter module '{}' contains no @WcfConverter classes", module.name());
		} else {
			ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context, true);
			scanner.addExcludeFilter(new AnnotationTypeFilter(Configuration.class));
			scanner.scan(converterPackages.toArray(String[]::new));
		}
		context.refresh();
		return context;
	}

	private static List<String> findConverterPackages(ConverterModule module) {
		ClassPathScanningCandidateComponentProvider finder = new ClassPathScanningCandidateComponentProvider(false);
		finder.addIncludeFilter(new AnnotationTypeFilter(WcfConverter.class));
		finder.setResourceLoader(new PathMatchingResourcePatternResolver(module.classLoader()));

		TreeSet<String> packages = new TreeSet<>();
		for (String root : module.basePackages()) {
			for (BeanDefinition candidate : finder.findCandidateComponents(root)) {
				String className = candidate.getBeanClassName();
				if (className != null) {
					int lastDot = className.lastIndexOf('.');
					packages.add(lastDot < 0 ? "" : className.substring(0, lastDot));
				}
			}
		}
		return minimalRoots(packages);
	}

	/** Drops any package whose ancestor is also present, since component scanning is recursive. */
	private static List<String> minimalRoots(TreeSet<String> packages) {
		List<String> roots = new ArrayList<>();
		for (String pkg : packages) {
			boolean covered = roots.stream().anyMatch(root -> pkg.equals(root) || pkg.startsWith(root + "."));
			if (!covered) {
				roots.add(pkg);
			}
		}
		return roots;
	}

	/**
	 * Builds a human-readable explanation for a {@link LinkageError} raised while a converter module is
	 * being loaded and its beans instantiated. Such an error (including {@link NoSuchMethodError},
	 * {@link NoSuchFieldError}, {@link NoClassDefFoundError}, loader-constraint violations) at load time
	 * usually means the converter jar bundles a different version of a class than the CLI provides.
	 */
	private static String versionConflictMessage(Path jar, LinkageError error) {
		String module = jar == null ? "<unknown>" : jar.getFileName().toString();
		return ("Converter module '%s' could not be loaded due to %s: %s. This usually means the converter jar "
			+ "bundles a different version of a class or library than the WCF CLI provides. Shared libraries (the "
			+ "WCF SPI, the A12 model API, Spring and Jackson) are always loaded from the CLI and its versions take "
			+ "precedence (parent-first class loading); the converter must be built against those same versions.")
			.formatted(module, error.getClass().getName(), error.getMessage());
	}

	/** Globally ordered converters discovered across all loaded modules. */
	public List<WorkspaceConverter> converters() {
		return converters;
	}

	@Override public void close() {
		closeQuietly(contexts, modules);
	}

	private static void closeQuietly(List<AnnotationConfigApplicationContext> contexts, List<ConverterModule> modules) {
		for (AnnotationConfigApplicationContext context : contexts) {
			try {
				context.close();
			} catch (RuntimeException e) {
				log.warn("Failed to close converter context {}", context.getId(), e);
			}
		}
		for (ConverterModule module : modules) {
			try {
				module.close();
			} catch (IOException e) {
				log.warn("Failed to release converter module '{}'", module.name(), e);
			}
		}
	}
}
