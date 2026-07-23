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
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * @since 2005-06-15
 */
public class ChangeFormComponentTypeFix implements SyntheticIntentionAction {
    private final PsiPlainTextFile myFormFile;
    private final String myFieldName;
    private final String myComponentTypeToSet;

    public ChangeFormComponentTypeFix(PsiPlainTextFile formFile, String fieldName, PsiType componentTypeToSet) {
        myFormFile = formFile;
        myFieldName = fieldName;
        if (componentTypeToSet instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                myComponentTypeToSet = ClassUtil.getJVMClassName(psiClass);
            }
            else {
                myComponentTypeToSet = classType.rawType().getCanonicalText();
            }
        }
        else {
            myComponentTypeToSet = componentTypeToSet.getCanonicalText();
        }
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.uidesignerChangeGuiComponentType();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        CommandProcessor.getInstance().newCommand()
            .project(file.getProject())
            .name(getText())
            .run(() -> {
                ReadonlyStatusHandler readOnlyHandler = ReadonlyStatusHandler.getInstance(myFormFile.getProject());
                ReadonlyStatusHandler.OperationStatus status = readOnlyHandler.ensureFilesWritable(myFormFile.getVirtualFile());
                if (!status.hasReadonlyFiles()) {
                    FormReferenceProvider.setGUIComponentType(myFormFile, myFieldName, myComponentTypeToSet);
                }
            });
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
