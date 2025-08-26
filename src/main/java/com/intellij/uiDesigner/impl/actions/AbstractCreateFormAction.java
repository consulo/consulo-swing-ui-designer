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

package com.intellij.uiDesigner.impl.actions;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.application.dumb.DumbAware;
import consulo.ide.IdeView;
import consulo.ide.action.CreateElementActionBase;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.image.Image;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author yole
 */
public abstract class AbstractCreateFormAction extends CreateElementActionBase implements DumbAware {
    public AbstractCreateFormAction(LocalizeValue text, LocalizeValue description, Image icon) {
        super(text, description, icon);
    }

    @Override
    public void update(final AnActionEvent e) {
        super.update(e);
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final Presentation presentation = e.getPresentation();
        if (presentation.isEnabled()) {
            final Module module = e.getData(LangDataKeys.MODULE);
            if (module != null && ModuleUtilCore.getExtension(module, JavaModuleExtension.class) != null) {
                final IdeView view = e.getData(IdeView.KEY);
                if (view != null) {
                    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                    final PsiDirectory[] dirs = view.getDirectories();
                    for (final PsiDirectory dir : dirs) {
                        if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && JavaDirectoryService.getInstance().getPackage(dir) != null) {
                            return;
                        }
                    }
                }
            }

            presentation.setEnabled(false);
            presentation.setVisible(false);
        }
    }

    protected String createFormBody(@Nullable final String fullQualifiedClassName, @NonNls final String formName, final String layoutManager)
        throws IncorrectOperationException {

        final InputStream inputStream = getClass().getResourceAsStream(formName);

        final StringBuffer buffer = new StringBuffer();
        try {
            for (int ch; (ch = inputStream.read()) != -1; ) {
                buffer.append((char) ch);
            }
        }
        catch (IOException e) {
            throw new IncorrectOperationException(UIDesignerBundle.message("error.cannot.read", formName), e);
        }

        String s = buffer.toString();

        if (fullQualifiedClassName != null) {
            s = StringUtil.replace(s, "$CLASS$", fullQualifiedClassName);
        }
        else {
            s = StringUtil.replace(s, "bind-to-class=\"$CLASS$\"", "");
        }

        s = StringUtil.replace(s, "$LAYOUT$", layoutManager);

        return StringUtil.convertLineSeparators(s);
    }

    @Override
    protected LocalizeValue getActionName(final PsiDirectory directory, final String newName) {
        return UIDesignerLocalize.progressCreatingClass(JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName);
    }
}
