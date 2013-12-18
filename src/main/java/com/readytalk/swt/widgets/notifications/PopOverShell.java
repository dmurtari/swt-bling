package com.readytalk.swt.widgets.notifications;

import com.readytalk.swt.effects.FadeEffect;
import com.readytalk.swt.effects.FadeEffect.Fadeable;
import com.readytalk.swt.effects.InvalidEffectArgumentException;
import com.readytalk.swt.helpers.AncestryHelper;
import com.readytalk.swt.widgets.CustomElementDataProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import java.util.logging.Logger;

/**
 * PopOverShell provides a simple interface for popping a Shell on top of any Object that subclasses
 * <code>Control</code> or implements <code>CustomElementDataProvider</code>
 */
public abstract class PopOverShell extends Widget implements Fadeable {
  private static final Logger LOG = Logger.getLogger(PopOverShell.class.getName());

  enum PopOverAboveOrBelowParent { BELOW_PARENT, ABOVE_PARENT }
  enum PopOverCornerCenteredOnParent { TOP_RIGHT_CORNER, TOP_LEFT_CORNER }

  private static final RGB BACKGROUND_COLOR = new RGB(74, 74, 74);
  private static final int FADE_OUT_TIME = 200; //milliseconds
  private static final int FULLY_VISIBLE_ALPHA = 255; //fully opaque
  private static final int FULLY_HIDDEN_ALPHA = 0; //fully transparent

  static final PopOverAboveOrBelowParent DEFAULT_DISPLAY_LOCATION = PopOverAboveOrBelowParent.BELOW_PARENT;
  static final PopOverCornerCenteredOnParent DEFAULT_POINT_CENTERED = PopOverCornerCenteredOnParent.TOP_LEFT_CORNER;

  PopOverAboveOrBelowParent popOverAboveOrBelowParent = DEFAULT_DISPLAY_LOCATION;
  PopOverCornerCenteredOnParent popOverCornerCenteredOnParent = DEFAULT_POINT_CENTERED;

  private Object fadeLock = new Object();

  protected Control parentControl;
  protected Shell popOverShell;

  private Shell parentShell;
  private PoppedOverItem poppedOverItem;
  private Listener popOverListener;
  private Listener parentListener;

  private Color backgroundColor;

  private Region popOverRegion;

  private boolean fadeEffectInProgress = false;

  /**
   * Provides the backbone for Custom Widgets that need a <code>Shell</code> popped over a <code>Control</code> or
   * <code>CustomElementDataProvider</code>. If you're using a <code>CustomElementDataProvider</code>, pass the
   * <code>CustomElementDataProvider.getPaintedElement()</code> as the parentControl.
   * @param parentControl The control you want the PopOverShell to appear above. In the case of
   *                      <code>CustomElementDataProvider</code>, pass
   *                      <code>CustomElementDataProvider.getPaintedElement()</code>.
   * @param customElementDataProvider The <code>CustomElementDataProvider</code> you want the PopOverShell to appear
   *                                  above (or null if you're using a Control)
   */
  public PopOverShell(Control parentControl, CustomElementDataProvider customElementDataProvider) {
    super(parentControl, SWT.NONE);

    if (customElementDataProvider != null) {
      poppedOverItem = new PoppedOverItem(customElementDataProvider);
    } else {
      poppedOverItem = new PoppedOverItem(parentControl);
    }

    this.parentControl = parentControl;
    parentShell = AncestryHelper.getShellFromControl(poppedOverItem.getControl());

    backgroundColor = new Color(getDisplay(), BACKGROUND_COLOR);

    popOverShell = new Shell(parentShell, SWT.ON_TOP | SWT.NO_TRIM);
    popOverShell.setBackground(backgroundColor);
    popOverShell.setLayout(new FillLayout());

    attachListeners();
  }

  /**
   * Shows the PopOverShell in a suitable location relative to the parent component. Classes extending PopOverShell will
   * provide the <code>Region</code> via the abstract <code>getAppropriatePopOverRegion()</code> method.
   */
  public void show() {
    runBeforeShowPopOverShell();

    Point popOverShellSize = getAppropriatePopOverSize();
    popOverRegion = new Region();
    popOverRegion.add(new Rectangle(0, 0, popOverShellSize.x, popOverShellSize.y));

    Point location = getPopOverShellLocation(parentShell, poppedOverItem, popOverRegion);

    popOverShell.setRegion(popOverRegion);
    popOverShell.setSize(popOverRegion.getBounds().width, popOverRegion.getBounds().height);
    popOverShell.setLocation(location);
    popOverShell.setAlpha(FULLY_VISIBLE_ALPHA);
    popOverShell.setVisible(true);
  }

  private Point getPopOverShellLocation(Shell parentShell, PoppedOverItem poppedOverItem, Region popOverRegion) {
    Rectangle displayBounds = parentShell.getDisplay().getBounds();
    Rectangle popOverBounds = popOverRegion.getBounds();
    Point poppedOverItemLocationRelativeToDisplay =
            getPoppedOverItemLocationRelativeToDisplay(parentShell, poppedOverItem);

    // Guess on the location
    Point location = getPopOverDisplayPoint(popOverBounds, poppedOverItem, poppedOverItemLocationRelativeToDisplay,
            popOverCornerCenteredOnParent, popOverAboveOrBelowParent);


    if (isBottomCutOff(displayBounds, location, popOverBounds)) {
      popOverAboveOrBelowParent = PopOverAboveOrBelowParent.ABOVE_PARENT;
      location.y = getPopOverYLocation(popOverBounds, poppedOverItem, poppedOverItemLocationRelativeToDisplay,
              popOverAboveOrBelowParent);
    }

    if (isRightCutOff(displayBounds, location, popOverBounds)) {
      popOverCornerCenteredOnParent = PopOverCornerCenteredOnParent.TOP_RIGHT_CORNER;
      location.x = getPopOverXLocation(popOverBounds, poppedOverItem, poppedOverItemLocationRelativeToDisplay,
              popOverCornerCenteredOnParent);
    }

    if (isStillOffScreen(displayBounds, location, popOverBounds)) {
      location = getPopOverLocationControlOffscreen(displayBounds, poppedOverItem, popOverRegion,
              poppedOverItemLocationRelativeToDisplay, location);
    }

    return location;
  }

  /**
   * Toggles visibility of the PopOverShell. If the PopOverShell is visible, it will fade it from the screen, otherwise
   * it will pop it up.
   */
  public void toggle() {
    if (isVisible() && !getIsFadeEffectInProgress()) {
      fadeOut();
    } else {
      show();
    }
  }

  /**
   * Implementers of this method return a Point describing the width and height the PopOverShell should be.
   * @return A Point object describing the appropriate PopOverSize. The x is the width and y is the height.
   */
  abstract Point getAppropriatePopOverSize();

  /**
   * Implementers of this method run any logic that needs to be executed before the PopOverShell is shown to
   * the user.
   */
  abstract void runBeforeShowPopOverShell();

  /**
   * Implementers of this method should do any clean-up needed to reset the widget to its default state.
   */
  abstract void resetWidget();

  /**
   * Called when the parent <code>PopOverShell</code> is disposed. Make sure you clean up any leftover elements
   * that need to be disposed. See https://github.com/ReadyTalk/swt-bling/wiki/Finding-SWT-Resource-Leaks-with-Sleak
   * for more information on detecting leaks with Sleak.
   */
  abstract void widgetDispose();

  PoppedOverItem getPoppedOverItem() {
    return poppedOverItem;
  }
  Shell getPopOverShell() { return popOverShell; }

  public void checkSubclass() {
    //no-op
  }

  private void attachListeners() {
    popOverListener = new Listener() {
      public void handleEvent(Event event) {
        switch (event.type) {
          case SWT.Dispose:
            onDispose(event);
            break;
        }
      }
    };

    addListener(SWT.Dispose, popOverListener);

    parentListener = new Listener() {
      public void handleEvent(Event event) {
        dispose();
      }
    };
    parentControl.addListener(SWT.Dispose, parentListener);
  }

  private void onDispose(Event event) {
    widgetDispose();

    parentControl.removeListener(SWT.Dispose, parentListener);
    removeListener(SWT.Dispose, parentListener);
    event.type = SWT.None;

    backgroundColor.dispose();
    popOverShell.dispose();
    popOverShell = null;

    if (popOverRegion != null) {
      popOverRegion.dispose();
    }
    popOverRegion = null;
  }

  boolean isBottomCutOff(Rectangle displayBounds, Point locationRelativeToDisplay,
                                              Rectangle containingRectangle) {
    int lowestYPosition = locationRelativeToDisplay.y + containingRectangle.height;

    if (!displayBounds.contains(new Point(0, lowestYPosition))) {
      return true;
    } else {
      return false;
    }
  }

  boolean isStillOffScreen(Rectangle displayBounds, Point locationRelativeToDisplay,
                           Rectangle containingRectangle) {
    Point currentPosition = new Point (locationRelativeToDisplay.x + containingRectangle.width,
            locationRelativeToDisplay.y + containingRectangle.height);
    if (!displayBounds.contains(currentPosition)) {
      return true;
    } else {
      return false;
    }
  }

  boolean isRightCutOff(Rectangle displayBounds, Point locationRelativeToDisplay,
                                                     Rectangle containingRectangle) {
    int farthestXPosition = locationRelativeToDisplay.x + containingRectangle.width;

    if (!displayBounds.contains(new Point(farthestXPosition, 0))) {
      popOverCornerCenteredOnParent = PopOverCornerCenteredOnParent.TOP_RIGHT_CORNER;
      return true;
    } else {
      return false;
    }
  }

  private Point getPoppedOverItemLocationRelativeToDisplay(Shell parentShell, PoppedOverItem poppedOverItem) {
    return parentShell.getDisplay().map(parentShell, null, poppedOverItem.getLocation());
  }

  private int getPopOverYLocation(Rectangle popOverBounds,
                                  PoppedOverItem poppedOverItem,
                                  Point poppedOverItemLocationRelativeToDisplay,
                                  PopOverAboveOrBelowParent aboveOrBelow) {
    switch (aboveOrBelow) {
      case ABOVE_PARENT:
        return poppedOverItemLocationRelativeToDisplay.y - popOverBounds.height;
      case BELOW_PARENT:
        return poppedOverItemLocationRelativeToDisplay.y + poppedOverItem.getSize().y;
      default:
        return 0;
    }
  }

  private Point getPopOverDisplayPoint(Rectangle popOverBounds,
                                       PoppedOverItem poppedOverItem,
                                       Point poppedOverItemLocationRelativeToDisplay,
                                       PopOverCornerCenteredOnParent popOverCornerCenteredOnParent,
                                       PopOverAboveOrBelowParent popOverAboveOrBelowParent) {
    Point location = new Point(0, 0);
    location.x = getPopOverXLocation(popOverBounds, poppedOverItem, poppedOverItemLocationRelativeToDisplay,
            popOverCornerCenteredOnParent);
    location.y = getPopOverYLocation(popOverBounds, poppedOverItem, poppedOverItemLocationRelativeToDisplay,
            popOverAboveOrBelowParent);
    return location;
  }

  private int getPopOverXLocation(Rectangle popOverBounds,
                                  PoppedOverItem poppedOverItem,
                                  Point poppedOverItemLocationRelativeToDisplay,
                                  PopOverCornerCenteredOnParent popOverCornerCenteredOnParent) {
    switch(popOverCornerCenteredOnParent) {
      case TOP_LEFT_CORNER:
        return poppedOverItemLocationRelativeToDisplay.x + (poppedOverItem.getSize().x / 2);
      case TOP_RIGHT_CORNER:
        return poppedOverItemLocationRelativeToDisplay.x - popOverBounds.width + (poppedOverItem.getSize().x / 2);
      default:
        return 0;
    }
  }

  private Point getPopOverLocationControlOffscreen(Rectangle displayBounds,
                                                   PoppedOverItem poppedOverItem,
                                                   Region popOverRegion,
                                                   Point poppedOverItemLocationRelativeToDisplay,
                                                   Point popOverOffscreenLocation) {
    Point appropriateDisplayLocation = popOverOffscreenLocation;
    Rectangle popOverRegionBounds = popOverRegion.getBounds();
    if (!displayBounds.contains(new Point(poppedOverItemLocationRelativeToDisplay.x + popOverRegionBounds.width, 0))) {
      appropriateDisplayLocation.x = displayBounds.width - popOverRegionBounds.width;
    }
    if (!displayBounds.contains(new Point(0, poppedOverItemLocationRelativeToDisplay.y + popOverRegionBounds.height))) {
      appropriateDisplayLocation.y = displayBounds.height - popOverRegionBounds.height;
    }

    return appropriateDisplayLocation;
  }

  /**
   * Returns whether the PopOverShell is currently visible on screen.
   * Note: If you utilize <code>PopOverShell.fadeOut()</code>, this method will return true while it's fading.
   * To determine if it's fading out, call <code>PopOverShell.getIsFadeEffectInProgress</code>
   * @return Visibility state of the PopOverShell
   */
  public boolean isVisible() {
    return popOverShell.isVisible();
  }

  /**
   * Fades the <code>PopOverShell</code> off the screen.
   */
  public void fadeOut() {
    if (fadeEffectInProgress) {
      return;
    }

    try {
      fadeEffectInProgress = true;
      FadeEffect fade = new FadeEffect.FadeEffectBuilder().
              setFadeable(this).
              setFadeCallback(new PopOverShellFadeCallback()).
              setFadeTimeInMilliseconds(FADE_OUT_TIME).
              setCurrentAlpha(FULLY_VISIBLE_ALPHA).
              setTargetAlpha(FULLY_HIDDEN_ALPHA).build();

      fade.startEffect();
    } catch (InvalidEffectArgumentException e) {
      LOG.warning("Invalid argument provided to FadeEffect.");
    }
  }

  /**
   * Returns whether the PopOverShell is currently fading from the screen.
   * Calls to <code>PopOverShell.isVisible()</code> will return true while the PopOverShell is dismissing.
   * @return Whether or not the PopOverShell is currently fading from the screen
   */
  public boolean getIsFadeEffectInProgress() {
    return fadeEffectInProgress;
  }

  /**
   * Implemented as part of Fadeable. <br/>
   * Users should not interact directly invoke this method.
   */
  public boolean fadeComplete(int targetAlpha) {
    synchronized (fadeLock) {
      if (popOverShell.getAlpha() == targetAlpha) {
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Implemented as part of Fadeable. <br/>
   * Users should not interact directly invoke this method.
   */
  public void fade(int alpha) {
    synchronized (fadeLock) {
      popOverShell.setAlpha(alpha);
    }
  }

  void hide() {
    popOverShell.setVisible(false);
    resetState();
    resetWidget();
  }

  private void resetState() {
    popOverAboveOrBelowParent = DEFAULT_DISPLAY_LOCATION;
    popOverCornerCenteredOnParent = DEFAULT_POINT_CENTERED;
    fadeEffectInProgress = false;
  }

  private class PopOverShellFadeCallback implements FadeEffect.FadeCallback {
    public void fadeComplete() {
      hide();
    }
  }

  public class PoppedOverItem {
    private Control control;
    private CustomElementDataProvider customElementDataProvider;

    public PoppedOverItem(Control control) {
      this.control = control;
    }

    public PoppedOverItem(CustomElementDataProvider customElementDataProvider) {
      this.customElementDataProvider = customElementDataProvider;
    }

    Point getSize() {
      if (control != null) {
        return control.getSize();
      } else {
        return customElementDataProvider.getSize();
      }
    }

    Point getLocation() {
      if (control != null) {
        return control.getLocation();
      } else {
        return customElementDataProvider.getLocation();
      }
    }

    Control getControl() {
      if (control != null) {
        return control;
      } else {
        return customElementDataProvider.getPaintedElement();
      }
    }

    Object getControlOrCustomElement() {
      if (customElementDataProvider != null) {
        return customElementDataProvider;
      } else {
        return control;
      }
    }

    CustomElementDataProvider getCustomElementDataProvider() {
      return customElementDataProvider;
    }
  }
}
