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
package com.intellij.uiDesigner.impl.quickFixes;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import consulo.application.Application;
import consulo.language.editor.FileModificationService;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.ex.awt.Messages;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;

/**
 * @author Eugene Zhuravlev
 * @since 2005-06-14
 */
public class ChangeFieldTypeFix extends QuickFix {
    private final PsiField myField;
    private final PsiType myNewType;

    public ChangeFieldTypeFix(GuiEditor uiEditor, PsiField field, PsiType uiComponentType) {
        super(uiEditor, getText(field, uiComponentType).get(), null);
        myField = field;
        myNewType = uiComponentType;
    }

    private static LocalizeValue getText(PsiField field, PsiType uiComponentType) {
        return UIDesignerLocalize.actionChangeFieldType(
            StringUtil.notNullize(field.getName()),
            field.getType().getCanonicalText(),
            uiComponentType.getCanonicalText()
        );
    }

    @Override
    public void run() {
        PsiFile psiFile = myField.getContainingFile();
        if (psiFile == null) {
            return;
        }
        if (!FileModificationService.getInstance().preparePsiElementForWrite(psiFile)) {
            return;
        }
        CommandProcessor.getInstance().newCommand()
            .project(myField.getProject())
            .name(LocalizeValue.ofNullable(getName()))
            .inWriteAction()
            .run(() -> {
                try {
                    PsiManager manager = myField.getManager();
                    myField.getTypeElement()
                        .replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myNewType));
                }
                catch (IncorrectOperationException e) {
                    Application.get().invokeLater(() -> Messages.showErrorDialog(
                        myEditor,
                        UIDesignerLocalize.errorCannotChangeFieldType(myField.getName(), e.getMessage()).get(),
                        CommonLocalize.titleError().get()
                    ));
                }
        });
    }
}
