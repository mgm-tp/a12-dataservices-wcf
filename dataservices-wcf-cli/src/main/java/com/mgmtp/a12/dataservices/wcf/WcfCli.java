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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.mgmtp.a12.dataservices.wcf.internal.DefaultWorkspaceConversionService;
import com.mgmtp.a12.dataservices.wcf.loader.internal.ConverterModules;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Command line interface for workspace conversion.
 */
@SpringBootApplication(scanBasePackages = "com.mgmtp.a12")
@Command(
	name = "wcf-cli",
	mixinStandardHelpOptions = true,
	description = "Converts a workspace into seed data models.",
	versionProvider = ApplicationVersion.VersionProvider.class
)
public class WcfCli implements Runnable {

	@Parameters(index = "0", arity = "1", paramLabel = "WORKSPACE_DIR", description = "Input workspace directory")
	private Path workspaceDir;

	@Parameters(index = "1", arity = "1", paramLabel = "SEED_DATA_DIR", description = "Output seed data models directory")
	private Path seedDataDir;

	@CommandLine.Option(names = { "-c", "--converters" }, arity = "1", paramLabel = "CONVERTERS_JAR",
		description = "Path to a flat converter fat-jar (shadow/uber or plain jar) bundling WorkspaceConverters "
			+ "and their dependencies. Repeatable; each jar is loaded in an isolated class loader. "
			+ "Optional: omit when converters are already on the launch classpath (library mode).")
	private List<Path> convertersJars = new ArrayList<>();

	/**
	 * Main method.
	 *
	 * @param args command line arguments.
	 */
	public static void main(String[] args) {
		int exit = new CommandLine(new WcfCli()).execute(args);
		System.exit(exit);
	}

	@Override
	public void run() {
		try {
			// Resolve relative paths against current working directory
			Path cwd = Path.of("").toAbsolutePath().normalize();
			if (workspaceDir != null && !workspaceDir.isAbsolute()) {
				workspaceDir = cwd.resolve(workspaceDir).normalize();
			}
			if (seedDataDir != null && !seedDataDir.isAbsolute()) {
				seedDataDir = cwd.resolve(seedDataDir).normalize();
			}
			convertersJars.replaceAll(jar -> jar.isAbsolute() ? jar : cwd.resolve(jar).normalize());

			if (workspaceDir == null || !Files.isDirectory(workspaceDir)) {
				throw new CommandLine.ParameterException(new CommandLine(this),
					"Input workspace directory does not exist or is not a directory: " + workspaceDir);
			}
			Files.createDirectories(seedDataDir);

			if (convertersJars.isEmpty()) {
				// Library mode: converters are already on the launch classpath and are discovered
				// via component scan. Delegate to the supported core entry point.
				WcfRunner.run(workspaceDir, seedDataDir);
			} else {
				for (Path jar : convertersJars) {
					if (!Files.isRegularFile(jar)) {
						throw new CommandLine.ParameterException(new CommandLine(this),
							"Converter jar does not exist or is not a file: " + jar);
					}
				}
				convert();
			}
			System.out.println("Conversion finished. Output: " + seedDataDir);
		} catch (Exception e) {
			System.err.println("Conversion failed: " + e.getMessage());
			throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to convert workspace", e);
		}
	}

	/**
	 * Boots the shared CLI application context (supplier, consumer and other core beans), loads the
	 * converters from the supplied fat-jars into isolated class loaders / child contexts, and runs the
	 * conversion pipeline with the globally ordered converters.
	 *
	 * @throws Exception if the converter jars cannot be loaded or the pipeline fails
	 */
	private void convert() throws Exception {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(WcfCli.class)) {
			IWorkspaceSupplier supplier = ctx.getBean(IWorkspaceSupplier.class);
			IWorkspaceConsumer consumer = ctx.getBean(IWorkspaceConsumer.class);
			try (ConverterModules modules = ConverterModules.load(convertersJars, WcfCli.class.getClassLoader(), ctx)) {
				WorkspaceConversionService service =
					new DefaultWorkspaceConversionService(modules.converters(), supplier, consumer);
				service.process(workspaceDir, seedDataDir);
			}
		}
	}
}
