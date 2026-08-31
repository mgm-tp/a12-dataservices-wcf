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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.domain.FileTuple;
import com.mgmtp.a12.dataservices.wcf.domain.ModelTuple;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;
import com.mgmtp.a12.wcf.testfixtures.TempDirs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DefaultWorkspaceConsumerTest {

	private final TempDirs tempDirs = new TempDirs();

	@AfterMethod(alwaysRun = true)
	public void cleanup() {
		tempDirs.cleanup();
	}

	@Test public void writesModelsAndFiles() throws Exception {
		Workspace ws = mock(Workspace.class);

		ModelTuple modelTuple = mock(ModelTuple.class);
		when(modelTuple.getContent()).thenReturn("{\"k\":1}");

		Map<String, ModelTuple> models = new HashMap<>();
		models.put("model1", modelTuple);
		when(ws.getModels()).thenReturn(models);

		FileTuple fileTuple = mock(FileTuple.class);
		when(fileTuple.getOutputPath()).thenReturn("extra/hello.txt");
		when(fileTuple.getContent()).thenReturn("HELLO".getBytes(StandardCharsets.UTF_8));

		Map<String, FileTuple> files = new HashMap<>();
		files.put("ignoredKey", fileTuple);
		when(ws.getFiles()).thenReturn(files);
		when(ws.getInputDir()).thenReturn(tempDirs.create("inputDir").toString());

		Path seed = tempDirs.create("seed");
		new DefaultWorkspaceConsumer().accept(ws, seed);

		// Assert model file
		Path modelOut = seed.resolve("data/models/model1.json");
		assertTrue(Files.exists(modelOut), "Model file not created");
		String content = Files.readString(modelOut);
		assertTrue(content.contains("\"k\":1"), "Model content mismatch");

		// Assert extra file
		Path fileOut = seed.resolve("extra/hello.txt");
		assertTrue(Files.exists(fileOut), "Extra file not created");
		assertEquals(Files.readString(fileOut), "HELLO");
	}
}
