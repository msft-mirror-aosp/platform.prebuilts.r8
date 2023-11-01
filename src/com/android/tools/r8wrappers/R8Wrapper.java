/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.r8wrappers;

import com.android.tools.r8.ArchiveProtoAndroidResourceConsumer;
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider;
import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8wrappers.utils.DepsFileWriter;
import com.android.tools.r8wrappers.utils.WrapperDiagnosticsHandler;
import com.android.tools.r8wrappers.utils.WrapperFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class R8Wrapper {

  private static final String WRAPPER_STRING = "r8-aosp-wrapper";

  private static final Origin CLI_ORIGIN =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return WRAPPER_STRING;
        }
      };

  private static List<ParseFlagInfo> getAdditionalFlagsInfo() {
    return Arrays.asList(
        new WrapperFlag("--deps-file <file>", "Write input dependencies to <file>."),
        new WrapperFlag("--info", "Print the info-level log messages from the compiler."),
        new WrapperFlag("--resource-input", "Resource input for the resource shrinker."),
        new WrapperFlag("--resource-output", "Resource shrinker output."));
  }

  private static String getUsageMessage() {
    StringBuilder builder =
        appendLines(
            new StringBuilder(),
            "Usage: r8 [options] [@<argfile>] <input-files>",
            " where <input-files> are any combination of class, zip, or jar files",
            " and each <argfile> is a file containing additional arguments (one per line)",
            " and options are:");
    new ParseFlagPrinter()
        .addFlags(R8Command.getParseFlagsInformation())
        .addFlags(getAdditionalFlagsInfo())
        .setIndent(2)
        .appendLinesToBuilder(builder);
    return builder.toString();
  }

  private static StringBuilder appendLines(StringBuilder builder, String... lines) {
    for (String line : lines) {
      builder.append(line).append(System.lineSeparator());
    }
    return builder;
  }

  public static void main(String[] args) throws CompilationFailedException {
    // Disable this optimization as it can impact weak reference semantics. See b/233432839.
    System.setProperty("com.android.tools.r8.disableEnqueuerDeferredTracing", "1");
    // Disable class merging across different files to improve attribution. See b/242881914.
    System.setProperty("com.android.tools.r8.enableSameFilePolicy", "1");
    // Enable experimental -whyareyounotinlining config to aid debugging. See b/277389461.
    System.setProperty("com.android.tools.r8.experimental.enablewhyareyounotinlining", "1");

    R8Wrapper wrapper = new R8Wrapper();
    String[] remainingArgs = wrapper.parseWrapperArguments(args);
    R8Command.Builder builder = R8Command.parse(
        remainingArgs, CLI_ORIGIN, wrapper.diagnosticsHandler);
    if (builder.isPrintHelp()) {
      System.out.println(getUsageMessage());
      return;
    }
    if (builder.isPrintVersion()) {
      System.out.println("R8(" + WRAPPER_STRING + ") " + Version.getVersionString());
      return;
    }
    wrapper.applyWrapperArguments(builder);
    applyCommonCompilerArguments(builder);
    builder.setEnableExperimentalKeepAnnotations(true);
    R8.run(builder.build());
  }

  private WrapperDiagnosticsHandler diagnosticsHandler = new WrapperDiagnosticsHandler();
  private boolean useCompatPg = false;
  private Path depsOutput = null;
  private Path resourceInput = null;
  private Path resourceOutput = null;
  private final List<String> pgRules = new ArrayList<>();
  private boolean printInfoDiagnostics = false;

  private String[] parseWrapperArguments(String[] args) {
    List<String> remainingArgs = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--info":
          {
            printInfoDiagnostics = true;
            break;
          }
        case "--resource-input":
          {
            if (resourceInput != null) {
              throw new RuntimeException("Only one --resource-input flag accepted");
            }
            String nextArg = args[++i];
            resourceInput = Paths.get(nextArg);
            break;
          }
        case "--resource-output":
          {
            if (resourceOutput != null) {
              throw new RuntimeException("Only one --resource-output flag accepted");
            }
            String nextArg = args[++i];
            resourceOutput = Paths.get(nextArg);
            break;
          }
        case "--deps-file":
          {
            String nextArg = args[++i];
            depsOutput = Paths.get(nextArg);
            break;
          }
          // Remove uses of this same as for D8 (b/69377755).
        case "--multi-dex":
          {
            break;
          }
          // TODO(zerny): replace uses with --pg-compat
        case "--force-proguard-compatibility":
          {
            useCompatPg = true;
            break;
          }
          // Zero argument PG rules.
        case "-dontshrink":
        case "-dontoptimize":
        case "-dontobfuscate":
        case "-ignorewarnings":
          {
            pgRules.add(arg);
            break;
          }
          // One argument PG rules.
        case "-injars":
        case "-libraryjars":
        case "-include":
        case "-printmapping":
        case "-printconfiguration":
        case "-printusage":
        case "-printseeds":
          {
            pgRules.add(arg + " " + args[++i]);
            break;
          }
        default:
          {
            remainingArgs.add(arg);
            break;
          }
      }
    }
    return remainingArgs.toArray(new String[0]);
  }

  private void applyWrapperArguments(R8Command.Builder builder) {
    diagnosticsHandler.setPrintInfoDiagnostics(printInfoDiagnostics);
    if (depsOutput != null) {
      Path codeOutput = builder.getOutputPath();
      Path target = Files.isDirectory(codeOutput) ? codeOutput.resolve("classes.dex") : codeOutput;
      builder.setInputDependencyGraphConsumer(new DepsFileWriter(target, depsOutput.toString()));
    }
    if (resourceInput != null && resourceOutput != null) {
      builder.setAndroidResourceProvider(new ArchiveProtoAndroidResourceProvider(resourceInput,
          new PathOrigin(resourceInput)));
      builder.setAndroidResourceConsumer(new ArchiveProtoAndroidResourceConsumer(resourceOutput));
    } else if (resourceOutput != null || resourceInput != null) {
      throw new RuntimeException("Both --resource-input and --resource-output must be specified");
    }

    if (!pgRules.isEmpty()) {
      builder.addProguardConfiguration(pgRules, CLI_ORIGIN);
    }
    if (useCompatPg) {
      builder.setProguardCompatibility(useCompatPg);
    }
  }

  /** Utility method to apply platform specific settings to both D8 and R8. */
  public static void applyCommonCompilerArguments(BaseCompilerCommand.Builder<?, ?> builder) {
    // TODO(b/232073181): Remove this once platform flag is the default.
    if (!builder.getAndroidPlatformBuild()) {
      System.setProperty("com.android.tools.r8.disableApiModeling", "1");
    }
  }
}
