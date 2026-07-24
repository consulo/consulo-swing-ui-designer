
package com.intellij.uiDesigner.impl.propertyInspector;

/**
 * @author yole
 */
public class InplaceContext {
  private final boolean myKeepInitialValue;
  private boolean myStartedByTyping;
  private char myStartChar;
  private boolean myModalDialogDisplayed;

  public InplaceContext(boolean keepInitialValue) {
    myKeepInitialValue = keepInitialValue;
  }

  public InplaceContext(boolean keepInitialValue, char startChar) {
    myKeepInitialValue = keepInitialValue;
    myStartedByTyping = true;
    myStartChar = startChar;
  }

  public boolean isKeepInitialValue() {
    return myKeepInitialValue;
  }

  public boolean isStartedByTyping() {
    return myStartedByTyping;
  }

  public char getStartChar() {
    return myStartChar;
  }

  public boolean isModalDialogDisplayed() {
    return myModalDialogDisplayed;
  }

  public void setModalDialogDisplayed(boolean modalDialogDisplayed) {
    myModalDialogDisplayed = modalDialogDisplayed;
  }
}
