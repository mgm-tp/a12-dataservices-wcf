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
package com.mgmtp.a12.wcf.testfixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Test helper that creates temp directories and deletes them (recursively) on {@link #cleanup()}.
 *
 * Compose it into a test (`private final TempDirs tempDirs = new TempDirs();`), create dirs via
 * {@link #create(String)}, and call {@link #cleanup()} from an `@AfterMethod`. Cleanup is
 * best-effort: failures are ignored so they never mask a test result.
 */
public final class TempDirs {

	private final List<Path> created = new ArrayList<>();

	/**
	 * Creates a temp directory registered for later {@link #cleanup()}.
	 *
	 * @param prefix temp directory name prefix
	 * @return the created directory
	 * @throws IOException if the directory cannot be created
	 */
	public Path create(String prefix) throws IOException {
		Path dir = Path.of(System.getProperty("java.io.tmpdir"), prefix + "-" + UUID.randomUUID());
		created.add(dir);
		Files.createDirectory(dir);
		return dir;
	}

	/**
	 * Recursively deletes every directory created via {@link #create(String)} and forgets them.
	 */
	public void cleanup() {
		for (Path root : created) {
			if (!Files.exists(root)) {
				continue;
			}
			try (Stream<Path> paths = Files.walk(root)) {
				// Files.walk yields top-down; reverse order ensures children are deleted before their parent,
				// since deleting a non-empty directory would fail silently (exception is swallowed below).
				paths.sorted(Comparator.reverseOrder()).forEach(TempDirs::deleteQuietly);
			} catch (IOException | UncheckedIOException ignored) {
				// best-effort: the OS reclaims temp space anyway
			}
		}
		created.clear();
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
