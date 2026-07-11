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

import java.io.IOException;
import java.util.Properties;

import com.vdurmont.semver4j.Semver;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * Singleton class to provide application version information.
 */
@Slf4j
public class ApplicationVersion {

	private static final ApplicationVersion VERSION = new ApplicationVersion();
	private final Semver version;

	/**
	 * Returns the singleton instance of {@link ApplicationVersion}.
	 *
	 * @return application version instance.
	 */
	public static ApplicationVersion getInstance() {
		return VERSION;
	}

	/**
	 * Returns the application version as a {@link Semver} object.
	 *
	 * @return application version.
	 */
	public static Semver getVersion() {
		return getInstance().version;
	}

	private ApplicationVersion() {
		version = new Semver(loadVersionFromProperty());
	}

	@NonNull private String loadVersionFromProperty() {
		Properties properties = new Properties();
		try {
			properties.load(getClass().getResourceAsStream("/version.properties"));
			return (String) properties.get("version");
		} catch (IOException e) {
			log.error("Can not load version properties: ".concat(e.getMessage()));
		}
		return "";
	}

	public static class VersionProvider implements CommandLine.IVersionProvider {
		@Override public String[] getVersion() {
			return new String[] { ApplicationVersion.getVersion().toString() };
		}
	}
}
