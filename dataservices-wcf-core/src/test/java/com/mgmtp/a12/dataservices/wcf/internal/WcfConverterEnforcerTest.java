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

import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

public class WcfConverterEnforcerTest {

	@WcfConverter(order = 0)
	static class AnnotatedConverter implements WorkspaceConverter {
		@Override public Workspace convert(Workspace w) {return w;}
	}

	@WcfConverter(order = 1)
	static class AnnotatedNonConverter {}

	static class PlainConverter implements WorkspaceConverter {
		@Override public Workspace convert(Workspace w) {return w;}
	}

	static class PlainNonConverter {}

	private final WcfConverterEnforcer enforcer = new WcfConverterEnforcer();

	@Test
	public void annotatedAndImplementsThrows() {
		enforcer.postProcessBeforeInitialization(new AnnotatedConverter(), "ac");
	}

	@Test
	public void notAnnotatedNotImplementsOk() {
		enforcer.postProcessBeforeInitialization(new PlainNonConverter(), "pnc");
	}

	@Test(expectedExceptions = IllegalStateException.class)
	public void annotatedWithoutInterfaceOk() {
		enforcer.postProcessBeforeInitialization(new AnnotatedNonConverter(), "anc");
	}

	@Test(expectedExceptions = IllegalStateException.class)
	public void notAnnotatedImplementsOk() {
		enforcer.postProcessBeforeInitialization(new PlainConverter(), "pc");
	}
}
