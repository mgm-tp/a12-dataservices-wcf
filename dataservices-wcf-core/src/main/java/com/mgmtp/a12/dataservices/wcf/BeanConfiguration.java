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

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.mgmtp.a12.dataservices.wcf.internal.DefaultWorkspaceConsumer;
import com.mgmtp.a12.dataservices.wcf.internal.DefaultWorkspaceConversionService;
import com.mgmtp.a12.dataservices.wcf.internal.DefaultWorkspaceSupplier;
import com.mgmtp.a12.model.header.DefaultHeaderParser;
import com.mgmtp.a12.model.header.HeaderParser;

/**
 * Auto-configuration for WCF beans.
 */
@AutoConfiguration public class BeanConfiguration {

	@ConditionalOnMissingBean(WorkspaceFactory.class)
	@Bean WorkspaceFactory workspaceFactory() {
		return WorkspaceFactory.getInstance();
	}

	@ConditionalOnMissingBean(HeaderParser.class)
	@Bean HeaderParser headerParser() {
		return new DefaultHeaderParser();
	}

	@ConditionalOnMissingBean(IWorkspaceSupplier.class)
	@Bean IWorkspaceSupplier workspaceSupplier(WorkspaceFactory workspaceFactory, HeaderParser headerParser) {
		return new DefaultWorkspaceSupplier(workspaceFactory, headerParser);
	}

	@ConditionalOnMissingBean(IWorkspaceConsumer.class)
	@Bean IWorkspaceConsumer workspaceConsumer() {
		return new DefaultWorkspaceConsumer();
	}

	@ConditionalOnMissingBean(WorkspaceConversionService.class)
	@Bean WorkspaceConversionService workspaceConversionService(List<WorkspaceConverter> converters, IWorkspaceSupplier workspaceSupplier,
		IWorkspaceConsumer workspaceConsumer) {
		return new DefaultWorkspaceConversionService(converters, workspaceSupplier, workspaceConsumer);
	}
}
