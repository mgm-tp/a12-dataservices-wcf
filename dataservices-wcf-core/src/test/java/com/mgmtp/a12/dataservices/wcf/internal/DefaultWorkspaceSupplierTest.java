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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.domain.FileTuple;
import com.mgmtp.a12.dataservices.wcf.domain.ModelTuple;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;
import com.mgmtp.a12.dataservices.wcf.WorkspaceFactory;
import com.mgmtp.a12.model.header.Header;
import com.mgmtp.a12.model.header.HeaderParser;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class DefaultWorkspaceSupplierTest {

	@Test public void loadsModelJson() throws Exception {
		Path dir = Files.createTempDirectory("ws");
		Path json = dir.resolve("sample.json");
		Files.writeString(json, "{\"id\":\"m1\"}");

		HeaderParser headerParser = mock(HeaderParser.class);
		Header header = mock(Header.class);
		when(header.getId()).thenReturn("m1");
		when(headerParser.parseJson(anyString())).thenReturn(header);

		WorkspaceFactory factory = mock(WorkspaceFactory.class);
		Workspace ws = mock(Workspace.class);
		Map<String, ModelTuple> models = new HashMap<>();
		Map<String, FileTuple> files = new HashMap<>();
		when(ws.getModels()).thenReturn(models);
		when(ws.getFiles()).thenReturn(files);
		when(ws.getInputDir()).thenReturn(dir.toString());

		ModelTuple mt = mock(ModelTuple.class);
		when(factory.createWorkspace()).thenReturn(ws);
		when(factory.createModelTuple(eq(header), anyString())).thenReturn(mt);

		DefaultWorkspaceSupplier supplier = new DefaultWorkspaceSupplier(factory, headerParser);
		Workspace result = supplier.get(dir);

		// assert result == ws;
		assertSame(result, ws);
		// assert models.containsKey("m1") : "Model was not added";
		assertTrue(models.containsKey("m1"), "Model was not added");
		verify(factory).createModelTuple(eq(header), anyString());
	}
}
