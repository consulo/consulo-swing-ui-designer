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

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.*;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.shared.BorderType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.awt.JBUI;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class FormSourceCodeGenerator {
    private static final Logger LOG = Logger.getInstance(FormSourceCodeGenerator.class);

    private StringBuffer myBuffer;
    private Stack<Boolean> myIsFirstParameterStack;
    private final Project myProject;
    private final GuiDesignerConfiguration myConfiguration;
    private final ArrayList<FormErrorInfo> myErrors;
    private boolean myNeedLoadLabelText;
    private boolean myNeedLoadButtonText;

    private static final Map<Class, LayoutSourceGenerator> ourComponentLayoutCodeGenerators = new HashMap<>();
    private static final Map<String, LayoutSourceGenerator> ourContainerLayoutCodeGenerators = new HashMap<>();
    private static final IntObjectMap<String> ourFontStyleMap = IntMaps.newIntObjectHashMap();
    private static final IntObjectMap<String> ourTitleJustificationMap = IntMaps.newIntObjectHashMap();
    private static final IntObjectMap<String> ourTitlePositionMap = IntMaps.newIntObjectHashMap();

    private static final ElementPattern ourSuperCallPattern = PsiJavaPatterns.psiExpressionStatement().withFirstChild(
        PlatformPatterns.psiElement(PsiMethodCallExpression.class)
            .withFirstChild(PlatformPatterns.psiElement().withText(PsiKeyword.SUPER))
    );

    static {
        ourComponentLayoutCodeGenerators.put(LwSplitPane.class, new SplitPaneLayoutSourceGenerator());
        ourComponentLayoutCodeGenerators.put(LwTabbedPane.class, new TabbedPaneLayoutSourceGenerator());
        ourComponentLayoutCodeGenerators.put(LwScrollPane.class, new ScrollPaneLayoutSourceGenerator());
        ourComponentLayoutCodeGenerators.put(LwToolBar.class, new ToolBarLayoutSourceGenerator());

        ourFontStyleMap.put(Font.PLAIN, "java.awt.Font.PLAIN");
        ourFontStyleMap.put(Font.BOLD, "java.awt.Font.BOLD");
        ourFontStyleMap.put(Font.ITALIC, "java.awt.Font.ITALIC");
        ourFontStyleMap.put(Font.BOLD | Font.ITALIC, "java.awt.Font.BOLD | java.awt.Font.ITALIC");

        ourTitlePositionMap.put(0, "javax.swing.border.TitledBorder.DEFAULT_POSITION");
        ourTitlePositionMap.put(1, "javax.swing.border.TitledBorder.ABOVE_TOP");
        ourTitlePositionMap.put(2, "javax.swing.border.TitledBorder.TOP");
        ourTitlePositionMap.put(3, "javax.swing.border.TitledBorder.BELOW_TOP");
        ourTitlePositionMap.put(4, "javax.swing.border.TitledBorder.ABOVE_BOTTOM");
        ourTitlePositionMap.put(5, "javax.swing.border.TitledBorder.BOTTOM");
        ourTitlePositionMap.put(6, "javax.swing.border.TitledBorder.BELOW_BOTTOM");

        ourTitleJustificationMap.put(0, "javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION");
        ourTitleJustificationMap.put(1, "javax.swing.border.TitledBorder.LEFT");
        ourTitleJustificationMap.put(2, "javax.swing.border.TitledBorder.CENTER");
        ourTitleJustificationMap.put(3, "javax.swing.border.TitledBorder.RIGHT");
        ourTitleJustificationMap.put(4, "javax.swing.border.TitledBorder.LEADING");
        ourTitleJustificationMap.put(5, "javax.swing.border.TitledBorder.TRAILING");
    }

    public FormSourceCodeGenerator(@Nonnull Project project) {
        myProject = project;
        myErrors = new ArrayList<>();
        myConfiguration = GuiDesignerConfiguration.getInstance(project);
    }

    @RequiredWriteAction
    public void generate(VirtualFile formFile) {
        myNeedLoadLabelText = false;
        myNeedLoadButtonText = false;

        consulo.module.Module module = ModuleUtilCore.findModuleForFile(formFile, myProject);
        if (module == null) {
            return;
        }

        // ensure that new instances of generators are used for every run
        ourContainerLayoutCodeGenerators.clear();
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_INTELLIJ, new GridLayoutSourceGenerator());
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_GRIDBAG, new GridBagLayoutSourceGenerator());
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_BORDER, new BorderLayoutSourceGenerator());
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_FLOW, new FlowLayoutSourceGenerator());
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_CARD, new CardLayoutSourceGenerator());
        ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_FORM, new FormLayoutSourceGenerator());
        myErrors.clear();

        PsiPropertiesProvider propertiesProvider = new PsiPropertiesProvider(module);

        Document doc = FileDocumentManager.getInstance().getDocument(formFile);
        LwRootContainer rootContainer;
        try {
            rootContainer = Utils.getRootContainer(doc.getText(), propertiesProvider);
        }
        catch (AlienFormFileException ignored) {
            // ignoring this file
            return;
        }
        catch (Exception e) {
            myErrors.add(new FormErrorInfo(null, UIDesignerLocalize.errorCannotProcessFormFile(e).get()));
            return;
        }

        if (rootContainer.getClassToBind() == null) {
            // form skipped - no class to bind
            return;
        }

        ErrorAnalyzer.analyzeErrors(module, formFile, null, rootContainer, null);
        FormEditingUtil.iterate(
            rootContainer,
            iComponent -> {
                ErrorInfo errorInfo = ErrorAnalyzer.getErrorForComponent(iComponent);
                if (errorInfo != null) {
                    String message;
                    if (iComponent.getBinding() != null) {
                        message = UIDesignerLocalize.errorForComponent(iComponent.getBinding(), errorInfo.myDescription).get();
                    }
                    else {
                        message = errorInfo.myDescription;
                    }
                    myErrors.add(new FormErrorInfo(iComponent.getId(), message));
                }
                return true;
            }
        );

        if (myErrors.size() != 0) {
            return;
        }

        try {
            _generate(rootContainer, module);
        }
        catch (ClassToBindNotFoundException e) {
            // ignore
        }
        catch (CodeGenerationException e) {
            myErrors.add(new FormErrorInfo(e.getComponentId(), e.getMessage()));
        }
        catch (IncorrectOperationException e) {
            myErrors.add(new FormErrorInfo(null, e.getMessage()));
        }
    }

    public ArrayList<FormErrorInfo> getErrors() {
        return myErrors;
    }

    @RequiredWriteAction
    private void _generate(LwRootContainer rootContainer, consulo.module.Module module)
        throws CodeGenerationException, IncorrectOperationException {
        myBuffer = new StringBuffer();
        myIsFirstParameterStack = new Stack<>();

        Map<LwComponent, String> component2variable = new HashMap<>();
        ObjectIntMap<String> class2variableIndex = ObjectMaps.newObjectIntHashMap();
        Map<String, LwComponent> id2component = new HashMap<>();

        if (rootContainer.getComponentCount() != 1) {
            throw new CodeGenerationException(null, UIDesignerLocalize.errorOneToplevelComponentRequired().get());
        }
        LwComponent topComponent = (LwComponent) rootContainer.getComponent(0);
        String id = Utils.findNotEmptyPanelWithXYLayout(topComponent);
        if (id != null) {
            throw new CodeGenerationException(id, UIDesignerLocalize.errorNonemptyXyPanelsFound().get());
        }

        PsiClass classToBind = FormEditingUtil.findClassToBind(module, rootContainer.getClassToBind());
        if (classToBind == null) {
            throw new ClassToBindNotFoundException(UIDesignerLocalize.errorClassToBindNotFound(rootContainer.getClassToBind()).get());
        }

        boolean haveCustomCreateComponents = Utils.getCustomCreateComponentCount(rootContainer) > 0;
        if (haveCustomCreateComponents) {
            if (FormEditingUtil.findCreateComponentsMethod(classToBind) == null) {
                throw new CodeGenerationException(null, UIDesignerLocalize.errorNoCustomCreateMethod().get());
            }
            myBuffer.append(AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME).append("();");
        }

        generateSetupCodeForComponent(topComponent, component2variable, class2variableIndex, id2component, module, classToBind);
        generateComponentReferenceProperties(topComponent, component2variable, class2variableIndex, id2component, classToBind);
        generateButtonGroups(rootContainer, component2variable, class2variableIndex, id2component, classToBind);

        String methodText = myBuffer.toString();

        PsiManager psiManager = PsiManager.getInstance(module.getProject());
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();

        PsiClass newClass = (PsiClass) classToBind.copy();

        cleanup(newClass);

        // [anton] the comments are written according to the SCR 26896
        PsiClass fakeClass = elementFactory.createClassFromText(
            "{\n" +
                "// GUI initializer generated by " + module.getApplication().getName() + " GUI " +
                "Designer\n" +
                "// >>> IMPORTANT!! <<<\n" +
                "// DO NOT EDIT OR ADD ANY CODE HERE!\n" +
                "" + AsmCodeGenerator.SETUP_METHOD_NAME + "();\n" +
                "}\n" +
                "\n" +
                "/** Method generated by " + module.getApplication().getName() + " GUI " +
                "Designer\n" +
                " * >>> IMPORTANT!! <<<\n" +
                " * DO NOT edit this method OR call it in your code!\n" +
                " * @noinspection ALL\n" +
                " */\n" +
                "private void " + AsmCodeGenerator.SETUP_METHOD_NAME + "()\n" +
                "{\n" +
                methodText +
                "}\n",
            null
        );

        CodeStyleManager formatter = CodeStyleManager.getInstance(module.getProject());
        JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(module.getProject());

        PsiMethod method = (PsiMethod) newClass.add(fakeClass.getMethods()[0]);

        // don't generate initializer block if $$$setupUI$$$() is called explicitly from one of the constructors
        boolean needInitializer = true;
        boolean needSetupUI = false;
        for (PsiMethod constructor : newClass.getConstructors()) {
            if (containsMethodIdentifier(constructor, method)) {
                needInitializer = false;
            }
            else if (haveCustomCreateComponents && hasCustomComponentAffectingReferences(constructor, newClass, rootContainer, null)) {
                needInitializer = false;
                needSetupUI = true;
            }
        }

        if (needSetupUI) {
            for (PsiMethod constructor : newClass.getConstructors()) {
                addSetupUICall(constructor, rootContainer, method);
            }
        }

        if (needInitializer) {
            newClass.addBefore(fakeClass.getInitializers()[0], method);
        }

        String grcMethodText = "/** @noinspection ALL */ public javax.swing.JComponent " +
            AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME +
            "() { return " + topComponent.getBinding() + "; }";
        generateMethodIfRequired(newClass, method, AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME, grcMethodText,
            topComponent.getBinding() != null
        );

        String loadButtonTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, AbstractButton.class, module);
        generateMethodIfRequired(
            newClass,
            method,
            AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD,
            loadButtonTextMethodText,
            myNeedLoadButtonText
        );
        String loadLabelTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, JLabel.class, module);
        generateMethodIfRequired(newClass, method, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, loadLabelTextMethodText, myNeedLoadLabelText);

        newClass = (PsiClass) styler.shortenClassReferences(newClass);
        newClass = (PsiClass) formatter.reformat(newClass);

        if (!lexemsEqual(classToBind, newClass)) {
            classToBind.replace(newClass);
        }
    }

    @RequiredWriteAction
    private static void addSetupUICall(PsiMethod constructor, LwRootContainer rootContainer, PsiMethod setupUIMethod) {
        PsiCodeBlock psiCodeBlock = constructor.getBody();
        if (psiCodeBlock == null) {
            return;
        }
        PsiClass classToBind = constructor.getContainingClass();
        PsiStatement[] statements = psiCodeBlock.getStatements();
        PsiElement anchor = psiCodeBlock.getRBrace();
        SimpleReference<Boolean> callsThisConstructor = SimpleReference.create(Boolean.FALSE);
        for (PsiStatement statement : statements) {
            if (containsMethodIdentifier(statement, setupUIMethod)) {
                return;
            }
            if (!ourSuperCallPattern.accepts(statement) && hasCustomComponentAffectingReferences(statement,
                classToBind, rootContainer, callsThisConstructor
            )) {
                anchor = statement;
                break;
            }
        }
        if (!callsThisConstructor.get()) {
            PsiElementFactory factory = JavaPsiFacade.getInstance(constructor.getProject()).getElementFactory();
            try {
                PsiStatement setupUIStatement = factory.createStatementFromText(AsmCodeGenerator.SETUP_METHOD_NAME + "();", constructor);
                psiCodeBlock.addBefore(setupUIStatement, anchor);
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
    }

    private static boolean hasCustomComponentAffectingReferences(
        PsiElement element,
        final PsiClass classToBind,
        final LwRootContainer rootContainer,
        @Nullable final SimpleReference<Boolean> callsThisConstructor
    ) {
        final SimpleReference<Boolean> result = SimpleReference.create(Boolean.FALSE);
        element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceElement(expression);
                PsiElement psiElement = expression.resolve();
                if (psiElement == null) {
                    return;
                }
                if (psiElement instanceof PsiField field) {
                    if (field.getContainingClass().equals(classToBind) && Utils.isBoundField(rootContainer, field.getName())) {
                        result.set(Boolean.TRUE);
                    }
                }
                else if (psiElement instanceof PsiMethod) {
                    PsiMethod method = (PsiMethod) psiElement;
                    if (method.isConstructor()) {
                        if (method.getContainingClass() == classToBind) {
                            if (callsThisConstructor != null) {
                                callsThisConstructor.set(Boolean.TRUE);
                            }
                        }
                        else if (method.getContainingClass() != classToBind.getSuperClass()) {
                            result.set(Boolean.TRUE);
                        }
                    }
                    else {
                        result.set(Boolean.TRUE);
                    }
                }
            }
        });

        return result.get();
    }

    @RequiredReadAction
    private static boolean lexemsEqual(PsiClass classToBind, PsiClass newClass) {
        JavaLexer oldTextLexer = new JavaLexer(LanguageLevel.HIGHEST);
        JavaLexer newTextLexer = new JavaLexer(LanguageLevel.HIGHEST);
        String oldBuffer = classToBind.getText();
        String newBuffer = newClass.getText();
        oldTextLexer.start(oldBuffer);
        newTextLexer.start(newBuffer);

        while (true) {
            IElementType oldLexem = oldTextLexer.getTokenType();
            IElementType newLexem = newTextLexer.getTokenType();
            if (oldLexem == null || newLexem == null) {
                // must terminate at the same time
                return oldLexem == null && newLexem == null;
            }
            if (oldLexem != newLexem) {
                return false;
            }
            if (oldLexem != TokenType.WHITE_SPACE && oldLexem != JavaDocElementType.DOC_COMMENT) {
                int oldStart = oldTextLexer.getTokenStart();
                int newStart = newTextLexer.getTokenStart();
                int oldLength = oldTextLexer.getTokenEnd() - oldTextLexer.getTokenStart();
                int newLength = newTextLexer.getTokenEnd() - newTextLexer.getTokenStart();
                if (oldLength != newLength) {
                    return false;
                }
                for (int i = 0; i < oldLength; i++) {
                    if (oldBuffer.charAt(oldStart + i) != newBuffer.charAt(newStart + i)) {
                        return false;
                    }
                }
            }
            oldTextLexer.advance();
            newTextLexer.advance();
        }
    }

    private String getLoadMethodText(String methodName, Class componentClass, consulo.module.Module module) {
        boolean needIndex = haveSetDisplayedMnemonic(componentClass, module);
        return "/** @noinspection ALL */ " +
            "private void " + methodName + "(" + componentClass.getName() + " component, " +
            "java.lang.String text) {" +
            "  StringBuffer result = new StringBuffer(); " +
            "  boolean haveMnemonic = false;    " +
            "  char mnemonic = '\\0';" +
            (needIndex ? "int mnemonicIndex = -1;" : "") +
            "  for(int i=0; i<text.length(); i++) {" +
            "    if (text.charAt(i) == '&') {" +
            "      i++;" +
            "      if (i == text.length()) break;" +
            "      if (!haveMnemonic && text.charAt(i) != '&') {" +
            "        haveMnemonic = true;" +
            "        mnemonic = text.charAt(i);" +
            (needIndex ? "mnemonicIndex = result.length();" : "") +
            "      }" +
            "    }" +
            "    result.append(text.charAt(i));" +
            "  }" +
            "  component.setText(result.toString()); " +
            "  if (haveMnemonic) {" +
            (componentClass.equals(AbstractButton.class) ? "        component.setMnemonic(mnemonic);" : "        " +
                "component.setDisplayedMnemonic(mnemonic);") +
            (needIndex ? "component.setDisplayedMnemonicIndex(mnemonicIndex);" : "") +
            "} }";
    }

    @RequiredWriteAction
    private void generateMethodIfRequired(
        PsiClass aClass,
        PsiMethod anchor,
        String methodName,
        String methodText,
        boolean condition
    ) throws IncorrectOperationException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
        PsiMethod newMethod;
        PsiMethod[] oldMethods = aClass.findMethodsByName(methodName, false);
        if (!condition) {
            for (PsiMethod oldMethod : oldMethods) {
                oldMethod.delete();
            }
        }
        else {
            newMethod = elementFactory.createMethodFromText(methodText, aClass);
            if (oldMethods.length > 0) {
                newMethod = (PsiMethod) oldMethods[0].replace(newMethod);
            }
            else {
                newMethod = (PsiMethod) aClass.addAfter(newMethod, anchor);
            }
        }
    }

    @RequiredWriteAction
    public static void cleanup(PsiClass aClass) throws IncorrectOperationException {
        PsiMethod[] methods = aClass.findMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, false);
        for (PsiMethod method : methods) {
            PsiClassInitializer[] initializers = aClass.getInitializers();
            for (PsiClassInitializer initializer : initializers) {
                if (containsMethodIdentifier(initializer, method)) {
                    initializer.delete();
                }
            }

            method.delete();
        }

        deleteMethods(aClass, AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME);
        deleteMethods(aClass, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD);
        deleteMethods(aClass, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD);
    }

    @RequiredWriteAction
    private static void deleteMethods(PsiClass aClass, String methodName) throws IncorrectOperationException {
        PsiMethod[] grcMethods = aClass.findMethodsByName(methodName, false);
        for (PsiMethod grcMethod : grcMethods) {
            grcMethod.delete();
        }
    }

    @RequiredReadAction
    private static boolean containsMethodIdentifier(PsiElement element, PsiMethod setupMethod) {
        if (element instanceof PsiMethodCallExpression methodCall) {
            PsiMethod psiMethod = methodCall.resolveMethod();
            if (setupMethod.equals(psiMethod)) {
                return true;
            }
        }
        PsiElement[] children = element.getChildren();
        for (int i = children.length - 1; i >= 0; i--) {
            if (containsMethodIdentifier(children[i], setupMethod)) {
                return true;
            }
        }
        return false;
    }


    private void generateSetupCodeForComponent(
        LwComponent component,
        Map<LwComponent, String> component2TempVariable,
        ObjectIntMap<String> class2variableIndex,
        Map<String, LwComponent> id2component,
        consulo.module.Module module,
        PsiClass aClass
    ) throws CodeGenerationException {
        id2component.put(component.getId(), component);
        GlobalSearchScope globalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);

        LwContainer parent = component.getParent();

        String variable = getVariable(component, component2TempVariable, class2variableIndex, aClass);
        String componentClass = component instanceof LwNestedForm nestedForm
            ? getNestedFormClass(module, nestedForm)
            : getComponentLayoutGenerator(component.getParent()).mapComponentClass(component.getComponentClassName());

        if (component.isCustomCreate() && component.getBinding() == null) {
            throw new CodeGenerationException(component.getId(), UIDesignerLocalize.errorCustomCreateNoBinding().get());
        }

        if (!component.isCustomCreate()) {
            String binding = component.getBinding();
            if (binding != null) {
                myBuffer.append(binding);
            }
            else {
                myBuffer.append("final ");
                myBuffer.append(componentClass);
                myBuffer.append(" ");
                myBuffer.append(variable);
            }
            myBuffer.append('=');
            startConstructor(componentClass);
            endConstructor(); // will finish the line
        }

        if (component instanceof LwContainer container) {
            if (!container.isCustomCreate() || container.getComponentCount() > 0) {
                getComponentLayoutGenerator(container).generateContainerLayout(container, this, variable);
            }
        }

        // introspected properties
        LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();

        // see SCR #35990
        Arrays.sort(introspectedProperties, (p1, p2) -> p1.getName().compareTo(p2.getName()));

        for (LwIntrospectedProperty property : introspectedProperties) {
            if (property instanceof LwIntroComponentProperty) {
                // component properties are processed in second pass
                continue;
            }

            Object value = component.getPropertyValue(property);

            //noinspection HardCodedStringLiteral
            boolean isTextWithMnemonicProperty =
                "text".equals(property.getName()) && (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope)
                    || isAssignableFrom(JLabel.class.getName(), componentClass, globalSearchScope));

            // handle resource bundles
            if (property instanceof LwRbIntroStringProperty) {
                StringDescriptor descriptor = (StringDescriptor) value;
                if (descriptor.getValue() == null) {
                    if (isTextWithMnemonicProperty) {
                        if (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope)) {
                            myNeedLoadButtonText = true;
                            startMethodCall("this", AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD);
                        }
                        else {
                            myNeedLoadLabelText = true;
                            startMethodCall("this", AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD);
                        }
                        pushVar(variable);
                    }
                    else {
                        startMethodCall(variable, property.getWriteMethodName());
                    }
                    push(descriptor);
                    endMethod();

                    continue;
                }
                else {
                    value = descriptor.getValue();
                }
            }
            else if (property instanceof LwIntroListModelProperty) {
                generateListModelProperty(property, class2variableIndex, aClass, value, variable);
                continue;
            }

            SupportCode.TextWithMnemonic textWithMnemonic = null;
            if (isTextWithMnemonicProperty) {
                textWithMnemonic = SupportCode.parseText((String) value);
                value = textWithMnemonic.myText;
            }


            String propertyClass = property.getPropertyClassName();
            if (propertyClass.equals(Color.class.getName())) {
                ColorDescriptor descriptor = (ColorDescriptor) value;
                if (!descriptor.isColorSet()) {
                    continue;
                }
            }

            startMethodCall(variable, property.getWriteMethodName());

            if (propertyClass.equals(Dimension.class.getName())) {
                newDimension((Dimension) value);
            }
            else if (propertyClass.equals(Integer.class.getName())) {
                push((Integer) value);
            }
            else if (propertyClass.equals(Double.class.getName())) {
                push((Double) value);
            }
            else if (propertyClass.equals(Float.class.getName())) {
                push((Float) value);
            }
            else if (propertyClass.equals(Long.class.getName())) {
                push((Long) value);
            }
            else if (propertyClass.equals(Short.class.getName())) {
                push((Short) value);
            }
            else if (propertyClass.equals(Byte.class.getName())) {
                push((Byte) value);
            }
            else if (propertyClass.equals(Character.class.getName())) {
                push((Character) value);
            }
            else if (propertyClass.equals(Boolean.class.getName())) {
                push((Boolean) value);
            }
            else if (propertyClass.equals(Rectangle.class.getName())) {
                newRectangle((Rectangle) value);
            }
            else if (propertyClass.equals(Insets.class.getName())) {
                newInsets((Insets) value);
            }
            else if (propertyClass.equals(String.class.getName())) {
                push((String) value);
            }
            else if (propertyClass.equals(Color.class.getName())) {
                pushColor((ColorDescriptor) value);
            }
            else if (propertyClass.equals(Font.class.getName())) {
                pushFont(variable, (FontDescriptor) value, property.getReadMethodName());
            }
            else if (propertyClass.equals(Icon.class.getName())) {
                pushIcon((IconDescriptor) value);
            }
            else if (property instanceof LwIntroEnumProperty) {
                pushVar(propertyClass.replace('$', '.') + "." + value.toString());
            }
            else {
                throw new RuntimeException("unexpected property class: " + propertyClass);
            }

            endMethod();

            // special handling of mnemonics

            if (!isTextWithMnemonicProperty) {
                continue;
            }

            if (textWithMnemonic.myMnemonicIndex == -1) {
                continue;
            }

            if (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope)) {
                generateSetMnemonic(variable, textWithMnemonic, module, "setMnemonic", AbstractButton.class);
            }
            else if (isAssignableFrom(JLabel.class.getName(), componentClass, globalSearchScope)) {
                generateSetMnemonic(variable, textWithMnemonic, module, "setDisplayedMnemonic", JLabel.class);
            }
        }

        generateClientProperties(component, variable);

        // add component to parent
        if (!(component.getParent() instanceof LwRootContainer)) {
            String parentVariable = getVariable(parent, component2TempVariable, class2variableIndex, aClass);
            String componentVar = variable;
            if (component instanceof LwNestedForm) {
                componentVar = variable + "." + AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME + "()";
            }
            getComponentLayoutGenerator(component.getParent()).generateComponentLayout(component, this, componentVar,
                parentVariable
            );
        }

        if (component instanceof LwContainer container) {
            generateBorder(container, variable);

            for (int i = 0; i < container.getComponentCount(); i++) {
                generateSetupCodeForComponent(
                    (LwComponent) container.getComponent(i),
                    component2TempVariable,
                    class2variableIndex,
                    id2component,
                    module,
                    aClass
                );
            }
        }
    }

    private void generateSetMnemonic(
        String variable,
        SupportCode.TextWithMnemonic textWithMnemonic,
        consulo.module.Module module,
        String setMethodName,
        Class controlClass
    ) {
        startMethodCall(variable, setMethodName);
        pushVar("'" + textWithMnemonic.getMnemonicChar() + "'");
        endMethod();

        if (haveSetDisplayedMnemonic(controlClass, module)) {
            // generated code needs to be compatible with jdk 1.3
            startMethodCall(variable, "setDisplayedMnemonicIndex");
            push(textWithMnemonic.myMnemonicIndex);
            endMethod();
        }
    }

    private boolean haveSetDisplayedMnemonic(Class controlClass, Module module) {
        PsiClass aClass =
            JavaPsiFacade.getInstance(myProject).findClass(controlClass.getName(), GlobalSearchScope.moduleWithLibrariesScope(module));
        return aClass != null && aClass.findMethodsByName("setDisplayedMnemonicIndex", true).length > 0;
    }

    private void generateListModelProperty(
        LwIntrospectedProperty property,
        ObjectIntMap<String> class2variableIndex,
        PsiClass aClass,
        Object value,
        String variable
    ) {
        String valueClassName;
        if (property.getPropertyClassName().equals(ComboBoxModel.class.getName())) {
            valueClassName = DefaultComboBoxModel.class.getName();
        }
        else {
            valueClassName = DefaultListModel.class.getName();
        }
        String modelVarName = generateUniqueVariableName(valueClassName, class2variableIndex, aClass);
        myBuffer.append("final ");
        myBuffer.append(valueClassName);
        myBuffer.append(" ");
        myBuffer.append(modelVarName);
        myBuffer.append("= new ").append(valueClassName).append("();");
        String[] items = (String[]) value;
        for (String item : items) {
            startMethodCall(modelVarName, "addElement");
            push(item);
            endMethod();
        }

        startMethodCall(variable, property.getWriteMethodName());
        pushVar(modelVarName);
        endMethod();
    }

    private void generateBorder(LwContainer container, String variable) {
        BorderType borderType = container.getBorderType();
        StringDescriptor borderTitle = container.getBorderTitle();
        Insets borderSize = container.getBorderSize();
        String borderFactoryMethodName = borderType.getBorderFactoryMethodName();

        boolean borderNone = borderType.equals(BorderType.NONE);
        if (!borderNone || borderTitle != null) {
            startMethodCall(variable, "setBorder");

            startStaticMethodCall(BorderFactory.class, "createTitledBorder");

            if (!borderNone) {
                startStaticMethodCall(BorderFactory.class, borderFactoryMethodName);
                if (borderType.equals(BorderType.LINE)) {
                    if (container.getBorderColor() == null) {
                        pushVar("java.awt.Color.black");
                    }
                    else {
                        pushColor(container.getBorderColor());
                    }
                }
                else if (borderType.equals(BorderType.EMPTY) && borderSize != null) {
                    push(borderSize.top);
                    push(borderSize.left);
                    push(borderSize.bottom);
                    push(borderSize.right);
                }
                endMethod();
            }
            else if (isCustomBorder(container)) {
                push((String) null);
            }

            push(borderTitle);

            if (isCustomBorder(container)) {
                push(container.getBorderTitleJustification(), ourTitleJustificationMap);
                push(container.getBorderTitlePosition(), ourTitlePositionMap);
                if (container.getBorderTitleFont() != null || container.getBorderTitleColor() != null) {
                    if (container.getBorderTitleFont() == null) {
                        push((String) null);
                    }
                    else {
                        pushFont(variable, container.getBorderTitleFont(), "getFont");
                    }
                    if (container.getBorderTitleColor() != null) {
                        pushColor(container.getBorderTitleColor());
                    }
                }
            }

            endMethod(); // createTitledBorder

            endMethod(); // setBorder
        }
    }

    private static boolean isCustomBorder(LwContainer container) {
        return container.getBorderTitleJustification() != 0 || container.getBorderTitlePosition() != 0 ||
            container.getBorderTitleColor() != null || container.getBorderTitleFont() != null;
    }

    private void generateClientProperties(LwComponent component, String variable) throws CodeGenerationException {
        Map props = component.getDelegeeClientProperties();
        for (Object o : props.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            startMethodCall(variable, "putClientProperty");
            push((String) e.getKey());

            Object value = e.getValue();
            if (value instanceof StringDescriptor) {
                push(((StringDescriptor) value).getValue());
            }
            else if (value instanceof Boolean) {
                if ((Boolean) value) {
                    pushVar("Boolean.TRUE");
                }
                else {
                    pushVar("Boolean.FALSE");
                }
            }
            else {
                startConstructor(value.getClass().getName());
                if (value instanceof Integer intValue) {
                    push(intValue);
                }
                else if (value instanceof Double doubleValue) {
                    push(doubleValue);
                }
                else {
                    throw new CodeGenerationException(component.getId(), "Unknown client property value type");
                }
                endConstructor();
            }

            endMethod();
        }
    }

    private static String getNestedFormClass(Module module, LwNestedForm nestedForm) throws CodeGenerationException {
        LwRootContainer container;
        try {
            container = new PsiNestedFormLoader(module).loadForm(nestedForm.getFormFileName());
            return container.getClassToBind();
        }
        catch (Exception e) {
            throw new CodeGenerationException(nestedForm.getId(), e.getMessage());
        }
    }

    private void generateComponentReferenceProperties(
        LwComponent component,
        Map<LwComponent, String> component2variable,
        ObjectIntMap<String> class2variableIndex,
        Map<String, LwComponent> id2component,
        PsiClass aClass
    ) {
        String variable = getVariable(component, component2variable, class2variableIndex, aClass);
        LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();
        for (LwIntrospectedProperty property : introspectedProperties) {
            if (property instanceof LwIntroComponentProperty) {
                String componentId = (String) component.getPropertyValue(property);
                if (componentId != null && componentId.length() > 0) {
                    LwComponent target = id2component.get(componentId);
                    if (target != null) {
                        String targetVariable = getVariable(target, component2variable, class2variableIndex, aClass);
                        startMethodCall(variable, property.getWriteMethodName());
                        pushVar(targetVariable);
                        endMethod();
                    }
                }
            }
        }

        if (component instanceof LwContainer container) {
            for (int i = 0; i < container.getComponentCount(); i++) {
                generateComponentReferenceProperties(
                    (LwComponent) container.getComponent(i),
                    component2variable,
                    class2variableIndex,
                    id2component,
                    aClass
                );
            }
        }
    }

    private void generateButtonGroups(
        LwRootContainer rootContainer,
        Map<LwComponent, String> component2variable,
        ObjectIntMap<String> class2variableIndex,
        Map<String, LwComponent> id2component,
        PsiClass aClass
    ) {
        IButtonGroup[] groups = rootContainer.getButtonGroups();
        boolean haveGroupDeclaration = false;
        for (IButtonGroup group : groups) {
            boolean haveGroupConstructor = false;
            String[] ids = group.getComponentIds();
            for (String id : ids) {
                LwComponent target = id2component.get(id);
                if (target != null) {
                    if (!haveGroupConstructor) {
                        if (group.isBound()) {
                            append(group.getName());
                        }
                        else {
                            if (!haveGroupDeclaration) {
                                append("javax.swing.ButtonGroup buttonGroup;");
                                haveGroupDeclaration = true;
                            }
                            append("buttonGroup");
                        }
                        append("= new javax.swing.ButtonGroup();");
                        haveGroupConstructor = true;
                    }
                    String targetVariable = getVariable(target, component2variable, class2variableIndex, aClass);
                    startMethodCall(group.isBound() ? group.getName() : "buttonGroup", "add");
                    pushVar(targetVariable);
                    endMethod();
                }
            }
        }
    }

    private static LayoutSourceGenerator getComponentLayoutGenerator(LwContainer container) {
        LayoutSourceGenerator generator = ourComponentLayoutCodeGenerators.get(container.getClass());
        if (generator != null) {
            return generator;
        }
        LwContainer parent = container;
        while (parent != null) {
            String layoutManager = parent.getLayoutManager();
            if (layoutManager != null && layoutManager.length() > 0) {
                generator = ourContainerLayoutCodeGenerators.get(layoutManager);
                if (generator != null) {
                    return generator;
                }
            }
            parent = parent.getParent();
        }
        return GridLayoutSourceGenerator.INSTANCE;
    }

    void push(StringDescriptor descriptor) {
        if (descriptor == null) {
            push((String) null);
        }
        else if (descriptor.getValue() != null) {
            push(descriptor.getValue());
        }
        else {
            startMethodCall("java.util.ResourceBundle.getBundle(\"" + descriptor.getBundleName() + "\")", "getString");
            push(descriptor.getKey());
            endMethod();
        }
    }

    private void pushColor(ColorDescriptor descriptor) {
        if (descriptor.getColor() != null) {
            startConstructor(Color.class.getName());
            push(descriptor.getColor().getRGB());
            endConstructor();
        }
        else if (descriptor.getSwingColor() != null) {
            startStaticMethodCall(UIManager.class, "getColor");
            push(descriptor.getSwingColor());
            endMethod();
        }
        else if (descriptor.getSystemColor() != null) {
            pushVar("java.awt.SystemColor." + descriptor.getSystemColor());
        }
        else if (descriptor.getAWTColor() != null) {
            pushVar("java.awt.Color." + descriptor.getAWTColor());
        }
        else {
            throw new IllegalStateException("Unknown color type");
        }
    }

    private void pushFont(String variable, FontDescriptor fontDescriptor, String getterName) {
        if (fontDescriptor.getSwingFont() != null) {
            startStaticMethodCall(UIManager.class, "getFont");
            push(fontDescriptor.getSwingFont());
            endMethod();
        }
        else {
            startConstructor(Font.class.getName());
            if (fontDescriptor.getFontName() != null) {
                push(fontDescriptor.getFontName());
            }
            else {
                pushVar(variable + "." + getterName + "().getName()");
            }
            if (fontDescriptor.getFontStyle() >= 0) {
                push(fontDescriptor.getFontStyle(), ourFontStyleMap);
            }
            else {
                pushVar(variable + "." + getterName + "().getStyle()");
            }
            if (fontDescriptor.getFontSize() >= 0) {
                push(fontDescriptor.getFontSize());
            }
            else {
                pushVar(variable + "." + getterName + "().getSize()");
            }
            endMethod();
        }
    }

    public void pushIcon(IconDescriptor iconDescriptor) {
        startConstructor(ImageIcon.class.getName());
        startMethodCall("getClass()", "getResource");
        push("/" + iconDescriptor.getIconPath());
        endMethod();
        endMethod();
    }

    private boolean isAssignableFrom(String className, String fromName, GlobalSearchScope scope) {
        PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, scope);
        PsiClass fromClass = JavaPsiFacade.getInstance(myProject).findClass(fromName, scope);
        //noinspection SimplifiableIfStatement
        if (aClass == null || fromClass == null) {
            return false;
        }
        return InheritanceUtil.isInheritorOrSelf(fromClass, aClass, true);
    }

    /**
     * @return variable idx
     */
    private static String getVariable(
        LwComponent component,
        Map<LwComponent, String> component2variable,
        ObjectIntMap<String> class2variableIndex,
        PsiClass aClass
    ) {
        if (component2variable.containsKey(component)) {
            return component2variable.get(component);
        }

        if (component.getBinding() != null) {
            return component.getBinding();
        }

        String className = component instanceof LwNestedForm ? "nestedForm" : component.getComponentClassName();

        String result = generateUniqueVariableName(className, class2variableIndex, aClass);
        component2variable.put(component, result);

        return result;
    }

    private static String generateUniqueVariableName(String className, ObjectIntMap<String> class2variableIndex, PsiClass aClass) {
        String shortName;
        if (className.startsWith("javax.swing.J")) {
            shortName = className.substring("javax.swing.J".length());
        }
        else {
            int idx = className.lastIndexOf('.');
            if (idx != -1) {
                shortName = className.substring(idx + 1);
            }
            else {
                shortName = className;
            }
        }

        if (!class2variableIndex.containsKey(className)) {
            class2variableIndex.putInt(className, 0);
        }
        String result;
        do {
            class2variableIndex.putInt(className, class2variableIndex.getInt(className) + 1);
            int newIndex = class2variableIndex.getInt(className);

            result = Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1) + newIndex;
        }
        while (aClass.findFieldByName(result, true) != null);
        return result;
    }

    void newDimensionOrNull(Dimension dimension) {
        if (dimension.width == -1 && dimension.height == -1) {
            checkParameter();
            myBuffer.append("null");
        }
        else {
            newDimension(dimension);
        }
    }

    void newDimension(Dimension dimension) {
        startConstructor(Dimension.class.getName());
        pushScaling(dimension.width);
        pushScaling(dimension.height);
        endConstructor();
    }

    void newInsets(Insets insets) {
        startConstructor(Insets.class.getName());
        pushScaling(insets.top);
        pushScaling(insets.left);
        pushScaling(insets.bottom);
        pushScaling(insets.right);
        endConstructor();
    }

    private void newRectangle(Rectangle rectangle) {
        startConstructor(Rectangle.class.getName());
        pushScaling(rectangle.x);
        pushScaling(rectangle.y);
        pushScaling(rectangle.width);
        pushScaling(rectangle.height);
        endConstructor();
    }


    void startMethodCall(String variable, String methodName) {
        checkParameter();

        append(variable);
        myBuffer.append('.');
        myBuffer.append(methodName);
        myBuffer.append('(');

        myIsFirstParameterStack.push(Boolean.TRUE);
    }

    private void startStaticMethodCall(Class aClass, String methodName) {
        checkParameter();

        myBuffer.append(aClass.getName());
        myBuffer.append('.');
        myBuffer.append(methodName);
        myBuffer.append('(');

        myIsFirstParameterStack.push(Boolean.TRUE);
    }

    void endMethod() {
        myBuffer.append(')');

        myIsFirstParameterStack.pop();

        if (myIsFirstParameterStack.empty()) {
            myBuffer.append(";\n");
        }
    }

    void startConstructor(String className) {
        checkParameter();

        myBuffer.append("new ");
        myBuffer.append(className);
        myBuffer.append('(');

        myIsFirstParameterStack.push(Boolean.TRUE);
    }

    void endConstructor() {
        endMethod();
    }

    void push(byte value) {
        checkParameter();
        append(value);
    }

    void append(byte value) {
        myBuffer.append("(byte) ");
        myBuffer.append(value);
    }

    void push(short value) {
        checkParameter();
        append(value);
    }

    void append(short value) {
        myBuffer.append("(short) ");
        myBuffer.append(value);
    }

    void push(int value) {
        checkParameter();
        append(value);
    }

    void pushScaling(int value) {
        checkParameter();
        if (value > 0 && myConfiguration.USE_JB_SCALING) {
            append(JBUI.class.getName());
            append(".scale(");
            append(value);
            append(")");
        }
        else {
            append(value);
        }
    }

    void append(int value) {
        myBuffer.append(value);
    }

    void push(int value, IntObjectMap<?> map) {
        String stringRepresentation = (String) map.get(value);
        if (stringRepresentation != null) {
            checkParameter();
            myBuffer.append(stringRepresentation);
        }
        else {
            push(value);
        }
    }

    private void push(double value) {
        checkParameter();
        append(value);
    }

    public void append(double value) {
        myBuffer.append(value);
    }

    private void push(float value) {
        checkParameter();
        append(value);
    }

    public void append(float value) {
        myBuffer.append(value).append("f");
    }

    private void push(long value) {
        checkParameter();
        append(value);
    }

    public void append(long value) {
        myBuffer.append(value).append("L");
    }

    private void push(char value) {
        checkParameter();
        append(value);
    }

    public void append(char value) {
        myBuffer.append("'").append(value).append("'");
    }

    void push(boolean value) {
        checkParameter();
        myBuffer.append(value);
    }

    void push(String value) {
        checkParameter();
        if (value == null) {
            myBuffer.append("null");
        }
        else {
            myBuffer.append('"');
            myBuffer.append(StringUtil.escapeStringCharacters(value));
            myBuffer.append('"');
        }
    }

    void pushVar(String variable) {
        checkParameter();
        append(variable);
    }

    void append(String text) {
        myBuffer.append(text);
    }

    void checkParameter() {
        if (!myIsFirstParameterStack.empty()) {
            Boolean b = myIsFirstParameterStack.pop();
            if (b.equals(Boolean.FALSE)) {
                myBuffer.append(',');
            }
            myIsFirstParameterStack.push(Boolean.FALSE);
        }
    }
}
