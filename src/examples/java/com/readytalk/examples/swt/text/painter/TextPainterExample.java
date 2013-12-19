package com.readytalk.examples.swt.text.painter;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.readytalk.examples.swt.RunnableExample;
import com.readytalk.examples.swt.SwtBlingExample;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.readytalk.swt.text.navigation.NavigationEvent;
import com.readytalk.swt.text.navigation.NavigationListener;
import com.readytalk.swt.text.painter.TextPainter;
import com.readytalk.swt.text.tokenizer.TextTokenizerFactory;
import com.readytalk.swt.text.tokenizer.TextTokenizerType;

public class TextPainterExample implements SwtBlingExample {

  class TextCanvas extends Canvas {

    Timer timer;

    public TextCanvas(Composite parent, int style)
        throws InstantiationException, IllegalAccessException,
        ClassNotFoundException {

      super(parent, style);
      timer = new Timer();
      final List<TextPainter> painters = new ArrayList<TextPainter>();

      painters.add( buildLabel(new Rectangle(20, 20, 200, 20), "Left Justified").setJustification(SWT.LEFT));
      painters.add(buildWikiTextPainter(new Rectangle(20, 40, 200, 250), false).setJustification(SWT.LEFT));

      painters.add( buildLabel(new Rectangle(240, 20, 200, 20), "Centered").setJustification(SWT.CENTER));
      painters.add( buildWikiTextPainter(new Rectangle(240, 40, 200, 250), false).setJustification(SWT.CENTER));

      painters.add( buildLabel(new Rectangle(460, 20, 200, 20), "Right Justified").setJustification(SWT.RIGHT));
      painters.add( buildWikiTextPainter(new Rectangle(460, 40, 200, 250), false).setJustification(SWT.RIGHT));

      painters.add( buildLabel(new Rectangle(20, 300, 350, 20), "Left Justified with Modulated Width").setJustification(SWT.LEFT));
      painters.add( buildWikiTextPainter(new Rectangle(20, 320, 200, 250), true).setJustification(SWT.LEFT));

      addPaintListener(new PaintListener() {
        @Override
        public void paintControl(PaintEvent e) {
          for (TextPainter painter: painters){
            painter.handlePaint(e);
          }
        }
      });
    }

    private TextPainter buildLabel(Rectangle bounds, String name) throws IllegalAccessException, ClassNotFoundException, InstantiationException {
      final TextPainter label = new TextPainter(this)
          .setTokenizer(TextTokenizerFactory.createTextTokenizer(TextTokenizerType.WIKI))
          .setText("'''"+name+"'''")
          .setBounds(new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height));
      return label;
    }

    private TextPainter buildWikiTextPainter(final Rectangle bounds, final boolean modulateWidth) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      final int width = bounds.width;

      final TextPainter painter = new TextPainter(this)
          .setTokenizer(TextTokenizerFactory.createTextTokenizer(TextTokenizerType.WIKI))
          .setText(
               "''Williamsburg biodiesel cornhole tote bag. Butcher mlkshk Tumblr, Pinterest sweater'' "
              + "'''pickled pop-up. Deep v tattooed forage pickled tofu.''' "
              + "'''''Gastropub post-ironic Banksy, Vice swag Tumblr gentrify street''''' "
              + "drinking. Skateboard, [http://www.austin.com Austin] Helvetica hoodie "
              + "distillery Cray. Meh scenester http://www.sustainable.com mixtape "
              + "Etsy McSweeney's slow-carb [http://www.shorditch.com Shoreditch].  Gluten-free pickled pug.")
          .setDrawCalculatedBounds(false)
          .setClipping(true)
          .setBounds(bounds)
          .setDrawBounds(false)
          .addNavigationListener(new NavigationListener() {
            @Override
            public void navigate(NavigationEvent event) {
              System.out.println("Navigate to: " + event.getUrl());
            }
          });

      if (modulateWidth) {
        timer.scheduleAtFixedRate(new TimerTask() {
          double counter = 0.0;

          @Override
          public void run() {
            counter += 0.01;
            int w = (int) (width * Math.sin(counter)) + width * 2;
            bounds.width = w;
            painter.setBounds(bounds);
            Display.getDefault().syncExec(new Runnable() {
              public void run() {
                if (!isDisposed()) {
                  redraw();
                }
              }
            });
          }
        }, 0, 40);
      }
      return painter;
    }
  };

  @RunnableExample(name="TextPainter")
  public TextPainterExample() { }

  public void run(Display display, Shell shell) {
    shell.setSize(700, 500);

    try {
      new TextCanvas(shell, SWT.NONE);
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    FillLayout fillLayout = new FillLayout();
    shell.setLayout(fillLayout);
    shell.open();
  }
}