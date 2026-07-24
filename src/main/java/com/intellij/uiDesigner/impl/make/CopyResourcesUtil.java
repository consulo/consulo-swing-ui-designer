/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.impl.make;

import consulo.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class CopyResourcesUtil {
  private CopyResourcesUtil() {
  }

  public static File copyClass(String targetPath, @NonNls String className, boolean deleteOnExit) throws IOException{
    File targetDir = new File(targetPath).getAbsoluteFile();
    File file = new File(targetDir, className + ".class");
    FileUtil.createParentDirs(file);
    if (deleteOnExit) {
      for (File f = file; f != null && !FileUtil.filesEqual(f, targetDir); f = FileUtil.getParentFile(f)) {
        f.deleteOnExit();
      }
    }
    @NonNls String resourceName = "/" + className + ".class";
    InputStream stream = CopyResourcesUtil.class.getResourceAsStream(resourceName);
    if (stream == null) {
      throw new IOException("cannot load " + resourceName);
    }
    return copyStreamToFile(stream, file);
  }

  private static File copyStreamToFile(InputStream stream, File file) throws IOException {
    try {
      FileOutputStream outputStream = new FileOutputStream(file);
      try {
        FileUtil.copy(stream, outputStream);
      }
      finally {
        outputStream.close();
      }
    }
    finally {
      stream.close();
    }
    return file;
  }

  public static void copyProperties(String targetPath, String fileName) throws IOException {
    File targetDir = new File(targetPath).getAbsoluteFile();
    File file = new File(targetDir, fileName);
    FileUtil.createParentDirs(file);
    for (File f = file; f != null && !FileUtil.filesEqual(f, targetDir); f = FileUtil.getParentFile(f)) {
      f.deleteOnExit();
    }
    String resourceName = "/" + fileName;
    InputStream stream = CopyResourcesUtil.class.getResourceAsStream(resourceName);
    if (stream == null) {
      return;
    }
    copyStreamToFile(stream, file);
  }

  public static List<File> copyFormsRuntime(String targetDir, boolean deleteOnExit) throws IOException {
    String[] runtimeClasses = {
      "AbstractLayout",
      "DimensionInfo",
      "GridConstraints",
      "GridLayoutManager",
      "HorizontalInfo",
      "LayoutState",
      "Spacer",
      "SupportCode$TextWithMnemonic",
      "SupportCode",
      "Util",
      "VerticalInfo",
    };

    List<File> copied = new ArrayList<>();
    for (String runtimeClass : runtimeClasses) {
      copied.add(copyClass(targetDir, "com/intellij/uiDesigner/core/" + runtimeClass, deleteOnExit));
    }
    return copied;
  }
}
