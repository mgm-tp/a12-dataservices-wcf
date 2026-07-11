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
import java.util.List;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.loader.internal.ConverterModules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that the converter fat-jar produced by the core module is loaded in an isolated class
 * loader, that its `@WcfConverter` beans are discovered and globally ordered, and that the
 * shared SPI type identity is preserved across the class-loader boundary.
 */
public class WcfCliConverterLoadingIT {

	private static Path convertersJar() {
		String jar = System.getProperty("wcf.test.convertersJar");
		assertTrue(jar != null && !jar.isBlank(), "System property 'wcf.test.convertersJar' must be set by the build");
		return Path.of(jar);
	}

	@Test public void loadsConvertersInIsolatedClassLoaderAndOrdersThem() throws Exception {
		try (ConfigurableApplicationContext parent = new SpringApplicationBuilder(WcfCli.class)
			.web(WebApplicationType.NONE).run();
			ConverterModules modules = ConverterModules.load(List.of(convertersJar()),
				WcfCli.class.getClassLoader(), parent)) {

			List<WorkspaceConverter> converters = modules.converters();

			assertEquals(converters.size(), 3, "expected all test converters to be discovered");

			// Global ordering by @WcfConverter(order = ...): 1, then 2, then 3.
			assertEquals(converters.get(0).getClass().getSimpleName(), "ConverterModifyAndAdd");
			assertEquals(converters.get(1).getClass().getSimpleName(), "ConverterMoveAndAddFile");
			// ConverterUsesLocalService can only be instantiated if its module-local @Service collaborator
			// was component-scanned from the jar; its mere presence proves module-local stereotype wiring.
			assertEquals(converters.get(2).getClass().getSimpleName(), "ConverterUsesLocalService");

			// Isolation: converters are loaded by the module's own class loader, not the CLI loader.
			ClassLoader converterLoader = converters.get(0).getClass().getClassLoader();
			assertNotSame(converterLoader, WcfCli.class.getClassLoader(),
				"converter must be loaded by the isolated module class loader");

			// Cross-loader type identity: the shared SPI interface resolves to the same Class instance,
			// otherwise getBeansOfType(WorkspaceConverter.class) could not have matched these beans.
			assertTrue(WorkspaceConverter.class.isInstance(converters.get(0)),
				"converter must be assignable to the shared WorkspaceConverter SPI");
		}
	}
}
