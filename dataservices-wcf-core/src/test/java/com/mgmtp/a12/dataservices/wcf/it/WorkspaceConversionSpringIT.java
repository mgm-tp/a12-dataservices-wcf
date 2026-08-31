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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConversionService;
import com.mgmtp.a12.dataservices.wcf.internal.OutputConflictException;
import com.mgmtp.a12.dataservices.wcf.it.converters.TestHeader;
import com.mgmtp.a12.wcf.testfixtures.TempDirs;

import lombok.extern.slf4j.Slf4j;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Integration test for full pipeline (supplier -> converters -> consumer) in Spring context.
 */
@Slf4j
@SpringBootTest(classes = { TestBeansConfig.class })
public class WorkspaceConversionSpringIT extends AbstractTestNGSpringContextTests {

	@Autowired private WorkspaceConversionService conversionService;

	private final TempDirs tempDirs = new TempDirs();

	@AfterMethod(alwaysRun = true)
	public void cleanup() {
		tempDirs.cleanup();
	}

	/**
	 * Successful pipeline run – two converters modify and extend the workspace.
	 */
	@Test public void fullPipelineSuccess() throws Exception {
		Path wsDir = tempDirs.create("it-spring-ws");
		Path seedDir = tempDirs.create("it-spring-seed");
		prepareWorkspace(wsDir, seedDir);

		conversionService.process(wsDir, seedDir);

		Path model1 = seedDir.resolve("data/models/model1.json");
		Path model2 = seedDir.resolve("data/models/model2.json");

		assertTrue(Files.exists(model1), "model1.json missing");

		assertTrue(Files.exists(model2), "model2.json missing");
		String m1 = Files.readString(model1);

		assertTrue(m1.contains("\"converted\":true"), "model1 not modified");

		assertTrue(m1.contains("\"value\":1"), "original value lost");
		String m2 = Files.readString(model2);

		assertTrue(m2.contains("\"generated\":true"), "model2 not added");

		Path genFile = seedDir.resolve("generated/info.txt");

		assertTrue(Files.exists(genFile), "generated file missing");

		assertEquals(Files.readString(genFile), "INFO_OK");
	}

	/**
	 * Conflict scenario – target model1.json already exists, expecting an exception.
	 */
	@Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "Unable to store model model1")
	public void failsOnExistingModel() throws Exception {
		Path wsDir = tempDirs.create("it-spring-ws-conflict");
		Files.writeString(wsDir.resolve("model1.json"), ModelUtils.createValidModel(TestHeader.builder().id("model1").build(), "{\"value\":2}"));

		Path seedDir = tempDirs.create("it-spring-seed-conflict");

		logPaths(wsDir, seedDir);

		Path conflictingModel = seedDir.resolve("data/models/model1.json");
		Files.createDirectories(conflictingModel.getParent());
		Files.writeString(conflictingModel, "PRE_EXISTING");

		conversionService.process(wsDir, seedDir);
	}

	/**
	 * Conflict scenario – target model1.json already exists, expecting an exception.
	 */
	@Test(expectedExceptions = OutputConflictException.class, expectedExceptionsMessageRegExp = "Unable to store file .*[/\\\\]generated[/\\\\]info\\.txt")
	public void failsOnExistingFileContent() throws Exception {
		Path wsDir = tempDirs.create("it-spring-ws-conflict");
		Path seedDir = tempDirs.create("it-spring-seed-conflict");
		prepareWorkspace(wsDir, seedDir);

		Path conflictingFile = seedDir.resolve("generated/info.txt");
		Files.createDirectories(conflictingFile.getParent());
		Files.writeString(conflictingFile, "PRE_EXISTING");

		conversionService.process(wsDir, seedDir);
	}

	/**
	 * Conflict scenario – target model1.json already exists, expecting an exception.
	 */
	@Test(expectedExceptions = OutputConflictException.class, expectedExceptionsMessageRegExp = "Unable to store file .*[/\\\\]moved\\.txt")
	public void failsOnExistingFilePath() throws Exception {
		Path wsDir = tempDirs.create("it-spring-ws-conflict");
		Path seedDir = tempDirs.create("it-spring-seed-conflict");
		prepareWorkspace(wsDir, seedDir);

		Path conflictingFile = seedDir.resolve("moved.txt");
		Files.createDirectories(conflictingFile.getParent());
		Files.writeString(conflictingFile, "PRE_EXISTING");

		conversionService.process(wsDir, seedDir);
	}

	private static void logPaths(Path wsDir, Path seedDir) {
		log.info("Workspace: {}", wsDir);
		log.info("Seed: {}", seedDir);
	}

	private static void prepareWorkspace(Path wsDir, Path seedDir) throws IOException {
		Files.writeString(wsDir.resolve("model1.json"), ModelUtils.createValidModel(TestHeader.builder().id("model1").build(), "{\"value\":1}"));
		Files.writeString(wsDir.resolve("existing_file.txt"), "ORIGINAL_CONTENT");
		logPaths(wsDir, seedDir);
	}
}
