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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class DefaultWorkspaceConversionServiceTest {

	@Test public void convertersAppliedAndConsumerCalled() throws Exception {
		WorkspaceConverter c1 = mock(WorkspaceConverter.class);
		WorkspaceConverter c2 = mock(WorkspaceConverter.class);
		Workspace w0 = mock(Workspace.class);
		Workspace w1 = mock(Workspace.class);
		Workspace w2 = mock(Workspace.class);

		when(c1.convert(w0)).thenReturn(w1);
		when(c2.convert(w1)).thenReturn(w2);

		DefaultWorkspaceSupplier supplier = mock(DefaultWorkspaceSupplier.class);
		DefaultWorkspaceConsumer consumer = mock(DefaultWorkspaceConsumer.class);
		when(supplier.get(any())).thenReturn(w0);

		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(c1, c2), supplier, consumer);

		Path workspaceDir = Files.createTempDirectory("wcf-test-ws");
		Path seedDir = Files.createTempDirectory("wcf-test-seed");
		try {
			svc.process(workspaceDir, seedDir);
		} finally {
			deleteDir(workspaceDir);
			deleteDir(seedDir);
		}

		verify(c1).convert(w0);
		verify(c2).convert(w1);
		verify(consumer).accept(w2, seedDir);
	}

	@Test public void setsThreadContextClassLoaderToConverterDuringConvertAndRestoresAfter() throws Exception {
		Workspace w0 = mock(Workspace.class);
		AtomicReference<ClassLoader> tcclDuringConvert = new AtomicReference<>();

		// Concrete (non-mock) converter so getClass().getClassLoader() is deterministic.
		WorkspaceConverter converter = new WorkspaceConverter() {
			@Override public Workspace convert(Workspace workspace) {
				tcclDuringConvert.set(Thread.currentThread().getContextClassLoader());
				return workspace;
			}
		};

		DefaultWorkspaceSupplier supplier = mock(DefaultWorkspaceSupplier.class);
		DefaultWorkspaceConsumer consumer = mock(DefaultWorkspaceConsumer.class);
		when(supplier.get(any())).thenReturn(w0);

		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(converter), supplier, consumer);

		Path workspaceDir = Files.createTempDirectory("wcf-test-ws");
		Path seedDir = Files.createTempDirectory("wcf-test-seed");
		Thread current = Thread.currentThread();
		ClassLoader original = current.getContextClassLoader();
		ClassLoader sentinel = new URLClassLoader(new java.net.URL[0], original);
		current.setContextClassLoader(sentinel);
		try {
			svc.process(workspaceDir, seedDir);

			assertSame(tcclDuringConvert.get(), converter.getClass().getClassLoader(),
				"TCCL during convert must be the converter's own class loader");
			assertSame(current.getContextClassLoader(), sentinel, "TCCL must be restored after process");
		} finally {
			current.setContextClassLoader(original);
			deleteDir(workspaceDir);
			deleteDir(seedDir);
		}
	}

	@Test public void translatesLinkageErrorIntoVersionConflictMessage() throws Exception {
		Workspace w0 = mock(Workspace.class);

		// A converter bundling an incompatible library version surfaces as a LinkageError subtype.
		WorkspaceConverter converter = new WorkspaceConverter() {
			@Override public Workspace convert(Workspace workspace) {
				throw new NoSuchMethodError("com.example.Bundled.method()V");
			}
		};

		DefaultWorkspaceSupplier supplier = mock(DefaultWorkspaceSupplier.class);
		DefaultWorkspaceConsumer consumer = mock(DefaultWorkspaceConsumer.class);
		when(supplier.get(any())).thenReturn(w0);

		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(converter), supplier, consumer);

		Path workspaceDir = Files.createTempDirectory("wcf-test-ws");
		Path seedDir = Files.createTempDirectory("wcf-test-seed");
		try {
			IllegalStateException ex =
				expectThrows(IllegalStateException.class, () -> svc.process(workspaceDir, seedDir));

			assertTrue(ex.getMessage().contains("different "), "message must explain the version conflict: " + ex.getMessage());
			assertTrue(ex.getMessage().contains(converter.getClass().getName()), "message must name the converter");
			assertTrue(ex.getCause() instanceof NoSuchMethodError, "original LinkageError must be preserved as cause");
		} finally {
			deleteDir(workspaceDir);
			deleteDir(seedDir);
		}
	}

	@Test public void throwsIllegalArgumentExceptionWhenWorkspaceLocationIsNull() {
		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(), mock(DefaultWorkspaceSupplier.class), mock(DefaultWorkspaceConsumer.class));

		expectThrows(IllegalArgumentException.class, () -> svc.process(null, Path.of("/some/output")));
	}

	@Test public void throwsIllegalArgumentExceptionWhenSeedDataPathIsNull() {
		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(), mock(DefaultWorkspaceSupplier.class), mock(DefaultWorkspaceConsumer.class));

		expectThrows(IllegalArgumentException.class, () -> svc.process(Path.of("/some/input"), null));
	}

	@Test public void normalizesRelativeWorkspaceLocation() throws Exception {
		// Path.of(".") is the CWD expressed as a relative path — always a valid directory
		Workspace w0 = mock(Workspace.class);
		DefaultWorkspaceSupplier supplier = mock(DefaultWorkspaceSupplier.class);
		DefaultWorkspaceConsumer consumer = mock(DefaultWorkspaceConsumer.class);
		when(supplier.get(any())).thenReturn(w0);

		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(), supplier, consumer);

		Path seedDir = Files.createTempDirectory("wcf-test-seed");
		try {
			svc.process(Path.of("."), seedDir);
		} finally {
			deleteDir(seedDir);
		}

		ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
		verify(supplier).get(captor.capture());
		assertTrue(captor.getValue().isAbsolute(), "workspace path passed to supplier must be absolute after normalization");
	}

	@Test public void normalizesRelativeSeedDataPath() throws Exception {
		Path workspaceDir = Files.createTempDirectory("wcf-test-ws");
		Workspace w0 = mock(Workspace.class);
		DefaultWorkspaceSupplier supplier = mock(DefaultWorkspaceSupplier.class);
		DefaultWorkspaceConsumer consumer = mock(DefaultWorkspaceConsumer.class);
		when(supplier.get(any())).thenReturn(w0);

		DefaultWorkspaceConversionService svc =
			new DefaultWorkspaceConversionService(List.of(), supplier, consumer);

		String seedDirName = "wcf-test-seed-" + System.nanoTime();
		Path relSeedPath = Path.of(seedDirName);
		Path absSeedPath = Path.of("").toAbsolutePath().resolve(seedDirName);
		try {
			svc.process(workspaceDir, relSeedPath);
		} finally {
			deleteDir(workspaceDir);
			deleteDir(absSeedPath);
		}

		ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
		verify(consumer).accept(any(), captor.capture());
		assertTrue(captor.getValue().isAbsolute(), "seed path passed to consumer must be absolute after normalization");
	}

	private static void deleteDir(Path dir) throws Exception {
		if (!Files.exists(dir)) {
			return;
		}
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); }
			});
		}
	}
}
