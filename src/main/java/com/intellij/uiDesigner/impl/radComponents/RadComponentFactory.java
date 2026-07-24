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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.07.2006
 * Time: 13:49:57
 */
package com.intellij.uiDesigner.impl.radComponents;

import com.intellij.uiDesigner.impl.LoaderFactory;
import com.intellij.uiDesigner.impl.ModuleProvider;
import com.intellij.uiDesigner.impl.palette.Palette;
import consulo.module.Module;

public abstract class RadComponentFactory {
  public RadComponent newInstance(ModuleProvider moduleProvider, String className, String id) throws ClassNotFoundException {
    Module module = moduleProvider.getModule();
    Class<?> aClass = Class.forName(className, true, LoaderFactory.getInstance(module.getProject()).getLoader(module));
    return newInstance(moduleProvider, aClass, id);
  }

  protected abstract RadComponent newInstance(ModuleProvider moduleProvider, Class aClass, String id);

  public abstract RadComponent newInstance(Class componentClass, String id, Palette palette);
}