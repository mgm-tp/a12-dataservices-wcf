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

import com.mgmtp.a12.dataservices.wcf.domain.FileTuple;
import com.mgmtp.a12.dataservices.wcf.domain.ModelTuple;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;
import com.mgmtp.a12.dataservices.wcf.domain.internal.DefaultFileTuple;
import com.mgmtp.a12.dataservices.wcf.domain.internal.DefaultModelTuple;
import com.mgmtp.a12.dataservices.wcf.domain.internal.DefaultWorkspace;
import com.mgmtp.a12.model.header.Header;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Provides light-weight creation methods for creating workspace-related object instances.
 * Instances created are simple POJOs with no external resources.
 * Thread-safety:
 * Factory methods are stateless. The singleton accessor {@link #getInstance()} uses
 * the initialization-on-demand holder idiom (thread-safe, lazy, without synchronization overhead on reads).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkspaceFactory {
	/**
	 * Creates a workspace with empty models and files maps.
	 *
	 * @return new {@link Workspace} instance
	 */
	public Workspace createWorkspace() {
		return new DefaultWorkspace();
	}

	/**
	 * Creates a {@link ModelTuple} instance.
	 *
	 * @param header model header
	 * @param content JSON content of the model
	 * @return new {@link ModelTuple} instance
	 * @throws NullPointerException if the header or content is null
	 */
	public ModelTuple createModelTuple(@NonNull Header header, @NonNull String content) {
		return new DefaultModelTuple(header, content);
	}

	/**
	 * Creates an empty {@link FileTuple} instance.
	 *
	 * @return new {@link FileTuple} instance
	 */
	public FileTuple createFileTuple() {
		return new DefaultFileTuple();
	}

	/**
	 * Creates a new {@link FileTuple} instance with the output path and file content.
	 *
	 * @param outputPath output path
	 * @param content binary content
	 * @return new {@link FileTuple} instance
	 */
	public FileTuple createFileTuple(String outputPath, byte[] content) {
		FileTuple fileTuple = new DefaultFileTuple();
		fileTuple.setOutputPath(outputPath);
		fileTuple.setContent(content);
		return fileTuple;
	}

	/**
	 * Creates a new {@link FileTuple} instance with the output path only.
	 *
	 * @param outputPath output path
	 * @return new {@link FileTuple} instance
	 */
	public FileTuple createFileTuple(String outputPath) {
		FileTuple fileTuple = new DefaultFileTuple();
		fileTuple.setOutputPath(outputPath);
		return fileTuple;
	}

	/**
	 * Creates a new {@link FileTuple} instance with content only.
	 *
	 * @param content binary content of the file
	 * @return new {@link FileTuple} instance
	 */
	public FileTuple createFileTuple(byte[] content) {
		FileTuple fileTuple = new DefaultFileTuple();
		fileTuple.setContent(content);
		return fileTuple;
	}

	/**
	 * Returns a lazily initialized, thread-safe singleton instance of {@link WorkspaceFactory}.
	 *
	 * Implemented via the initialization-on-demand holder idiom: the JVM guarantees
	 * the inner Holder class is loaded (and the INSTANCE created) only upon the first call
	 * to this method and initialization runs exactly once without explicit synchronization.
	 *
	 * @return shared {@link WorkspaceFactory} instance
	 */
	public static WorkspaceFactory getInstance() {
		return Holder.INSTANCE;
	}

	// Thread-safe lazy holder
	private static final class Holder {
		private static final WorkspaceFactory INSTANCE = new WorkspaceFactory();
	}
}
