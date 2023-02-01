/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8wrappers.utils.DepsFileWriter;
import com.android.tools.r8wrappers.utils.WrapperDiagnosticsHandler;
import com.android.tools.r8wrappers.utils.WrapperFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class D8Wrapper {

  private static final String WRAPPER_STRING = "d8-aosp-wrapper";

  private static final Origin CLI_ORIGIN =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return WRAPPER_STRING;
        }
      };

  private static List<ParseFlagInfo> getAdditionalFlagsInfo() {
    return Arrays.asList(
        new WrapperFlag("--info", "Print the info-level log messages from the compiler."));
  }

  private static String getUsageMessage() {
    StringBuilder builder =
        appendLines(
            new StringBuilder(),
            "Usage: d8 [options] [@<argfile>] <input-files>",
            " where <input-files> are any combination of dex, class, zip, jar or apk files",
            " and each <argfile> is a file containing additional arguments (one per line)",
            " and options are:");
    new ParseFlagPrinter()
        .addFlags(D8Command.getParseFlagsInformation())
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
    D8Wrapper wrapper = new D8Wrapper();
    String[] remainingArgs = wrapper.parseWrapperArguments(args);
    D8Command.Builder builder = D8Command.parse(
        remainingArgs, CLI_ORIGIN, wrapper.diagnosticsHandler);
    if (builder.isPrintHelp()) {
      System.out.println(getUsageMessage());
      return;
    }
    if (builder.isPrintVersion()) {
      System.out.println("D8(" + WRAPPER_STRING + ") " + Version.getVersionString());
      return;
    }
    wrapper.applyWrapperArguments(builder);
    // TODO(b/232073181): Remove this once platform flag is the default.
    if (!builder.getAndroidPlatformBuild()) {
      System.setProperty("com.android.tools.r8.disableApiModeling", "1");
    }
    D8.run(builder.build());
  }

  private WrapperDiagnosticsHandler diagnosticsHandler = new WrapperDiagnosticsHandler();
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
        default:
          {
            remainingArgs.add(arg);
            break;
          }
      }
    }
    return remainingArgs.toArray(new String[0]);
  }

  private void applyWrapperArguments(D8Command.Builder builder) {
    diagnosticsHandler.setPrintInfoDiagnostics(printInfoDiagnostics);
    // TODO(b/249230932): Remove once the correct use of platform flag is in place.
    if (builder.getMinApiLevel() == 10000) {
      builder.setAndroidPlatformBuild(true);
    }
  }
}
