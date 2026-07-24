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

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.application.ApplicationManager;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.function.Computable;
import consulo.document.util.TextRange;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiReferenceProcessor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.language.util.ProcessingContext;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.xml.language.XmlFileType;
import consulo.xml.language.psi.XmlAttribute;
import consulo.xml.language.psi.XmlAttributeValue;
import consulo.xml.language.psi.XmlFile;
import consulo.xml.language.psi.XmlTag;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class FormReferenceProvider extends PsiReferenceProvider
{
  private static class CachedFormData {
    PsiReference[] myReferences;
    Map<String, Pair<PsiType, TextRange>> myFieldNameToTypeMap;

    public CachedFormData(PsiReference[] refs, Map<String, Pair<PsiType, TextRange>> map) {
      myReferences = refs;
      myFieldNameToTypeMap = map;
    }
  }

  private static final Key<CachedValue<CachedFormData>> CACHED_DATA = Key.create("Cached form reference");

  @Nonnull
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
    if (element instanceof PsiPlainTextFile) {
      PsiPlainTextFile plainTextFile = (PsiPlainTextFile) element;
      if (plainTextFile.getFileType().equals(GuiFormFileType.INSTANCE)) {
        return getCachedData(plainTextFile).myReferences;
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Nullable
  public static PsiFile getFormFile(PsiField field) {
    PsiReference ref = getFormReference(field);
    if (ref != null) {
      return ref.getElement().getContainingFile();
    }
    return null;
  }

  @Nullable
  public static PsiReference getFormReference(PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      List<PsiFile> forms = FormClassIndex.findFormsBoundToClass(containingClass);
      for (PsiFile formFile : forms) {
        PsiReference[] refs = formFile.getReferences();
        for (PsiReference ref : refs) {
          if (ref.isReferenceTo(field)) {
            return ref;
          }
        }
      }
    }
    return null;
  }

  public static @Nullable
  PsiType getGUIComponentType(PsiPlainTextFile file, String fieldName) {
    Map<String, Pair<PsiType, TextRange>> fieldNameToTypeMap = getCachedData(file).myFieldNameToTypeMap;
    Pair<PsiType, TextRange> typeRangePair = fieldNameToTypeMap.get(fieldName);
    return typeRangePair != null? typeRangePair.getFirst() : null;
  }

  public static void setGUIComponentType(PsiPlainTextFile file, String fieldName, String typeText) {
    Map<String, Pair<PsiType, TextRange>> fieldNameToTypeMap = getCachedData(file).myFieldNameToTypeMap;
    Pair<PsiType, TextRange> typeRangePair = fieldNameToTypeMap.get(fieldName);
    if (typeRangePair != null) {
      TextRange range = typeRangePair.getSecond();
      if (range != null) {
        PsiDocumentManager.getInstance(file.getProject()).getDocument(file).replaceString(range.getStartOffset(), range.getEndOffset(), typeText);
      }
    }
  }

  private static void processReferences(final PsiPlainTextFile file, final PsiReferenceProcessor processor) {
    final Project project = file.getProject();
    final XmlTag rootTag = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
      public XmlTag compute() {
        XmlFile xmlFile = (XmlFile) PsiFileFactory.getInstance(project).createFileFromText("a.xml", XmlFileType.INSTANCE, file.getText());
        return xmlFile.getRootTag();
      }
    });

    if (rootTag == null || !Utils.FORM_NAMESPACE.equals(rootTag.getNamespace())) {
      return;
    }

    @NonNls String name = rootTag.getName();
    if (!"form".equals(name)){
      return;
    }

    PsiReference classReference = null;

    XmlAttribute classToBind = rootTag.getAttribute("bind-to-class", null);
    if (classToBind != null) {
      // reference to class
      XmlAttributeValue valueElement = classToBind.getValueElement();
      if (valueElement == null) {
        return;
      }
      String className = valueElement.getValue().replace('$','.');
      PsiReference[] referencesByString = new JavaClassReferenceProvider().getReferencesByString(className, file, valueElement.getTextRange().getStartOffset() + 1);
      if(referencesByString.length < 1){
        // There are no references there
        return;
      }
      for (PsiReference aReferencesByString : referencesByString) {
        processor.execute(aReferencesByString);
      }
      classReference = referencesByString[referencesByString.length - 1];
    }

    final PsiReference finalClassReference = classReference;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        processReferences(rootTag, finalClassReference, file, processor);
      }
    });
  }

  private static TextRange getValueRange(XmlAttribute classToBind) {
    XmlAttributeValue valueElement = classToBind.getValueElement();
    TextRange textRange = valueElement.getTextRange();
    return new TextRange(textRange.getStartOffset() + 1, textRange.getEndOffset() - 1); // skip " "
  }

  private static void processReferences(XmlTag tag,
                                        PsiReference classReference,
                                        PsiPlainTextFile file,
                                        PsiReferenceProcessor processor) {
    XmlAttribute clsAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, null);
    String classNameStr = clsAttribute != null? clsAttribute.getValue().replace('$','.') : null;
    // field
    {
      XmlAttribute bindingAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, null);
      if (bindingAttribute != null && classReference != null) {
        XmlAttribute customCreateAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, null);
        boolean customCreate = (customCreateAttribute != null && Boolean.parseBoolean(customCreateAttribute.getValue()));
        TextRange nameRange = clsAttribute != null ? getValueRange(clsAttribute) : null;
        processor.execute(new FieldFormReference(file, classReference, getValueRange(bindingAttribute), classNameStr, nameRange, customCreate));
      }
      XmlAttribute titleBundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE, null);
      XmlAttribute titleKeyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_KEY, null);
      if (titleBundleAttribute != null && titleKeyAttribute != null) {
        processResourceBundleFileReferences(file, processor, titleBundleAttribute);
        processor.execute(new ResourceBundleKeyReference(file, titleBundleAttribute.getValue(), getValueRange(titleKeyAttribute)));
      }

      XmlAttribute bundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE, null);
      XmlAttribute keyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_KEY, null);
      if (bundleAttribute != null && keyAttribute != null) {
        processResourceBundleFileReferences(file, processor, bundleAttribute);
        processor.execute(new ResourceBundleKeyReference(file, bundleAttribute.getValue(), getValueRange(keyAttribute)));
      }

      processNestedFormReference(tag, processor, file);
      processButtonGroupReference(tag, processor, file, classReference);
    }

    // component class
    {
      if (clsAttribute != null) {
        JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        PsiReference[] referencesByString = provider.getReferencesByString(classNameStr, file, clsAttribute.getValueElement().getTextRange().getStartOffset() + 1);
        if(referencesByString.length < 1){
          // There are no references there
          return;
        }
        for (PsiReference aReferencesByString : referencesByString) {
          processor.execute(aReferencesByString);
        }
      }
    }

    // property references
    XmlTag parentTag = tag.getParentTag();
    if (parentTag != null && parentTag.getName().equals(UIFormXmlConstants.ELEMENT_PROPERTIES)) {
      XmlTag componentTag = parentTag.getParentTag();
      if (componentTag != null) {
        String className = componentTag.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_CLASS, Utils.FORM_NAMESPACE);
        if (className != null) {
          processPropertyReference(tag, processor, file, className.replace('$', '.'));
        }
      }
    }

    XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      processReferences(subtag, classReference, file, processor);
    }
  }

  private static void processResourceBundleFileReferences(PsiPlainTextFile file,
                                                          PsiReferenceProcessor processor,
                                                          XmlAttribute titleBundleAttribute) {
    processPackageReferences(file, processor, titleBundleAttribute);
    processor.execute(new ResourceBundleFileReference(file, getValueRange(titleBundleAttribute)));
  }

  private static void processPackageReferences(PsiPlainTextFile file,
                                               PsiReferenceProcessor processor,
                                               XmlAttribute attribute) {
    TextRange valueRange = getValueRange(attribute);
    String value = attribute.getValue();
    int pos=-1;
    while(true) {
      pos = value.indexOf('/', pos+1);
      if (pos < 0) {
        break;
      }
      processor.execute(new FormPackageReference(file, new TextRange(valueRange.getStartOffset(), valueRange.getStartOffset() + pos)));
    }
  }

  private static void processNestedFormReference(XmlTag tag, PsiReferenceProcessor processor, PsiPlainTextFile file) {
    XmlAttribute formFileAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, null);
    if (formFileAttribute != null) {
      processPackageReferences(file, processor, formFileAttribute);
      processor.execute(new ResourceFileReference(file, getValueRange(formFileAttribute)));
    }
  }

  private static void processButtonGroupReference(XmlTag tag, PsiReferenceProcessor processor, PsiPlainTextFile file,
                                                  PsiReference classReference) {
    XmlAttribute boundAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BOUND, null);
    XmlAttribute nameAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, null);
    if (boundAttribute != null && Boolean.parseBoolean(boundAttribute.getValue()) && nameAttribute != null) {
      processor.execute(new FieldFormReference(file, classReference, getValueRange(nameAttribute), null, null, false));
    }
  }

  private static void processPropertyReference(final XmlTag tag, PsiReferenceProcessor processor, final PsiPlainTextFile file,
                                               final String className) {
    final XmlAttribute valueAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, null);
    if (valueAttribute != null) {
      PsiReference reference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
        @Nullable
        public PsiReference compute() {
          JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(file.getProject());
          Module module = ModuleUtilCore.findModuleForPsiElement(file);
          if (module == null) return null;
          GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
          PsiClass psiClass = psiFacade.findClass(className, scope);
          if (psiClass != null) {
            PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, tag.getName(), false, true);
            if (getter != null) {
              PsiType returnType = getter.getReturnType();
              if (returnType instanceof PsiClassType) {
                PsiClassType propClassType = (PsiClassType)returnType;
                PsiClass propClass = propClassType.resolve();
                if (propClass != null) {
                  if (propClass.isEnum()) {
                    return new FormEnumConstantReference(file, getValueRange(valueAttribute), propClassType);
                  }
                  PsiClass iconClass = psiFacade.findClass("javax.swing.Icon", scope);
                  if (iconClass != null && InheritanceUtil.isInheritorOrSelf(propClass, iconClass, true)) {
                    return new ResourceFileReference(file, getValueRange(valueAttribute));
                  }
                }
              }
            }
          }
          return null;
      }});
      if (reference != null) {
        if (reference instanceof ResourceFileReference) {
          processPackageReferences(file, processor, valueAttribute);
        }
        processor.execute(reference);
      }
    }
  }

  @Nullable
  public static String getBundleName(PropertiesFile propertiesFile) {
    PsiDirectory directory = propertiesFile.getParent();
    if (directory == null) {
      return null;
    }
    String packageName;
    PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      packageName = "";
    }
    else {
      packageName = aPackage.getQualifiedName();
    }

    //noinspection NonConstantStringShouldBeStringBuffer
    String bundleName = propertiesFile.getResourceBundle().getBaseName();

    if (packageName.length() > 0) {
      bundleName = packageName + '.' + bundleName;
    }
    bundleName = bundleName.replace('.', '/');
    return bundleName;
  }

  private static CachedFormData getCachedData(final PsiPlainTextFile element) {
    CachedValue<CachedFormData> data = element.getUserData(CACHED_DATA);

    if(data == null) {
      data = CachedValuesManager.getManager(element.getProject()).createCachedValue(new CachedValueProvider<CachedFormData>() {
        final Map<String, Pair<PsiType, TextRange>> map = new HashMap<>();
        public Result<CachedFormData> compute() {
          PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements() {
            public boolean execute(PsiReference ref) {
              if (ref instanceof FieldFormReference) {
                FieldFormReference fieldRef = ((FieldFormReference)ref);
                String componentClassName = fieldRef.getComponentClassName();
                if (componentClassName != null) {
                  PsiClassType type = JavaPsiFacade.getInstance(element.getProject()).getElementFactory()
                    .createTypeByFQClassName(componentClassName, element.getResolveScope());
                  map.put(fieldRef.getRangeText(), new Pair<>(type, fieldRef.getComponentClassNameTextRange()));
                }
              }
              return super.execute(ref);
            }
          };
          processReferences(element, processor);
          PsiReference[] refs = processor.toArray(PsiReference.EMPTY_ARRAY);
          return new Result<>(new CachedFormData(refs, map), element);
        }
      }, false);
      element.putUserData(CACHED_DATA, data);
    }
    return data.getValue();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @Nonnull
  @NonNls
  public String getComponentName() {
    return "FormReferenceProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
