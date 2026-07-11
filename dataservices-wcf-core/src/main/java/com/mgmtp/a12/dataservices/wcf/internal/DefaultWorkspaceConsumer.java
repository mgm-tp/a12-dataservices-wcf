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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.lang3.StringUtils;
import com.mgmtp.a12.dataservices.wcf.IWorkspaceConsumer;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

public class DefaultWorkspaceConsumer implements IWorkspaceConsumer {

	@Override public void accept(Workspace ws, Path seedDataPath) {
		File seedDataDir = seedDataPath.toFile();
		if (!seedDataDir.exists() && !seedDataDir.mkdirs()) {
			throw new IllegalStateException("Cannot create output directory: " + seedDataPath);
		}

		ws.getModels()
			.forEach((modelId, model) -> {
				String fileName = "%s.json".formatted(modelId);
				Path out = seedDataPath.resolve("data").resolve("models").resolve(fileName);
				try {
					Files.createDirectories(out.getParent());
					Files.writeString(out, model.getContent(), StandardOpenOption.CREATE_NEW);
				} catch (IOException e) {
					throw new OutputConflictException(fileName, out.toString(), "Unable to store model %s".formatted(modelId), e);
				}
			});

		ws.getFiles().entrySet().stream()
			.filter(e -> StringUtils.isNotEmpty(e.getValue().getOutputPath()))
			.forEach(e -> {
				Path out = seedDataPath.resolve(e.getValue().getOutputPath());
				byte[] content = e.getValue().getContent();
				try {
					Files.createDirectories(out.getParent());
					if (content != null) {
						Files.write(out, content, StandardOpenOption.CREATE_NEW);
					} else {
						Files.copy(Path.of(ws.getInputDir()).resolve(e.getKey()), out);
					}
				} catch (IOException ex) {
					throw new OutputConflictException(e.getKey(), e.getValue().getOutputPath(), "Unable to store file %s".formatted(out), ex);
				}
			});
	}
}
