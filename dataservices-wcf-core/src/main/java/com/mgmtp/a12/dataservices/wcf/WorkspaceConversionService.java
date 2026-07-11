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

/**
 * Service orchestrating workspace conversion:
 *
 *   - Loads a workspace (models and additional files) from `workspaceLocation`.
 *   - Applies registered {@link WorkspaceConverter} instances sequentially in defined order (output of one is input to the next).
 *   - Stores the final workspace state into the structure rooted at `seedDataPath`.
 *
 * Implementations should be deterministic and produce no side effects outside the provided output location.
 */
public interface WorkspaceConversionService {

	/**
	 * Executes the full conversion pipeline for the given workspace.
	 *
	 * @param workspaceLocation input directory containing source files and models
	 * @param seedDataPath destination root where resulting models and supplemental files are stored
	 */
	void process(Path workspaceLocation, Path seedDataPath);
}
