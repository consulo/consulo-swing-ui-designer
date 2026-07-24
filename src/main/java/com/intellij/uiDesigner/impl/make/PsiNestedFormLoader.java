/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.uiDesigner.impl.PsiPropertiesProvider;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PsiNestedFormLoader implements NestedFormLoader {
  protected consulo.module.Module myModule;
  private final Map<String, LwRootContainer> myFormCache = new HashMap<>();

  public PsiNestedFormLoader(Module module) {
    myModule = module;
  }

  public LwRootContainer loadForm(String formFileName) throws Exception {
    if (myFormCache.containsKey(formFileName)) {
      return myFormCache.get(formFileName);
    }
    VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(myModule, formFileName);
    if (formFile == null) {
      throw new Exception("Could not find nested form file " + formFileName);
    }
    LwRootContainer container = Utils.getRootContainer(formFile.getInputStream(), new PsiPropertiesProvider(myModule));
    myFormCache.put(formFileName, container);
    return container;
  }

  public String getClassToBindName(LwRootContainer container) {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(myModule.getProject()).findClass(container.getClassToBind(), GlobalSearchScope.moduleWithDependenciesScope(myModule));
    if (psiClass != null) {
      return ClassUtil.getJVMClassName(psiClass);
    }

    return container.getClassToBind();
  }
}
