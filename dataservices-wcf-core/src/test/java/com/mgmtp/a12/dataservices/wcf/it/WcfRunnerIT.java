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
package com.mgmtp.a12.dataservices.wcf.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.WcfRunner;
import com.mgmtp.a12.dataservices.wcf.it.converters.TestHeader;
import com.mgmtp.a12.wcf.testfixtures.TempDirs;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Integration test for the supported library entry point {@link WcfRunner}.
 *
 * Exercises the real Spring bootstrap (component scan over `com.mgmtp.a12` + WCF
 * auto-configuration) rather than a pre-assembled test context, so the test converters on the
 * test classpath are discovered the same way a library consumer's converters would be.
 */
public class WcfRunnerIT {

	private final TempDirs tempDirs = new TempDirs();

	@AfterMethod(alwaysRun = true)
	public void cleanup() {
		tempDirs.cleanup();
	}

	/**
	 * Full bootstrap + pipeline run via {@link WcfRunner#run(Path, Path)}.
	 */
	@Test public void runBootstrapsAndConverts() throws Exception {
		Path wsDir = tempDirs.create("it-runner-ws");
		Path seedDir = tempDirs.create("it-runner-seed");
		prepareWorkspace(wsDir);

		WcfRunner.run(wsDir, seedDir);

		Path model1 = seedDir.resolve("data/models/model1.json");
		Path model2 = seedDir.resolve("data/models/model2.json");
		assertTrue(Files.exists(model1), "model1.json missing");
		assertTrue(Files.exists(model2), "model2.json (added by converter) missing");

		String m1 = Files.readString(model1);
		assertTrue(m1.contains("\"converted\":true"), "model1 not modified by converter");
		assertTrue(m1.contains("\"value\":1"), "original value lost");

		Path genFile = seedDir.resolve("generated/info.txt");
		assertTrue(Files.exists(genFile), "generated file missing");
		assertEquals(Files.readString(genFile), "INFO_OK");
	}

	/**
	 * Output directory is created when it does not yet exist.
	 */
	@Test public void createsMissingOutputDirectory() throws Exception {
		Path wsDir = tempDirs.create("it-runner-ws");
		prepareWorkspace(wsDir);
		Path seedDir = tempDirs.create("it-runner-seed").resolve("nested/out");

		WcfRunner.run(wsDir, seedDir);

		assertTrue(Files.isDirectory(seedDir), "output directory not created");
		assertTrue(Files.exists(seedDir.resolve("data/models/model1.json")), "model1.json missing");
	}

	/**
	 * Writes the minimal workspace the test converters operate on: model1 plus the file that
	 * `ConverterMoveAndAddFile` relocates as a copy-through entry.
	 */
	private static void prepareWorkspace(Path wsDir) throws IOException {
		Files.writeString(wsDir.resolve("model1.json"),
			ModelUtils.createValidModel(TestHeader.builder().id("model1").build(), "{\"value\":1}"));
		Files.writeString(wsDir.resolve("existing_file.txt"), "ORIGINAL_CONTENT");
	}

	/**
	 * Missing input workspace directory fails fast with a clear message.
	 */
	@Test
	public void failsOnMissingWorkspaceDir() throws IOException {
		Path missing = tempDirs.create("it-runner").resolve("does-not-exist");
		Path seedDir = tempDirs.create("it-runner-seed");

		assertThrows(IllegalArgumentException.class, () -> WcfRunner.run(missing, seedDir));
	}

	/**
	 * Null arguments are rejected immediately with a clear message.
	 */
	@Test
	public void failsOnNullArguments() throws IOException {
		Path wsDir = tempDirs.create("it-runner-ws");
		Path seedDir = tempDirs.create("it-runner-seed");

		assertThrows(IllegalArgumentException.class, () -> WcfRunner.run(null, seedDir));
		assertThrows(IllegalArgumentException.class, () -> WcfRunner.run(wsDir, null));
	}

}
