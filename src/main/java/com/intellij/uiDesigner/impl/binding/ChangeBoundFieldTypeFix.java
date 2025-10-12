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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * Date: Jun 15, 2005
 */
public class ChangeBoundFieldTypeFix implements SyntheticIntentionAction {
    private final PsiField myField;
    private final PsiType myTypeToSet;

    public ChangeBoundFieldTypeFix(PsiField field, PsiType typeToSet) {
        myField = field;
        myTypeToSet = typeToSet;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.uidesignerChangeBoundFieldType();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        CommandProcessor.getInstance().executeCommand(myField.getProject(), new Runnable() {
            @Override
            public void run() {
                try {
                    final PsiManager manager = myField.getManager();
                    myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myTypeToSet));
                }
                catch (final consulo.language.util.IncorrectOperationException e) {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            Messages.showErrorDialog(myField.getProject(), JavaQuickFixBundle.message("cannot.change.field.exception", myField.getName(), e.getLocalizedMessage()),
                                CommonBundle.getErrorTitle());
                        }
                    });
                }
            }
        }, getText().get(), null);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
