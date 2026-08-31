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
package com.mgmtp.a12.dataservices.wcf.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mgmtp.a12.dataservices.wcf.IWorkspaceConsumer;
import com.mgmtp.a12.dataservices.wcf.IWorkspaceSupplier;
import com.mgmtp.a12.dataservices.wcf.WorkspaceConversionService;
import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

import lombok.RequiredArgsConstructor;

/**
 * Default implementation of {@link WorkspaceConversionService}.
 *
 * Validates and normalizes input paths, then loads the workspace via {@link IWorkspaceSupplier},
 * applies each registered {@link WorkspaceConverter} in order, and writes the result via
 * {@link IWorkspaceConsumer}.
 */
@RequiredArgsConstructor
public class DefaultWorkspaceConversionService implements WorkspaceConversionService {

	private final List<WorkspaceConverter> converters;
	private final IWorkspaceSupplier workspaceSupplier;
	private final IWorkspaceConsumer workspaceConsumer;

	@Override public void process(Path workspaceLocation, Path seedDataPath) {
		if (workspaceLocation == null) {
			throw new IllegalArgumentException("workspaceLocation must not be null");
		}
		if (seedDataPath == null) {
			throw new IllegalArgumentException("seedDataPath must not be null");
		}
		workspaceLocation = normalize(workspaceLocation);
		seedDataPath = normalize(seedDataPath);
		if (!Files.isDirectory(workspaceLocation)) {
			throw new IllegalArgumentException(
				"Input workspace directory does not exist or is not a directory: " + workspaceLocation);
		}
		try {
			Files.createDirectories(seedDataPath);
		} catch (IOException e) {
			throw new UncheckedIOException("Unable to create output seed data directory: " + seedDataPath, e);
		}
		Workspace ws = workspaceSupplier.get(workspaceLocation);
		for (WorkspaceConverter converter : converters) {
			ws = convertWithModuleClassLoader(converter, ws);
			if (ws == null) {
				throw new IllegalStateException("Converter returned null: " + converter.getClass().getName());
			}
		}
		workspaceConsumer.accept(ws, seedDataPath);
	}

	private static Path normalize(Path path) {
		if (path.isAbsolute()) {
			return path.normalize();
		}
		Path cwd = Path.of("").toAbsolutePath().normalize();
		return cwd.resolve(path).normalize();
	}

	/**
	 * Runs a single converter with the thread context class loader (TCCL) set to the converter's own
	 * class loader, restoring the previous TCCL afterwards.
	 *
	 * Converters may be loaded from isolated module jars whose resources are visible only through the
	 * module's own class loader, not through the launching application's class loader. Code that
	 * resolves classpath resources via the TCCL (a common idiom in serializers, JAXB, `ServiceLoader`
	 * and similar libraries) would otherwise fail to find resources bundled inside the converter jar.
	 * Aligning the TCCL with the converter's class loader makes those lookups resolve against the jar
	 * the converter was loaded from.
	 *
	 * @param converter the converter to run
	 * @param ws        the workspace to convert
	 * @return the converted workspace
	 */
	private static Workspace convertWithModuleClassLoader(WorkspaceConverter converter, Workspace ws) {
		Thread current = Thread.currentThread();
		ClassLoader previous = current.getContextClassLoader();
		current.setContextClassLoader(converter.getClass().getClassLoader());
		try {
			return converter.convert(ws);
		} catch (LinkageError e) {
			throw new IllegalStateException(versionConflictMessage(converter.getClass().getName(), e), e);
		} finally {
			current.setContextClassLoader(previous);
		}
	}

	/**
	 * Builds a human-readable explanation for a {@link LinkageError} raised while running a converter.
	 *
	 * A {@link LinkageError} (and its subtypes {@link NoSuchMethodError}, {@link NoSuchFieldError},
	 * {@link NoClassDefFoundError}, {@link AbstractMethodError}, loader-constraint violations) at this
	 * point almost always means the converter jar bundles a different version of a class than the WCF
	 * runtime provides. Shared libraries are always loaded from the WCF runtime (its versions win), so
	 * the actionable advice is to build the converter against the versions the runtime ships.
	 *
	 * @param converterClassName the converter that triggered the error
	 * @param error              the linkage error
	 * @return a descriptive message
	 */
	private static String versionConflictMessage(String converterClassName, LinkageError error) {
		return ("Converter '%s' failed with %s: %s. This usually means the converter jar bundles a different "
			+ "version of a class or library than the WCF runtime provides. Shared libraries (the WCF SPI, the "
			+ "A12 model API, Spring and Jackson) are always loaded from the WCF runtime and its versions take "
			+ "precedence; the converter must be built against those same versions.")
			.formatted(converterClassName, error.getClass().getName(), error.getMessage());
	}
}
