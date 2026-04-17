/**
 * @author VISTALL
 * @since 31/01/2023
 */
open module com.intellij.uiDesigner
{
    requires consulo.application.api;
    requires consulo.application.content.api;
    requires consulo.application.ui.api;
    requires consulo.base.icon.library;
    requires consulo.bookmark.ui.view.api;
    requires consulo.code.editor.api;
    requires consulo.color.scheme.api;
    requires consulo.compiler.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.container.api;
    requires consulo.datacontext.api;
    requires consulo.disposer.api;
    requires consulo.document.api;
    requires consulo.execution.api;
    requires consulo.file.editor.api;
    requires consulo.file.template.api;
    requires consulo.index.io;
    requires consulo.language.api;
    requires consulo.language.code.style.api;
    requires consulo.language.editor.api;
    requires consulo.language.editor.refactoring.api;
    requires consulo.language.editor.ui.api;
    requires consulo.language.spellchecker.editor.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.module.api;
    requires consulo.module.content.api;
    requires consulo.navigation.api;
    requires consulo.process.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.project.ui.view.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.ui.ex.awt.api;
    requires consulo.undo.redo.api;
    requires consulo.usage.api;
    requires consulo.util.collection;
    requires consulo.util.dataholder;
    requires consulo.util.io;
    requires consulo.util.jdom;
    requires consulo.util.lang;
    requires consulo.util.nodep;
    requires consulo.util.xml.serializer;
    requires consulo.virtual.file.status.api;
    requires consulo.virtual.file.system.api;

    requires consulo.ide.impl;

    requires asm;

    requires consulo.java;
    requires consulo.java.analysis.impl;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.java.language.api;
    requires consulo.java.properties.impl;
    requires com.intellij.xml.api;
    requires com.intellij.xml;
    requires com.intellij.properties;

    requires jgoodies.common;
    requires jgoodies.forms;
    requires jgoodies.looks;

    requires instrumentation.util;
    requires forms.compiler;

    // TODO remove in future
    requires java.desktop;
    requires forms.rt;

    exports com.intellij.ide.palette;
    exports com.intellij.ide.palette.impl;
    exports com.intellij.uiDesigner.impl;
    exports com.intellij.uiDesigner.impl.actions;
    exports com.intellij.uiDesigner.impl.binding;
    exports com.intellij.uiDesigner.impl.clientProperties;
    exports com.intellij.uiDesigner.impl.componentTree;
    exports com.intellij.uiDesigner.impl.designSurface;
    exports com.intellij.uiDesigner.impl.editor;
    exports com.intellij.uiDesigner.impl.fileTemplate;
    exports com.intellij.uiDesigner.impl.i18n;
    exports com.intellij.uiDesigner.impl.inspections;
    exports com.intellij.uiDesigner.impl.make;
    exports com.intellij.uiDesigner.impl.palette;
    exports com.intellij.uiDesigner.impl.projectView;
    exports com.intellij.uiDesigner.impl.propertyInspector;
    exports com.intellij.uiDesigner.impl.propertyInspector.editors;
    exports com.intellij.uiDesigner.impl.propertyInspector.editors.string;
    exports com.intellij.uiDesigner.impl.propertyInspector.properties;
    exports com.intellij.uiDesigner.impl.propertyInspector.renderers;
    exports com.intellij.uiDesigner.impl.quickFixes;
    exports com.intellij.uiDesigner.impl.radComponents;
    exports com.intellij.uiDesigner.impl.snapShooter;
    exports com.intellij.uiDesigner.impl.wizard;
    exports consulo.uiDesigner.impl.icon;
    exports consulo.uiDesigner.impl.localize;


    //opens com.intellij.uiDesigner.impl.actions to consulo.component.impl;
}
