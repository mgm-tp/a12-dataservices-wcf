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
package com.mgmtp.a12.dataservices.wcf.it.converters;

import java.nio.charset.StandardCharsets;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.WorkspaceFactory;
import com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

import lombok.RequiredArgsConstructor;

/**
 * Converter 2 – adds a generated file and remaps an existing file entry.
 * Packaged inside the converters test JAR (classifier: converters).
 */
@RequiredArgsConstructor
@WcfConverter(order = 2, description = "Add generated file")
public class ConverterMoveAndAddFile implements WorkspaceConverter {
	private final WorkspaceFactory workspaceFactory;

	@Override
	public Workspace convert(Workspace ws) {
		ws.getFiles().put("existing_file.txt",
			workspaceFactory.createFileTuple("moved.txt"));
		ws.getFiles().put("generatedInfo",
			workspaceFactory.createFileTuple("generated/info.txt", "INFO_OK".getBytes(StandardCharsets.UTF_8)));
		return ws;
	}
}
