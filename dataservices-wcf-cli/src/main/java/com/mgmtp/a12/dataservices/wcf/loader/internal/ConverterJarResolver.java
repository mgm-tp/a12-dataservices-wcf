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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Resolves a converter fat-jar into an isolated {@link ConverterModule}.
 *
 * Only *flat* fat-jars are supported: classes and all bundled dependencies live at the jar root, so
 * the jar is consumed in place by a plain {@link java.net.URLClassLoader}. Such jars are produced by
 * the Gradle Shadow plugin, the Maven Shade plugin, or by expanding all dependencies into a single
 * jar (a plain jar with no dependencies works too).
 *
 * Spring Boot `bootJar` archives are deliberately *not* supported: their nested jar-in-jar layout
 * (`BOOT-INF/lib/*.jar`) cannot be read by a `URLClassLoader`. Such a jar is rejected with a clear
 * error so the converter is repackaged as a flat fat-jar instead.
 *
 * The base package roots that contain the converter's own classes are derived from the jar so the
 * caller can restrict Spring component scanning to exactly those packages.
 */
final class ConverterJarResolver {

	private static final String BOOT_INF = "BOOT-INF/";

	private ConverterJarResolver() {
	}

	static ConverterModule resolve(Path jar, ClassLoader parent) throws IOException {
		String moduleName = jar.getFileName().toString();
		try (JarFile jarFile = new JarFile(jar.toFile())) {
			if (isSpringBoot(jarFile)) {
				throw new IOException("Spring Boot fat-jars are not supported as converter jars (nested "
					+ "BOOT-INF/lib jars cannot be loaded): " + moduleName
					+ ". Repackage the converter as a flat fat-jar (e.g. Gradle Shadow / Maven Shade).");
			}
			List<String> packages = new ArrayList<>();
			jarFile.stream()
				.map(JarEntry::getName)
				.filter(ConverterJarResolver::isApplicationClass)
				.map(ConverterJarResolver::packageOf)
				.forEach(packages::add);
			URL[] urls = { jar.toUri().toURL() };
			return new ConverterModule(moduleName, new URLClassLoader(moduleName, urls, parent),
				packageRoots(packages));
		}
	}

	private static boolean isSpringBoot(JarFile jarFile) {
		return jarFile.getJarEntry(BOOT_INF) != null || jarFile.stream().anyMatch(e -> e.getName().startsWith(BOOT_INF));
	}

	private static boolean isApplicationClass(String entryName) {
		return entryName.endsWith(".class")
			&& !entryName.equals("module-info.class")
			&& !entryName.startsWith("META-INF/")
			&& entryName.contains("/"); // skip the default (unnamed) package
	}

	private static String packageOf(String entryName) {
		int lastSlash = entryName.lastIndexOf('/');
		return entryName.substring(0, lastSlash).replace('/', '.');
	}

	/**
	 * Reduces a collection of package names to the minimal set of roots: a package is dropped when an
	 * ancestor package is also present, since component scanning is recursive.
	 */
	private static List<String> packageRoots(List<String> packages) {
		TreeSet<String> sorted = new TreeSet<>(packages);
		List<String> roots = new ArrayList<>();
		for (String pkg : sorted) {
			boolean covered = roots.stream().anyMatch(root -> pkg.equals(root) || pkg.startsWith(root + "."));
			if (!covered) {
				roots.add(pkg);
			}
		}
		return roots;
	}
}
